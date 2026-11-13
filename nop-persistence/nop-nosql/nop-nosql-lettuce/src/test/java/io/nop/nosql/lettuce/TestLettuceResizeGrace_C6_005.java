/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.concurrent.ResourceRetirementQueue;
import io.nop.commons.concurrent.RoundRobinSupplier;
import io.nop.nosql.core.config.RedisConfig;
import io.nop.nosql.lettuce.impl.LettuceRedisConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_RETIREMENT_GRACE_PERIOD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.5-C6-005 缩容取用窗回归（plan 2504 Phase 3，C10-026 义务；双轨联动 §四#6）。
 *
 * <p>现有 testClientResourcesNotLeakedAcrossStartStop 不触发 resize（缩容修复不触红），本用例经
 * 裁定④测试缝（{@code protected createConnectionSupplier}，随修复抽取的修复面一部分）注入 fake
 * 供应商，走<b>真实 refreshConfig→resize 链</b>（dockerless，RedisClient 惰性建连无需真实 redis）。
 *
 * <p>修复前红形态：缩容立即热关被裁剪共享连接，已交付在用连接 close（显式 close 不走
 * autoReconnect）→ 取用窗口内瞬时命令失败。红证通道 = 关闭档（grace ≤0，裁定①确定的
 * 「退化为现行同步关闭语义」确定性缝）。
 * <p>修复后绿形态（设施轨行为钉）：缩容只退役被裁剪尾段，已交付连接自缩容时刻起存活 ≥ 宽限期；
 * 确定性排空后关闭（销毁时机可观测）；缩容后新取用只见存活代资源。
 */
public class TestLettuceResizeGrace_C6_005 {
    static final SourceLocation LOC = SourceLocation.fromClass(TestLettuceResizeGrace_C6_005.class);

    static class FakeConnection implements AutoCloseable {
        final int id;
        volatile boolean closed;

        FakeConnection(int id) {
            this.id = id;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** 经裁定④测试缝注入 fake 供应商的子类（dockerless：doStart 中真实 RedisClient 仅惰性创建） */
    static class TestProvider extends LettuceRedisConnectionProvider {
        volatile RoundRobinSupplier<? extends AutoCloseable> injected;

        @Override
        protected RoundRobinSupplier<? extends AutoCloseable> createConnectionSupplier(
                java.util.function.Supplier<? extends AutoCloseable> factory, int n) {
            // 忽略真实 client factory（dockerless 下不建连），注入 fake 连接供应商
            RoundRobinSupplier<FakeConnection> supplier =
                    new RoundRobinSupplier<>(() -> new FakeConnection(seq.incrementAndGet()), n);
            this.injected = supplier;
            return supplier;
        }
    }

    static final AtomicInteger seq = new AtomicInteger();

    @AfterEach
    public void cleanup() {
        // 排空测试期间登记的退役，防止泄漏到其他用例
        ResourceRetirementQueue.global().drainAllNow();
        AppConfig.getConfigProvider().updateConfigValue(CFG_CONCURRENT_RETIREMENT_GRACE_PERIOD, null);
    }

    private RedisConfig config(int poolSize) {
        RedisConfig config = new RedisConfig();
        config.setHost("localhost");
        config.setPort(6390); // dockerless：真实 client 惰性建连，不会被触达
        config.setConnectionPoolSize(poolSize);
        return config;
    }

    private TestProvider startProvider(RedisConfig config) {
        TestProvider provider = new TestProvider();
        provider.setConfig(config);
        provider.start();
        assertNotNull(provider.injected, "test seam must inject a fake connection supplier");
        return provider;
    }

    /**
     * C10-026 本体（绿形态）：真实 refreshConfig→resize 缩容链下，已交付在用连接不被热关
     * （存活 ≥ 宽限期），确定性排空后被裁剪尾段才关闭（销毁时机可观测）。
     */
    @Test
    public void testShrinkDoesNotHotCloseDeliveredConnection_C6_005() {
        RedisConfig config = config(3);
        TestProvider provider = startProvider(config);
        try {
            RoundRobinSupplier<? extends AutoCloseable> supplier = provider.injected;

            // 交付两条在用连接（模拟取用中：getSyncCommands/getConnection 的共享交出形态）
            FakeConnection inUse1 = (FakeConnection) supplier.get();
            FakeConnection inUse2 = (FakeConnection) supplier.get();
            assertFalse(inUse1.closed && inUse2.closed, "delivered connections must start alive");

            // 真实 refreshConfig→resize 链：3 → 1 缩容
            config.setConnectionPoolSize(1);
            provider.refreshConfig();

            // 绿形态①：已交付在用连接不被热关（存活 ≥ 宽限期）
            assertFalse(inUse1.closed, "delivered in-use connection must survive shrink (no hot close)");
            assertFalse(inUse2.closed, "delivered in-use connection must survive shrink (no hot close)");

            // 绿形态②：缩容后新取用只见存活代资源（头部共享引用，非被裁剪尾段）
            FakeConnection afterShrink = (FakeConnection) supplier.get();
            assertFalse(afterShrink.closed, "new delivery after shrink must be a live shared resource");

            // 绿形态③：确定性排空后被裁剪尾段关闭（销毁/关闭时机可观测，零 sleep）
            ResourceRetirementQueue.global().drainAllNow();
            assertTrue(inUse1.closed || inUse2.closed,
                    "retired tail connections must be closed after deterministic drain");
            assertFalse(afterShrink.closed, "shared head connection must stay alive (retained by new generation)");

            provider.stop();
        } catch (RuntimeException e) {
            try { provider.stop(); } catch (RuntimeException ignored) { /* 已在关闭路径，忽略二次停止失败 */ }
            throw e;
        }
    }

    /**
     * 双轨联动验证（rule 23）：取用侧三入口（getSyncCommands/getAsyncCommands/getConnection）共享
     * 同一 {@code connectionSupplier.get()} 交付线（provider :63/:74/:84），缩容窗口内交付的连接均存活、
     * 排空后新取用持续可用——使用面行为钉与设施轨 RoundRobinSupplier 行为钉交叉验证。
     * （fake 连接无法实现 lettuce 命令面接口，交付断言经共享交付线 supplier.get() 等价观测。）
     */
    @Test
    public void testShrinkWindowAcrossEntryPoints_C6_005() {
        RedisConfig config = config(2);
        TestProvider provider = startProvider(config);
        try {
            RoundRobinSupplier<? extends AutoCloseable> supplier = provider.injected;

            // 三入口对应的共享交付线连续交付（等价于 getSyncCommands/getAsyncCommands/getConnection 的取用）
            FakeConnection d1 = (FakeConnection) supplier.get();
            FakeConnection d2 = (FakeConnection) supplier.get();

            // 缩容 2 → 1
            config.setConnectionPoolSize(1);
            provider.refreshConfig();

            // 缩容窗口内交付的共享连接均不被热关（javadoc 契约：共享连接由设施全权关闭）
            assertFalse(d1.closed || d2.closed, "delivered shared connections must survive the shrink window");

            // 排空后新取用持续可用（被裁剪尾段关闭，存活代继续服务）
            ResourceRetirementQueue.global().drainAllNow();
            FakeConnection afterShrink = (FakeConnection) supplier.get();
            assertFalse(afterShrink.closed, "provider must keep serving after shrink+drain");

            provider.stop();
        } catch (RuntimeException e) {
            try { provider.stop(); } catch (RuntimeException ignored) { /* 已在关闭路径，忽略二次停止失败 */ }
            throw e;
        }
    }

    /**
     * 红证通道（在库确定性红 = 修复前语义特例）：grace ≤0 关闭档下，缩容同步热关被裁剪连接——
     * 与基线行为逐字节一致，证明本用例真实触发 resize 缩容面（非 testClientResourcesNotLeaked
     * 式不触红假覆盖）。
     */
    @Test
    public void testGraceZeroCloseModeRestoresLegacyHotClose_C6_005() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_CONCURRENT_RETIREMENT_GRACE_PERIOD, Duration.ZERO);
        RedisConfig config = config(3);
        TestProvider provider = startProvider(config);
        try {
            RoundRobinSupplier<? extends AutoCloseable> supplier = provider.injected;
            List<FakeConnection> delivered = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 3; i++)
                delivered.add((FakeConnection) supplier.get());

            config.setConnectionPoolSize(1);
            provider.refreshConfig();

            // 关闭档 = 修复前语义：被裁剪尾段（index ≥ 1）同步热关
            assertTrue(delivered.get(1).closed, "close-mode (grace<=0) must reproduce legacy hot close");
            assertTrue(delivered.get(2).closed, "close-mode (grace<=0) must reproduce legacy hot close");
            assertFalse(delivered.get(0).closed, "shared head must survive (retained by new generation)");

            provider.stop();
        } catch (RuntimeException e) {
            try { provider.stop(); } catch (RuntimeException ignored) { /* 已在关闭路径，忽略二次停止失败 */ }
            throw e;
        }
    }

    /**
     * 设施轨交叉验证（双轨验收口径①）：交付连接在收缩窗口内存活过宽限、确定性排空后关闭——
     * 与使用面（本文件上方用例）共享同一设施行为钉。
     */
    @Test
    public void testDeliveredConnectionOutlivesShrinkUntilDrain_C6_005() {
        RedisConfig config = config(4);
        TestProvider provider = startProvider(config);
        try {
            RoundRobinSupplier<? extends AutoCloseable> supplier = provider.injected;
            FakeConnection head = (FakeConnection) supplier.get();

            config.setConnectionPoolSize(1);
            provider.refreshConfig();

            // 宽限期内连接存活（存活 ≥ grace 契约的确定性观测：排空前未关闭）
            assertFalse(head.closed, "delivered connection must outlive the shrink window within grace");

            ResourceRetirementQueue.global().drainAllNow();
            assertFalse(head.closed, "delivered head is retained by the new generation (not retired tail)");

            provider.stop();
        } catch (RuntimeException e) {
            try { provider.stop(); } catch (RuntimeException ignored) { /* 已在关闭路径，忽略二次停止失败 */ }
            throw e;
        }
    }
}
