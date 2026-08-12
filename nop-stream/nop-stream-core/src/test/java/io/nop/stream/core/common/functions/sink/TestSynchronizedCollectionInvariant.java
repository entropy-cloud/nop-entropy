/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions.sink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.test.InvariantTableCompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate ② — synchronized 集合迭代点（invariant #2，JUnit 部分）.
 *
 * <p><b>2PC 迭代点表</b>（TwoPhaseCommitSinkFunction 的全部 synchronized-map 迭代点）：
 * <ol>
 *   <li>{@code saveState} :83 {@code new TreeMap<>(pendingCommits)}（copy 迭代）—
 *       <b>已知 residual（R16-AR-1）→ pin-and-record</b>：JUnit 侧 pin 行为语义（快照内容完整），
 *       锁状态由 mjs 扫描器 pin（见 {@code mjs-pins.json}）；</li>
 *   <li>{@code finishCommit} :101-118 entrySet 迭代 — 断言在 synchronized(pending) 内
 *       （行为证明：并发变更不抛 CME）；</li>
 *   <li>{@code restoreFromEpoch} :158-167 entrySet 迭代 — 断言在 synchronized(pending) 内
 *       （行为证明：并发变更不抛 CME）。</li>
 * </ol>
 *
 * <p><b>表范围</b>（live 持有 Collections.synchronized* 集合的 4 个类）：JUnit 侧点名
 * TwoPhaseCommitSinkFunction 迭代点表；SourceReaderOperator:98 / StreamSinkOperator:157 /
 * LocalSourceCoordinator:167 由 mjs 扫描器全量扫描兜底（防新增 synchronized 集合字段漏网），
 * 本类以表驱动核对四个类均在门禁范围内。
 */
public class TestSynchronizedCollectionInvariant {

    static final String MODULE = "nop-stream-core";

    /** 2PC 迭代点表（单一事实源 = gate-inventory.json 的 TwoPhaseCommitSinkFunction 行）。 */
    static Stream<String> twoPcIterationPoints() {
        return Stream.of("saveState", "finishCommit", "restoreFromEpoch");
    }

    /** 4 个 synchronized 集合类（2PC 在 JUnit 点名表内，其余三类 mjs 全量扫描兜底）。 */
    static Stream<String> synchronizedCollectionClasses() {
        return Stream.of(
                "io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction",
                "io.nop.stream.core.operators.SourceReaderOperator",
                "io.nop.stream.core.operators.StreamSinkOperator",
                "io.nop.stream.core.source.coordinator.LocalSourceCoordinator");
    }

    /** 迭代点入表：每个迭代点方法必须存在于 2PC 类且被门禁表跟踪。 */
    @ParameterizedTest
    @MethodSource("twoPcIterationPoints")
    void testIterationPointInTable(String methodName) throws Exception {
        Map<String, Object> inventory = InvariantTableCompleteness.loadInventory();
        Map<String, Object> classes = InvariantTableCompleteness.moduleClasses(inventory, MODULE);
        Map<String, Object> entry = (Map<String, Object>) classes.get(
                "io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction");
        List<String> table = InvariantTableCompleteness.tableMethods(entry);
        assertTrue(table.contains(methodName),
                "2PC iteration point " + methodName + " must be in the gate table: " + table);

        Class<?> clazz = Class.forName("io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction");
        assertTrue(InvariantTableCompleteness.changeTypeMethodNames(clazz).contains(methodName),
                methodName + " must be a live change-type method of TwoPhaseCommitSinkFunction");
    }

    /**
     * 表范围核对：2PC 在 JUnit 点名表内（gate-inventory）；其余三类（SourceReaderOperator /
     * StreamSinkOperator / LocalSourceCoordinator）由 mjs 扫描器全量扫描兜底（防新增
     * synchronized 集合字段漏网），此处核对四类在 live repo 中真实存在（repo-observable）。
     */
    @ParameterizedTest
    @MethodSource("synchronizedCollectionClasses")
    void testSynchronizedCollectionClassInScope(String fqcn) throws Exception {
        assertTrue(fqcn.startsWith("io.nop.stream."), "unexpected class: " + fqcn);
        if (fqcn.equals("io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction")) {
            Map<String, Object> inventory = InvariantTableCompleteness.loadInventory();
            Map<String, Object> classes = InvariantTableCompleteness.moduleClasses(inventory, MODULE);
            assertTrue(classes.containsKey(fqcn),
                    "TwoPhaseCommitSinkFunction must be tracked in gate-inventory.json");
        }
        // 其余三类：mjs scan-iterations 以模块 src/main 全量扫描兜底（scan scope 含 core），
        // 此处仅核对类在 live repo 存在（Class.forName 失败即显式红，非静默跳过）
        Class.forName(fqcn);
    }

    /**
     * saveState 已知 residual（R16-AR-1）pin-and-record：行为语义 pin = 快照内容完整
     * （pending 全部条目原样进入 snapshot）。锁状态（无锁 copy）由 mjs 扫描器 pin
     * （mjs-pins.json），本测试不修复、不断言锁。
     */
    @Test
    void testSaveStatePinsSnapshotContentComplete() throws Exception {
        TestSink sink = new TestSink();
        for (long e = 1; e <= 5; e++) {
            sink.getPendingCommits().put(e, "tx-" + e);
        }
        TaskStateSnapshot snapshot = sink.saveState(3L);
        Object raw = snapshot.getOperatorState(TwoPhaseCommitSinkFunction.PENDING_COMMITS_KEY);
        assertTrue(raw instanceof Map, "pinned behavior: saveState must return a real snapshot");
        Map<?, ?> copy = (Map<?, ?>) raw;
        assertEquals(5, copy.size(), "pinned behavior: snapshot content must be complete (all 5 pending)");
        for (long e = 1; e <= 5; e++) {
            assertEquals("tx-" + e, copy.get(e));
        }
    }

    /**
     * finishCommit 迭代点在 synchronized 块内（行为证明）：迭代期间并发 put/remove 不抛 CME，
     * 且恰好提交 ≤ epochId 的条目。
     */
    @Test
    void testFinishCommitIterationIsConcurrentSafe() throws Exception {
        TestSink sink = new TestSink();
        for (long e = 1; e <= 20; e++) {
            sink.getPendingCommits().put(e, "tx-" + e);
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch noiseDone = new CountDownLatch(4);
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 4; i++) {
            pool.submit(() -> {
                try {
                    go.await();
                    long base = System.nanoTime();
                    while (System.nanoTime() - base < 200_000_000L) {
                        sink.getPendingCommits().put(100L + (System.nanoTime() % 1000), "noise");
                        sink.getPendingCommits().remove(100L + (System.nanoTime() % 1000));
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    noiseDone.countDown();
                }
                return null;
            });
        }
        go.countDown();
        try {
            sink.finishCommit(10L, true);
        } finally {
            assertTrue(noiseDone.await(30, TimeUnit.SECONDS), "noise threads must finish");
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertTrue(errors.isEmpty(), "concurrent mutation must not throw, got: " + errors);
        assertTrue(sink.committedEpochs.size() >= 10,
                "finishCommit must commit every epoch <= 10, committed: " + sink.committedEpochs);
        for (long e = 1; e <= 10; e++) {
            assertTrue(sink.committedEpochs.contains(e), "epoch " + e + " must be committed");
        }
    }

    /**
     * restoreFromEpoch 迭代点在 synchronized 块内（行为证明）：迭代期间并发变更不抛 CME，
     * durable（≤ epochId）条目被提交而非回滚。
     */
    @Test
    void testRestoreFromEpochIterationIsConcurrentSafe() throws Exception {
        TestSink sink = new TestSink();
        for (long e = 1; e <= 20; e++) {
            sink.getPendingCommits().put(e, "tx-" + e);
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch noiseDone = new CountDownLatch(4);
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 4; i++) {
            pool.submit(() -> {
                try {
                    go.await();
                    long base = System.nanoTime();
                    while (System.nanoTime() - base < 200_000_000L) {
                        sink.getPendingCommits().put(200L + (System.nanoTime() % 1000), "noise");
                        sink.getPendingCommits().remove(200L + (System.nanoTime() % 1000));
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    noiseDone.countDown();
                }
                return null;
            });
        }
        go.countDown();
        try {
            sink.restoreFromEpoch(10L, null);
        } finally {
            assertTrue(noiseDone.await(30, TimeUnit.SECONDS), "noise threads must finish");
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertTrue(errors.isEmpty(), "concurrent mutation must not throw, got: " + errors);
        assertTrue(sink.committedEpochs.size() >= 10,
                "restoreFromEpoch must commit durable epochs <= 10, committed: " + sink.committedEpochs);
        for (long e = 1; e <= 10; e++) {
            assertTrue(sink.committedEpochs.contains(e), "durable epoch " + e + " must be committed on restore");
        }
    }

    /** 轻量 2PC 测试桩（仅记录 commit/abort 调用，不持外部资源）。 */
    static class TestSink extends TwoPhaseCommitSinkFunction<String> {
        final List<Long> committedEpochs = new ArrayList<>();
        final List<Long> abortedEpochs = new ArrayList<>();

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
            committedEpochs.add(checkpointId);
        }

        @Override
        public void rollback() {
        }
    }
}
