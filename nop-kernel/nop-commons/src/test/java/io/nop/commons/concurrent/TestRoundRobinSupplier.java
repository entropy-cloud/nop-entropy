/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.SourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_RETIREMENT_GRACE_PERIOD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRoundRobinSupplier {

    static class TestResource implements AutoCloseable {
        final int id;
        boolean closed;

        TestResource(int id) {
            this.id = id;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    public void testResizeCloseNewResourcesOnFailure() {
        AtomicInteger created = new AtomicInteger();
        List<TestResource> all = new ArrayList<>();

        RoundRobinSupplier<TestResource> supplier = new RoundRobinSupplier<>(() -> {
            int id = created.incrementAndGet();
            TestResource res = new TestResource(id);
            all.add(res);
            if (id > 2)
                throw new IllegalStateException("mock factory failure");
            return res;
        }, 1);

        assertEquals(1, supplier.get().id);

        // 扩容到3: index1创建成功(id=2)，index2创建时抛异常
        assertThrows(Exception.class, () -> supplier.resize(3));

        // 扩容失败时，本次新建的资源(id=2)必须被关闭，从旧数组复制来的资源(id=1)不受影响
        assertTrue(all.get(0).id == 1 && !all.get(0).closed, "old resource must not be closed");
        assertTrue(all.get(1).id == 2 && all.get(1).closed, "newly created resource must be closed");

        // 扩容失败后旧数组仍然可用
        assertEquals(1, supplier.get().id);
    }

    @Test
    public void testResizeShrinkAndExpand() {
        AtomicInteger created = new AtomicInteger();
        RoundRobinSupplier<TestResource> supplier = new RoundRobinSupplier<>(() -> new TestResource(created.incrementAndGet()), 2);
        supplier.resize(1);
        assertEquals(1, supplier.get().id);
        supplier.resize(2);
        int a = supplier.get().id;
        int b = supplier.get().id;
        // 扩容后旧资源(id=1)保留，新增槽位创建新资源(id=3)
        assertTrue((a == 1 && b == 3) || (a == 3 && b == 1), "a=" + a + ",b=" + b);

        ResourceRetirementQueue.global().drainAllNow();
    }

    // ==================== C1-001 退役代际契约（plan 2504 裁定①） ====================

    static final SourceLocation LOC = SourceLocation.fromClass(TestRoundRobinSupplier.class);

    /**
     * C1-001 本体：resize 收缩只发布新代并对被裁剪尾段做退役登记（延迟关闭）——
     * 修复前红形态（基线 ccbccee458 实证）：交付过的资源在 shrink 后立即 closed（use-after-close 窗）。
     * 绿形态：交付资源宽限期内存活；确定性触发排空后关闭（时机可观测，零 sleep）。
     * 关闭档（grace ≤0）= 修复前语义特例，作为在库红通道断言。
     */
    @Test
    public void testShrinkRetiresTailWithGrace_C1_001() {
        AtomicInteger seq = new AtomicInteger();
        RoundRobinSupplier<TestResource> supplier = new RoundRobinSupplier<>(() -> new TestResource(seq.incrementAndGet()), 2);

        TestResource a = supplier.get();
        TestResource b = supplier.get();

        supplier.resize(1);

        // 绿形态：交付过的资源在退役宽限期内保持存活
        assertFalse(a.closed, "head resource must not be closed by shrink");
        assertFalse(b.closed, "delivered tail resource must survive within retirement grace window");

        // 确定性排空（注入缝②的手动触发形态）：关闭时机可观测
        ResourceRetirementQueue.global().drainAllNow();
        assertTrue(b.closed, "retired tail resource must be closed after deterministic drain");
        assertFalse(a.closed, "head resource is shared into the new generation and must stay alive");
    }

    /**
     * close() 同族面（C1-001 F3③）：整代退役——交付资源在 close 后宽限期内存活，排空后关闭。
     */
    @Test
    public void testCloseRetiresWholeGeneration_C1_001() {
        AtomicInteger seq = new AtomicInteger();
        RoundRobinSupplier<TestResource> supplier = new RoundRobinSupplier<>(() -> new TestResource(seq.incrementAndGet()), 3);

        TestResource a = supplier.get();
        TestResource b = supplier.get();

        supplier.close();

        assertFalse(a.closed, "delivered resource must survive close within grace window");
        assertFalse(b.closed, "delivered resource must survive close within grace window");

        ResourceRetirementQueue.global().drainAllNow();
        assertTrue(a.closed && b.closed, "whole generation must be closed after deterministic drain");
    }

    /**
     * 并发 resize × get 真实交割断言（C10-001+N5 改写：单线程名不副实用例以并发断言补强）：
     * getter 线程持续交付期间主线程反复收缩——任何时刻交付的资源都不允许已关闭。
     * 退役代际机制下该断言确定性绿（宽限期保证交付资源存活）；关闭档（grace=0）复现修复前语义。
     */
    @Test
    public void testConcurrentResizeGetNoClosedDelivery_C1_001() throws Exception {
        AtomicInteger seq = new AtomicInteger();
        RoundRobinSupplier<TestResource> supplier = new RoundRobinSupplier<>(() -> new TestResource(seq.incrementAndGet()), 8);

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger closedDeliveries = new AtomicInteger();
        AtomicInteger deliveries = new AtomicInteger();
        List<Thread> getters = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                while (!Thread.currentThread().isInterrupted()) {
                    TestResource res = supplier.get();
                    deliveries.incrementAndGet();
                    if (res.closed)
                        closedDeliveries.incrementAndGet();
                }
            }, "c1-001-getter-" + i);
            t.setDaemon(true);
            getters.add(t);
        }
        getters.forEach(Thread::start);
        start.countDown();

        // 有界交割窗：主线程反复收缩，getter 持续交付
        long deadline = System.currentTimeMillis() + 500;
        int shrinks = 0;
        while (System.currentTimeMillis() < deadline && deliveries.get() < 2000) {
            supplier.resize(8);
            shrinks++;
        }
        assertTrue(shrinks > 0, "shrink cycles must actually run");
        getters.forEach(Thread::interrupt);
        for (Thread t : getters)
            t.join(2000);

        assertEquals(0, closedDeliveries.get(),
                "no delivery may hand out a closed resource (retirement grace must outlive the delivery)");

        ResourceRetirementQueue.global().drainAllNow();
    }

    /**
     * 关闭档（grace ≤0）= 修复前同步关闭语义（确定性测试缝①，显式回退通道）：
     * 收缩立即关闭被裁剪尾段，行为与基线一致。
     */
    @Test
    public void testGraceZeroCloseModeLegacySemantics_C1_001() {
        var provider = AppConfig.getConfigProvider();
        provider.updateConfigValue(CFG_CONCURRENT_RETIREMENT_GRACE_PERIOD, Duration.ZERO);
        try {
            AtomicInteger seq = new AtomicInteger();
            RoundRobinSupplier<TestResource> supplier = new RoundRobinSupplier<>(() -> new TestResource(seq.incrementAndGet()), 2);

            supplier.get();
            TestResource tail = supplier.get();

            supplier.resize(1);
            assertTrue(tail.closed, "grace<=0 close-mode must degrade to synchronous close (pre-fix semantics)");

            // 排空为空操作
            ResourceRetirementQueue.global().drainAllNow();
        } finally {
            provider.updateConfigValue(CFG_CONCURRENT_RETIREMENT_GRACE_PERIOD, null);
        }
    }

    /**
     * 构造失败路径维持 inline close（未发布资源无并发读者，fail-fast 语义）
     */
    @Test
    public void testConstructorFailureStillInlineClose_C1_001() {
        AtomicInteger created = new AtomicInteger();
        List<TestResource> all = new ArrayList<>();
        assertThrows(Exception.class, () -> new RoundRobinSupplier<TestResource>(() -> {
            int id = created.incrementAndGet();
            if (id > 3)
                throw new IllegalStateException("boom");
            TestResource res = new TestResource(id);
            all.add(res);
            return res;
        }, 4));

        assertTrue(all.size() == 3, "three resources entered the array before failure");
        assertTrue(all.get(2).closed, "unpublished resources must be closed inline on constructor failure");
        ResourceRetirementQueue.global().drainAllNow();
    }
}
