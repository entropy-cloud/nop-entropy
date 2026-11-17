/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.maintain;

import io.nop.stream.core.exceptions.StreamException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-10): state reset tool — explicit refusal semantics and the
 * reset-then-replay-from-start e2e demonstration.
 */
class TestStreamStateResetTool {

    @TempDir
    Path tempDir;

    private static final String JOB_ID = "reset-e2e-job";

    private JobGraph buildReplayableGraph(List<Integer> sinkResults, int recordCount) {
        SourceFunction<Integer> source = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;
            private volatile boolean running = true;

            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                // 时间确定化（plan 2510）：原 20ms/条 pacing 只是把运行拉过真实
                // checkpoint 周期以制造 durable 工件——运行终止路径的 final
                // checkpoint（CANCEL 模式 triggerFinalCheckpoint）确定性完成并
                // 持久化，满足「durable checkpoint state exists」断言，无真实
                // 时间依赖。
                for (int i = 1; i <= recordCount; i++) {
                    ctx.collect(i);
                }
            }

            @Override
            public void cancel() {
                running = false;
            }
        };
        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<Integer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
                sinkResults.add(value);
            }
        });
        OperatorChain sc = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain kc = new OperatorChain(Collections.singletonList(sinkOp));
        JobGraph g = new JobGraph(JOB_ID);
        g.addVertex(new JobVertex("source-1", "Source", 1, Collections.singletonList(sc),
                new StreamTaskInvokable(sc)));
        g.addVertex(new JobVertex("sink-2", "Sink", 1, Collections.singletonList(kc),
                new StreamTaskInvokable(kc)));
        g.addEdge(new JobEdge("source-1", "sink-2", ResultPartitionType.PIPELINED));
        return g;
    }

    private List<Integer> runJob() throws Exception {
        List<Integer> sinkResults = Collections.synchronizedList(new ArrayList<>());
        JobGraph graph = buildReplayableGraph(sinkResults, 10);
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(100);
        config.setCheckpointTimeout(5000);
        config.setProcessingGuarantee(ProcessingGuarantee.STRICT_EXACTLY_ONCE);
        config.setStorageProperty("path", tempDir.toString());
        GraphModelCheckpointExecutor.executeWithCheckpoint(graph, JOB_ID, config);
        return sinkResults;
    }

    /**
     * 时间确定化（plan 2510/WI-8）：executeWithCheckpoint 返回后 CANCEL 模式 final
     * checkpoint 的持久化完成仍是异步的（triggerFinalCheckpoint javadoc 明示
     * "Completion of the triggered checkpoint itself remains asynchronous"，持久化
     * 写盘由 coordinator 的 persist executor 带 grace window 收敛）。全 reactor 负载下
     * runJob() 返回时 durable 工件可能尚未落盘——任何「durable state 存在」断言必须先
     * 等待到确定性证据（latest checkpoint 可读），禁止把「恰好来得及写完」当前提。
     */
    private void awaitDurableCheckpointState() throws InterruptedException {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        // 60s budget: the 10s window was too tight under full-reactor -T1C load
        // (2026-11-17 full-build failure — the checkpoint did persist, just
        // later than 10s under 16-way contention). This is a wait budget, not
        // an assertion: the pass condition remains the deterministic
        // artifacts, only the ceiling moves.
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (storage.getLatestCheckpoint(JOB_ID, "1") != null
                    && Files.exists(tempDir.resolve(JOB_ID))) {
                return;
            }
            // RESIDUAL（gated 异步持久化收敛面，plan 2514 具名裁定）：checkpoint 落盘经
            // persist executor 异步收敛，无同步钩子可等；轮询间隔 10ms 仅作条件等待的
            // 节拍，判定条件是确定性产物（manifest + 目录），超时即失败——非 sleep 断言。
            Thread.sleep(10);
        }
        throw new AssertionError("durable checkpoint state did not appear within 60s after runJob()"
                + " (durable persistence lag even under load — investigate the persist executor)");
    }

    @Test
    void resetThenReplayFromStart() throws Exception {
        // run 1: full output + durable state on disk
        List<Integer> run1 = runJob();
        assertEquals(10, run1.size(), "run 1 processes all records");
        awaitDurableCheckpointState();
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        assertNotNull(storage.getLatestCheckpoint(JOB_ID, "1"),
                "durable checkpoint state exists after run 1");
        assertTrue(Files.exists(tempDir.resolve(JOB_ID)), "job state directory exists");

        // reset: clears durable checkpoints + source cursors
        StreamStateResetTool.ResetResult result =
                StreamStateResetTool.reset(JOB_ID, tempDir.toString(), true, null);
        assertEquals(JOB_ID, result.getJobId());
        assertEquals(tempDir.resolve(JOB_ID), result.getDeletedPath());
        assertTrue(result.getDeletedCheckpoints() >= 1,
                "at least one checkpoint artifact counted: " + result.getDeletedCheckpoints());
        assertFalse(Files.exists(tempDir.resolve(JOB_ID)), "state directory gone after reset");

        // run 2 (same jobId, fresh state): replays from the start — full output again
        List<Integer> run2 = runJob();
        assertEquals(10, run2.size(), "after reset the job replays from the start: " + run2);
        for (int i = 1; i <= 10; i++) {
            assertTrue(run2.contains(i));
        }
    }

    @Test
    void refusesNonReplayableSource() throws Exception {
        runJob(); // ensure state dir exists
        awaitDurableCheckpointState();
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamStateResetTool.reset(JOB_ID, tempDir.toString(), false, null));
        assertTrue(ex.getMessage().contains("NON-REPLAYABLE"),
                "refusal explains the non-replayable reason: " + ex.getMessage());
        // state untouched (refused, not best-effort-cleared)
        assertTrue(Files.exists(tempDir.resolve(JOB_ID)));
    }

    @Test
    void refusesMissingStateDirectory() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamStateResetTool.reset("typo-job", tempDir.toString(), true, null));
        assertTrue(ex.getMessage().contains("no local state directory"),
                "wrong path refuses instead of silently succeeding: " + ex.getMessage());
    }

    @Test
    void refusesBlankArguments() {
        assertThrows(StreamException.class,
                () -> StreamStateResetTool.reset(" ", tempDir.toString(), true, null));
        assertThrows(StreamException.class,
                () -> StreamStateResetTool.reset("j", " ", true, null));
    }

    @Test
    void refusesActiveCoordinator() throws Exception {
        runJob();
        awaitDurableCheckpointState();
        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerCoordinator(JOB_ID, "coordinator-live", 42L);
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamStateResetTool.reset(JOB_ID, tempDir.toString(), true, registry));
        assertTrue(ex.getMessage().contains("active coordinator"),
                "running job refuses reset: " + ex.getMessage());
        assertTrue(Files.exists(tempDir.resolve(JOB_ID)), "state untouched after refusal");
    }

    // ====================== F-08 (plan 2026-09-04-1326-3): path-traversal rejection =====

    /**
     * A typo'd/traversal jobId must NEVER resolve onto a sibling job's state directory:
     * {@code ../other-job} previously deleted the SIBLING's entire state. The reset is
     * a destructive recursive delete — the same two-stage discipline as
     * {@code LocalFileCheckpointStorage} (charset guard + canonical containment) applies.
     */
    @Test
    void refusesDotDotJobIdAndPreservesSiblingState() throws Exception {
        runJob(); // own state dir exists
        awaitDurableCheckpointState();
        // a sibling job's state that the traversal WOULD have deleted
        Path sibling = tempDir.resolve("other-job");
        Files.createDirectories(sibling.resolve("checkpoints"));
        Files.writeString(sibling.resolve("checkpoints/precious.txt"), "sibling state");

        io.nop.stream.core.exceptions.StreamException ex = assertThrows(
                io.nop.stream.core.exceptions.StreamException.class,
                () -> StreamStateResetTool.reset("../other-job", tempDir.toString(), true, null),
                "traversal jobId must be rejected typed");
        assertTrue(String.valueOf(ex).contains("[a-zA-Z0-9_-]"),
                "rejection names the legal id charset: " + ex);

        assertTrue(Files.exists(sibling.resolve("checkpoints/precious.txt")),
                "sibling state must survive the refused reset");
        assertTrue(Files.exists(tempDir.resolve(JOB_ID)), "own state untouched by refused reset");
    }

    @Test
    void refusesEncodedAndAbsolutePathJobIds() throws Exception {
        runJob();
        awaitDurableCheckpointState();
        // Encoded variants still contain illegal characters ('%', '/', ':') — the charset
        // guard rejects them before any path arithmetic.
        for (String hostile : new String[]{"..%2Fother-job", "%2e%2e/other-job", "/abs/path",
                "..\\other-job", "..", ".", "job/../other-job"}) {
            io.nop.stream.core.exceptions.StreamException ex = assertThrows(
                    io.nop.stream.core.exceptions.StreamException.class,
                    () -> StreamStateResetTool.reset(hostile, tempDir.toString(), true, null),
                    "hostile jobId must be rejected typed: " + hostile);
            assertTrue(String.valueOf(ex).contains("jobId"),
                    "rejection names the offending argument: " + ex);
        }
        // zero legal-use-case loss: legal charset jobIds pass the guards (they may still
        // refuse on missing state, which is the pre-existing honest refusal)
        assertTrue(Files.exists(tempDir.resolve(JOB_ID)));
        assertDoesNotThrowReset();
    }

    private void assertDoesNotThrowReset() {
        try {
            StreamStateResetTool.reset(JOB_ID, tempDir.toString(), true, null);
        } catch (Exception e) {
            throw new AssertionError("legal jobId reset must not be rejected by the F-08 guards", e);
        }
    }
}
