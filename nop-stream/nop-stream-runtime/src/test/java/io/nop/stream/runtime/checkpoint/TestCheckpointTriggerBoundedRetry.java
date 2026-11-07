package io.nop.stream.runtime.checkpoint;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.testsupport.TestAwait;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 369 Phase 1 C2: bounded-retry trigger for terminal/savepoint
 * checkpoints ({@link CheckpointCoordinator#triggerCheckpointBounded}).
 *
 * <p>Pre-fix repro (silent skip on in-flight collision) recorded in
 * {@code _tmp/r6-p1-c2-repro.log} via
 * {@code ReproC2TerminalSavepointSkippedOnInFlightCollision}: the raw
 * {@code tryTriggerPendingCheckpoint} returned null while a periodic
 * checkpoint was in flight and the production callers silently skipped the
 * terminal savepoint.
 *
 * <p>时间确定化（plan 2510）：{@code triggerCheckpointBounded} 的 deadline 读取经
 * {@code CoreMetrics}，测试注册 {@link ManualClock} 显式推进虚拟时间越过预算截止点；
 * 生产重试间的 backoff sleep 属于生产实现内部轮询（允许形态）。「确在重试」的
 * 结构性证据 = 辅助线程的时钟读取计数随重试迭代增长（re-evaluation evidence），
 * 不再断言墙钟耗时阈值。
 */
class TestCheckpointTriggerBoundedRetry {

    private static final TaskLocation LOC_1 = new TaskLocation("j", "p", "v1", 0);
    private static final TaskLocation LOC_2 = new TaskLocation("j", "p", "v2", 0);

    /** 重试证据阈值：deadline 读取 1 次 + 每个重试迭代的 now 读取 ≥ 2 次。 */
    private static final int RETRY_EVIDENCE_READS = 3;

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;
    private ManualClock clock;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointTimeout(30000L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        coordinator = new CheckpointCoordinator("j", "p", idCounter,
                new LocalFileCheckpointStorage(tempDir.toString()), config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
        // 时钟是 JVM 全局量：逐测试恢复默认时钟，避免冻结同 fork 后续所有类。
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

    /**
     * 在辅助线程上调用 {@code triggerCheckpointBounded}（虚拟时钟冻结时预算永不
     * 自然到期，调用线程会一直以真实 backoff sleep 重试，直到测试推进时钟或
     * 解除阻塞）。返回线程引用供时钟读取计数取证，结果经 AtomicReference 收集。
     */
    private Thread triggerOnHelperThread(CheckpointType type, long budgetMs,
                                         AtomicReference<Object> result,
                                         CountDownLatch done) {
        Thread helper = new Thread(() -> {
            try {
                result.set(coordinator.triggerCheckpointBounded(type, budgetMs));
            } catch (Exception e) {
                result.set(e);
            } finally {
                done.countDown();
            }
        }, "bounded-trigger-helper");
        helper.setDaemon(true);
        helper.start();
        return helper;
    }

    private static PendingCheckpoint asPending(Object result) {
        return (PendingCheckpoint) result;
    }

    private static Exception asException(Object result) {
        return (Exception) result;
    }

    /**
     * In-flight collision → bounded retry: the terminal savepoint trigger
     * waits for the in-flight periodic checkpoint to complete (ACKed on demand,
     * after the retry evidence is established) and then succeeds.
     */
    @Test
    void testInFlightCollisionRetriesUntilPeriodicCompletes() throws Exception {
        PendingCheckpoint periodic = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(periodic);

        AtomicReference<Object> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread helper = triggerOnHelperThread(CheckpointType.TERMINAL_SAVEPOINT, 10_000L, result, done);

        // 结构性重试证据：辅助线程的时钟读取计数增长（deadline 读取 + 每个重试迭代
        // 的 now 读取），即 trigger 确实在周期 checkpoint 在途期间反复重试 —— 替代
        // 旧的墙钟耗时阈值断言。
        TestAwait.until("bounded trigger observed retrying while the periodic checkpoint was in flight",
                () -> clock.readsBy(helper) >= RETRY_EVIDENCE_READS);

        // Complete the in-flight periodic checkpoint from the test thread — the
        // helper's next retry must now succeed.
        coordinator.acknowledgeTask(LOC_1, periodic.getCheckpointId(),
                TaskStateSnapshot.builder(LOC_1).checkpointId(periodic.getCheckpointId())
                        .putOperatorState("op", "p1").build());
        coordinator.acknowledgeTask(LOC_2, periodic.getCheckpointId(),
                TaskStateSnapshot.builder(LOC_2).checkpointId(periodic.getCheckpointId())
                        .putOperatorState("op", "p2").build());

        assertTrue(done.await(10, TimeUnit.SECONDS), "bounded trigger must return after the collision clears");
        assertTrue(result.get() instanceof PendingCheckpoint,
                "Terminal savepoint must be triggered after the in-flight checkpoint clears "
                        + "(bounded retry), got: " + result.get());
        PendingCheckpoint terminal = asPending(result.get());
        assertEquals(CheckpointType.TERMINAL_SAVEPOINT, terminal.getCheckpointType());
        helper.join(5000L);
    }

    /**
     * NO_TASKS_TO_ACK → short-circuit success (null return). With the C3
     * participant contraction an empty ACK set on a terminal trigger means
     * every task is terminal — treating that as a failure would turn a normal
     * shutdown into a job failure (the C2×C3 interaction).
     *
     * <p>「立即返回」的确定性口径：调用在辅助线程上执行并在挂死保护上限内返回
     * （若实现错误地对 NO_TASKS 进入重试循环，冻结的虚拟时钟下该循环永不自然
     * 到期，只能在超时处变红）；短路径自身不消费任何虚拟预算（时钟不推进）。
     */
    @Test
    void testNoTasksToAckShortCircuitsAsSuccess() throws Exception {
        coordinator.setTasksToAcknowledge(java.util.Collections.emptyList());
        long virtualBefore = clock.peekMillis();

        AtomicReference<Object> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread helper = triggerOnHelperThread(CheckpointType.TERMINAL_SAVEPOINT, 5000L, result, done);
        assertTrue(done.await(10, TimeUnit.SECONDS),
                "NO_TASKS_TO_ACK must short-circuit immediately (no retry loop)");
        assertNull(result.get(), "NO_TASKS_TO_ACK short-circuits as success (null)");
        assertEquals(virtualBefore, clock.peekMillis(),
                "the short-circuit path must not consume any virtual retry budget");
        helper.join(5000L);
    }

    /**
     * Retry budget exhausted → loud typed failure. The in-flight periodic
     * checkpoint is never ACKed, so the back-pressure rejection never clears;
     * the test advances the virtual clock past the retry budget — the helper's
     * next deadline re-evaluation fails loudly with ERR_STREAM_CHECKPOINT_FAILED.
     */
    @Test
    void testRetryBudgetExhaustedFailsLoud() throws Exception {
        PendingCheckpoint periodic = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(periodic);
        // Never ACK the periodic checkpoint.

        AtomicReference<Object> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread helper = triggerOnHelperThread(CheckpointType.TERMINAL_SAVEPOINT, 600L, result, done);

        // 结构性重试证据：辅助线程已在预算窗口内反复重试（时钟读取计数增长），
        // 此前推进时钟必然只会让下一次 deadline 重评失败——确定性预算耗尽。
        TestAwait.until("bounded trigger observed retrying inside the budget window",
                () -> clock.readsBy(helper) >= RETRY_EVIDENCE_READS);
        assertFalse(done.getCount() == 0,
                "the budget must not be exhausted before the virtual deadline passes");

        // 推进虚拟时间越过预算截止点：下一次重评 now >= deadline → 类型化失败。
        clock.advance(600L);
        assertTrue(done.await(10, TimeUnit.SECONDS),
                "the bounded trigger must fail once the virtual clock passes the budget");
        Object outcome = result.get();
        if (!(outcome instanceof Exception)) {
            fail("Expected ERR_STREAM_CHECKPOINT_FAILED when the retry budget is exhausted, got: " + outcome);
        }
        StreamException e = (StreamException) asException(outcome);
        assertEquals("nop.err.stream.checkpoint-failed", e.getErrorCode().toString(),
                "Budget exhaustion must fail with the typed checkpoint-failed error");
        assertTrue(String.valueOf(e.toString()).contains("TERMINAL_SAVEPOINT"),
                "The failure message must name the blocked checkpoint type");
        helper.join(5000L);
    }
}
