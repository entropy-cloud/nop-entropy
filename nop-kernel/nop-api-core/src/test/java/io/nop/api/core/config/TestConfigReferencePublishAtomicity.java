/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.config;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.StaticValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1-007（plan 2507 裁定④，含 C1-017 族案）回归：{@link DefaultConfigReference} 的
 * location + provider 必须以单一不可变 holder 经单 volatile 写发布——
 * {@code updateValue} 为「构造新 holder + 单 volatile 写」一次换代，读侧经
 * {@code stateSnapshot()} 单次读恒见同代 (location, provider) 对，两步写撕裂窗
 * 结构性不存在；provider 变更对读者经 volatile 发布边立即可见；defaultValue 不入
 * holder，{@code get()} 的 null 回落读边语义不变；wrapper 链（CastType/Placeholder/
 * Override）读站透传新代快照。
 *
 * <p>红绿留痕：缺陷形态源码 prefix（两步 plain write + 非 volatile 字段）运行整类
 * 观测到混代快照计数 &gt; 0（{@code _tmp/phase2-config-C1-007-red.log}）；修复后
 * 零混代（{@code _tmp/phase2-config-C1-007-green.log}）。测试纪律：daemon 夹具线程 +
 * latch 对齐 + 有界观察窗 + 类级 {@link Timeout} 防挂死，禁 sleep 依赖。
 */
@Timeout(60)
public class TestConfigReferencePublishAtomicity {
    private static final SourceLocation LOC_GEN1 = SourceLocation.fromPath("test://publish-gen-1");
    private static final SourceLocation LOC_GEN2 = SourceLocation.fromPath("test://publish-gen-2");

    /**
     * 撕裂窗红腿：写线程高频换代 × 读线程单次快照观测 (location, provider) 代际一致性。
     * 修复前（两步 plain write）观测到混代组合（新 location 配旧 provider 或反之）；
     * 修复后（holder 单 volatile 发布）混代计数恒为 0。
     */
    @Test
    public void testUpdateValueSinglePublishNoTornGeneration() throws Exception {
        DefaultConfigReference<String> ref = new DefaultConfigReference<>(LOC_GEN1, "nop.test.cfgref.publish.gen",
                String.class, null, StaticValue.valueOf("gen-1"));

        int flipPairs = 500_000;
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        AtomicLong tornCount = new AtomicLong();
        AtomicLong observed = new AtomicLong();

        Thread writer = new Thread(() -> {
            awaitUnchecked(started);
            StaticValue<String> p1 = StaticValue.valueOf("gen-1");
            StaticValue<String> p2 = StaticValue.valueOf("gen-2");
            for (int i = 0; i < flipPairs; i++) {
                // 缺陷形态：setLocation + setProvider 两步 plain write（撕裂窗本体）；
                // 修复形态：单 volatile holder 写发布，一次换代无中间态
                ref.updateValue(LOC_GEN1, p1);
                ref.updateValue(LOC_GEN2, p2);
            }
            stop.set(true);
        }, "cfgref-publish-writer");
        writer.setDaemon(true);

        int readers = 4;
        List<Thread> readerThreads = new ArrayList<>();
        for (int i = 0; i < readers; i++) {
            Thread reader = new Thread(() -> {
                awaitUnchecked(started);
                long torn = 0;
                long seen = 0;
                while (!stop.get()) {
                    // 单次快照：location 与 provider 必须同代（holder 单发布语义）
                    DefaultConfigReference.ConfigRefState<String> s = ref.stateSnapshot();
                    String value = s.provider.get();
                    String locPath = s.location == null ? null : s.location.getPath();
                    if ("gen-1".equals(value)) {
                        if (!LOC_GEN1.getPath().equals(locPath))
                            torn++;
                    } else if ("gen-2".equals(value)) {
                        if (!LOC_GEN2.getPath().equals(locPath))
                            torn++;
                    }
                    seen++;
                }
                tornCount.addAndGet(torn);
                observed.addAndGet(seen);
            }, "cfgref-publish-reader-" + i);
            reader.setDaemon(true);
            readerThreads.add(reader);
        }

        writer.start();
        for (Thread reader : readerThreads) {
            reader.start();
        }
        started.countDown();
        writer.join(30_000);
        assertFalse(writer.isAlive(), "writer thread must finish within bounded window");
        // 有界放行：读者在 writer 完成后必然退出，join 有上界防挂死
        stop.set(true);
        for (Thread reader : readerThreads) {
            reader.join(10_000);
        }
        for (Thread reader : readerThreads) {
            assertFalse(reader.isAlive(), "reader thread must terminate within bounded window");
        }

        // 绿形态：任意读者快照 location/provider 恒同代，混代计数为 0
        assertEquals(0L, tornCount.get(),
                () -> "torn generation snapshots observed: " + tornCount.get() + " of " + observed.get());
        assertTrue(observed.get() > 0, "readers must have observed at least one snapshot");
    }

    /**
     * volatile 发布边：updateValue 完成后（latch 对齐，非 sleep），其他线程的
     * get()/getAssignedValue()/getLocation() 立即可见新代快照。
     */
    @Test
    public void testProviderChangeVisibleToGetAfterPublish() throws Exception {
        DefaultConfigReference<String> ref = new DefaultConfigReference<>(LOC_GEN1, "nop.test.cfgref.publish.visible",
                String.class, null, StaticValue.valueOf("v-1"));

        CountDownLatch published = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            ref.updateValue(LOC_GEN2, StaticValue.valueOf("v-2"));
            published.countDown();
        }, "cfgref-visible-writer");
        writer.setDaemon(true);
        writer.start();

        assertTrue(published.await(10, TimeUnit.SECONDS), "writer must publish within bounded window");
        assertEquals("v-2", ref.get());
        assertEquals("v-2", ref.getAssignedValue());
        assertEquals(LOC_GEN2, ref.getLocation());
        assertEquals(LOC_GEN2, ref.stateSnapshot().location);
        assertEquals("v-2", ref.getProvider().get());
        writer.join(10_000);
        assertFalse(writer.isAlive());
    }

    /**
     * defaultValue 不入 holder：{@code get()} 在 assigned 为 null 时回落 defaultValue
     * （updateConfigValue(ref, null) 后的读边语义不变），且 defaultValue 独立字段
     * 不随 holder 代际替换变化。
     */
    @Test
    public void testDefaultValueFallbackUnchangedByHolderPublish() {
        DefaultConfigReference<String> ref = new DefaultConfigReference<>(
                SourceLocation.fromPath("test://publish-default"), "nop.test.cfgref.publish.default",
                String.class, "def-v", StaticValue.nullValue());

        // assigned 为 null 时回落 defaultValue
        assertEquals("def-v", ref.get());
        assertNull(ref.getAssignedValue());

        // 显式赋值后覆盖 default
        ref.updateValue(LOC_GEN1, StaticValue.valueOf("assigned"));
        assertEquals("assigned", ref.get());

        // 更新为 null 后再次回落 default（读边与 holder 正交）
        ref.updateValue(LOC_GEN2, StaticValue.nullValue());
        assertEquals("def-v", ref.get());

        // defaultValue 独立于 holder：不随 updateValue 代际替换
        assertEquals("def-v", ref.getDefaultValue());
        ref.setDefaultValue("def-v2");
        assertEquals("def-v2", ref.get());
    }

    /**
     * W4 assignConfigValue / W5 updateConfigValue / W6 reset 三个 api-core 写点
     * 经 SimpleConfigProvider 全部路由到 updateValue 的 holder 单发布路径。
     */
    @Test
    public void testSimpleConfigProviderWritePointsRouteThroughHolder() {
        SimpleConfigProvider provider = new SimpleConfigProvider();
        IConfigReference<String> ref = provider.getStaticConfigReference("nop.test.cfgref.w456", String.class,
                "base", null);
        assertEquals("base", ref.get());

        // W5 updateConfigValue
        provider.updateConfigValue(ref, "w5");
        assertEquals("w5", ref.get());
        assertTrue(provider.isDirty());

        // W4 assignConfigValue
        provider.assignConfigValue("nop.test.cfgref.w456", "w4");
        assertEquals("w4", ref.get());

        // W6 reset：按当前系统属性重采样恢复基线（未设置系统属性 → 声明缺省值）
        System.clearProperty("nop.test.cfgref.w456");
        provider.reset();
        assertEquals("base", ref.get());
        assertFalse(provider.isDirty());
    }

    /**
     * R8 wrapper 链读站：CastType/Placeholder/Override 委托经
     * get()/getAssignedValue()/getLocation() 透传底层 holder 新代快照，零改码语义。
     */
    @Test
    public void testWrapperChainDelegatesReadThroughHolder() {
        DefaultConfigReference<String> inner = new DefaultConfigReference<>(LOC_GEN1, "nop.test.cfgref.wrapper.inner",
                String.class, null, StaticValue.valueOf("gen-1"));
        CastTypeConfigReference<String> cast = new CastTypeConfigReference<>(inner, String.class);
        PlaceholderConfigReference<String> placeholder = new PlaceholderConfigReference<>(inner);
        OverrideConfigReference<String> override = new OverrideConfigReference<>(inner,
                DefaultConfigReference.staticRef("nop.test.cfgref.wrapper.default", "gen-1"));

        assertEquals("gen-1", cast.getAssignedValue());
        assertEquals("gen-1", placeholder.get());
        assertEquals("gen-1", override.getAssignedValue());
        assertEquals(LOC_GEN1, cast.getLocation());
        assertEquals(LOC_GEN1, placeholder.getLocation());
        assertEquals(LOC_GEN1, override.getLocation());

        // inner 经 updateValue 单发布换代后，wrapper 链读站透传新代 (location, provider)
        inner.updateValue(LOC_GEN2, StaticValue.valueOf("gen-2"));
        assertEquals("gen-2", cast.getAssignedValue());
        assertEquals("gen-2", placeholder.get());
        assertEquals("gen-2", override.getAssignedValue());
        assertEquals(LOC_GEN2, cast.getLocation());
        assertEquals(LOC_GEN2, placeholder.getLocation());
        assertEquals(LOC_GEN2, override.getLocation());
    }

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("latch not released within bounded window");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while awaiting latch", e);
        }
    }
}
