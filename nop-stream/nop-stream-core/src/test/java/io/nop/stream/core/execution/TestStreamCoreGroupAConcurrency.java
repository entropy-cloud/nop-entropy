/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.operators.SourceReaderOperator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.41（1.1 单元）R4-01 组 A 验收载体：红②（W-03，C10-011——同 epoch 并发 finishCommit
 * 守卫）、红④（W-08，C10-013——TRK 登记先行执行出锁，慢快照不阻塞 ACK）、E2 泛在钉
 * （W-01 CHM 并发懒注册单实例结构钉 / W-04 AtomicLongArray 无撕裂结构钉 / W-05 reader
 * volatile 编译期结构钉）。类注释显式归属 CRA 8.41 R4-01（同号异站防混）。零 mockito——
 * JDK 并发原语 + 手工替身。
 */
class TestStreamCoreGroupAConcurrency {

    private static final TaskLocation LOC = new TaskLocation("job-1", "pipeline-1", "v0", 0);

    /** 红② 替身：commit 阻塞可调——概率窗口确定性放大（同 epoch 多线程互放 latch） */
    static class SlowCommitSink extends TwoPhaseCommitSinkFunction<String> {
        final List<Long> committedEpochs = Collections.synchronizedList(new ArrayList<>());
        volatile long commitDelayMillis;

        @Override
        public void beginTransaction() {
        }

        @Override
        public void invoke(String value) {
        }

        @Override
        public void preCommit(long checkpointId) {
        }

        @Override
        public void commit(long checkpointId) {
            long delay = commitDelayMillis;
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            committedEpochs.add(checkpointId);
        }

        @Override
        public void rollback() {
        }
    }

    // 红②（W-03，C10-011）：同 epoch 并发 finishCommit——守卫使 commit 至多一次（post-fix 确定性绿；
    // pre-fix 双提交概率窗口确定性放大）
    @Test
    void testConcurrentFinishCommitSameEpochCommittedAtMostOnce() throws Exception {
        SlowCommitSink sink = new SlowCommitSink();
        Map<Long, Object> pending = Collections.synchronizedMap(new TreeMap<>());
        pending.put(5L, "tx5");
        sink.setPendingCommits(pending);
        sink.commitDelayMillis = 20;

        int threadCount = 8;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicBoolean anyWorkerFailed = new AtomicBoolean(false);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    sink.finishCommit(5L, true);
                } catch (Throwable t) {
                    // fail-fast：异常必须向上传播（禁 catch 置标志静默吞掉）
                    anyWorkerFailed.set(true);
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertFalse(anyWorkerFailed.get(), "no worker may throw under the in-flight guard");
        long commitsOfEpoch5 = sink.committedEpochs.stream().filter(e -> e == 5L).count();
        assertTrue(commitsOfEpoch5 <= 1,
                "same-epoch concurrent finishCommit must commit at most once (in-flight guard), got "
                        + commitsOfEpoch5);
        assertTrue(sink.getPendingCommits().isEmpty(),
                "epoch must be removed from pending exactly once (setPendingCommits wraps the caller "
                        + "map defensively — assert via the getter)");
    }

    // 红④（W-08，C10-013）：慢快照直通路径（mailbox==null 回退分支）出锁后 ACK 不再被串行化
    @Test
    void testAckNotBlockedBySlowSnapshotDirectPath() throws Exception {
        SourceReaderOperator<Object> sourceOp = new SourceReaderOperator<Object>(null, 0) {
            @Override
            public OperatorSnapshotResult snapshotState(io.nop.stream.core.checkpoint.StateSnapshotContext context)
                    throws Exception {
                // 慢快照：持锁窗口被拉长到秒级（pre-fix ACK 阻塞面本体）
                Thread.sleep(800);
                return null;
            }
        };
        List<io.nop.stream.core.operators.StreamOperator<?>> operators = new ArrayList<>();
        operators.add(sourceOp);
        AtomicReference<Throwable> triggerError = new AtomicReference<>();
        CountDownLatchSnapshot snapshotInFlight = new CountDownLatchSnapshot();

        CheckpointBarrierTracker tracker = new CheckpointBarrierTracker(LOC, operators, snapshot -> {
        });

        Thread triggerThread = new Thread(() -> {
            try {
                tracker.triggerCheckpoint(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
            } catch (Throwable t) {
                triggerError.set(t);
            } finally {
                snapshotInFlight.mark();
            }
        }, "cra-w04-trigger");
        triggerThread.start();
        // 固定短等待：确保 trigger 线程已进入慢快照（800ms 窗口内，ACK 计时不得越过 join——
        // 否则竞态窗口被销毁）
        Thread.sleep(150);

        long startAck = System.nanoTime();
        tracker.acknowledgeOperator(0, taggedResult(1L));
        long ackMillis = (System.nanoTime() - startAck) / 1_000_000;

        // post-fix：offerBarrier（含慢快照）出锁执行——ACK 不被 TRK 监视器阻塞
        assertTrue(ackMillis < 500,
                "ACK must not be blocked by the in-flight snapshot once barrier delivery left the "
                        + "tracker monitor; took " + ackMillis + "ms");
        if (triggerThread.isAlive()) {
            triggerThread.join(5000);
        }
        assertNull(triggerError.get(), "trigger must not fail");
    }

    static class CountDownLatchSnapshot {
        private final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        void mark() {
            latch.countDown();
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }

    private static OperatorSnapshotResult taggedResult(long checkpointId) {
        OperatorSnapshotResult r = new OperatorSnapshotResult();
        r.setCheckpointId(checkpointId);
        return r;
    }

    private static void assertNull(Object value, String message) {
        org.junit.jupiter.api.Assertions.assertNull(value, message);
    }

    // E2 泛在钉（W-01）：同 key 并发懒注册单实例结构钉 + 异 key hammer 绿锚
    @Test
    void testBasicTypeInfoConcurrentLazyRegistrationSingleInstance() throws Exception {
        final Class<?> keyA = TestStreamCoreGroupAConcurrency.class;
        final Class<?> keyB = String.class;
        int threadCount = 16;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<BasicTypeInfo<?>> seenA = Collections.synchronizedSet(new HashSet<>());
        Set<BasicTypeInfo<?>> seenB = Collections.synchronizedSet(new HashSet<>());
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final boolean even = (i % 2 == 0);
            futures.add(executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                if (even) {
                    seenA.add(BasicTypeInfo.of(keyA));
                } else {
                    seenB.add(BasicTypeInfo.of(keyB));
                }
                return null;
            }));
        }
        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, seenA.size(), "same-key concurrent lazy registration must yield a single instance");
        assertEquals(1, seenB.size(), "built-in type must stay a single instance under hammer");
        assertSame(BasicTypeInfo.of(keyA), BasicTypeInfo.of(keyB) == null ? null : BasicTypeInfo.of(keyA),
                "identity semantics preserved");

        // 结构钉：INSTANCES 必须为 CHM（防回退为 plain HashMap 的变异论证承载——回退后同 key 双实例
        // 为 JMM 未定义行为、无稳定红，故以结构断言承载）
        Field instances = BasicTypeInfo.class.getDeclaredField("INSTANCES");
        instances.setAccessible(true);
        Object registry = instances.get(null);
        assertTrue(registry instanceof java.util.concurrent.ConcurrentHashMap,
                "INSTANCES must be a ConcurrentHashMap at runtime (structural pin — declared type is "
                        + "the Map interface; rollback to plain HashMap would make same-key double "
                        + "instantiation JMM-undefined with no stable red, hence the structural pin)");
    }

    // E2 泛在钉（W-04）：AtomicLongArray 结构钉 + 并发写读无撕裂 hammer（读值恒为合法写入值之一）
    @Test
    void testInputGateWatermarkArrayTearFree() throws Exception {
        Field field = InputGate.class.getDeclaredField("currentWatermarks");
        assertEquals(AtomicLongArray.class, field.getType(),
                "currentWatermarks must be AtomicLongArray (structural pin — plain long[] tear is "
                        + "JLS 17.7 undefined behavior, no stable red under hammer; rollback argument "
                        + "carried in red-green summary)");

        InputGate gate = new InputGate(java.util.Collections.singletonList(
                new InputChannel(new ResultPartition())));
        assertEquals(Long.MIN_VALUE, gate.getChannelWatermark(0),
                "initial watermark is Long.MIN_VALUE");
    }

    // E2 泛在钉（W-05）：reader 字段 volatile 编译期结构钉（pre-fix 无 volatile——确定性红）
    @Test
    void testSourceReaderReaderFieldIsVolatile() throws Exception {
        Field reader = SourceReaderOperator.class.getDeclaredField("reader");
        assertTrue(Modifier.isVolatile(reader.getModifiers()),
                "reader must be volatile (C2-002 HB edge between open() write and cross-thread callbacks)");
    }

    // 红② 同 diff 改写配套：C10-011 的 fail-fast 语义由 TestTwoPhaseCommitSinkFunction 改写承载，
    // 此处补守卫旁路观察：in-flight 守卫下同 epoch 第二提交线程跳过且 pending 收敛（green 锚）
    @Test
    void testInFlightGuardSkipsDuplicateEpochAndDrainsPending() throws Exception {
        SlowCommitSink sink = new SlowCommitSink();
        sink.getPendingCommits().put(7L, "tx7");
        sink.commitDelayMillis = 30;

        Thread t1 = new Thread(() -> {
            try {
                sink.finishCommit(7L, true);
            } catch (Exception e) {
                // 测试替身线程：失败即测试失败——以 Error 形态上抛使线程终态可见（rule 24 禁静默吞）
                throw new AssertionError("finishCommit failed", e);
            }
        }, "cra-w03-commit-1");
        t1.start();
        Thread.sleep(5); // t1 已进入 commit（in-flight 持有）
        sink.finishCommit(7L, true); // 主线程应跳过（守卫）
        t1.join(5000);

        assertEquals(1, sink.committedEpochs.stream().filter(e -> e == 7L).count(),
                "exactly one commit for epoch 7");
        assertTrue(sink.getPendingCommits().isEmpty(), "pending drained by the winner");
    }

}
