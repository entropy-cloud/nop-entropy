package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-02 end-to-end proof: a region-scoped restart (consumer region) must
 * re-wire the rebuilt task into the checkpoint pipeline so that post-restart
 * checkpoints full-ACK and complete instead of timing out and aborting the job.
 *
 * <p>Graph: Source(A) →[materialization edge]→ FailingSink(B).
 * Region 0 = {A} (producer), Region 1 = {B} (consumer, fails once on first
 * consume, restarted by the supervision loop).
 *
 * <p>时间确定化（plan 2510）：
 * <ul>
 *   <li>源发出 s1..s30 后在 latch 上显式阻塞（分区保持打开），直到重启后的消费者
 *       已消费 30 条（结构性证据 = 重启 + 回放/追平完成），随后发出 s31..s60 作为
 *       重连后从活分区实时读取的数据——「重启时源仍存活 + 重启后仍有实时数据」
 *       不再依赖固定 pacing，在任意机器速度下成立。</li>
 *   <li>源的 EOS 由可观测的 checkpoint 证据门控：barrier 只能在 collect 边界注入，
 *       故源以 pad 元素（结果断言外单独计数）保持 collect 边界，往返条件等待为
 *       节奏，直到共享存储出现 ≥ 2 个已完成 epoch manifest（后台 watcher 条件等待
 *       置位）——作业必然活过 trigger(300ms)+ACK 窗口；RED 形态（post-restart
 *       checkpoint 从不 ACK → timeout abort）不受影响；上限仅作挂死保护。
 *       checkpoint 周期触发/超时是 coordinator 内部真实调度器（不可注入），属被测
 *       语义的具名残余。</li>
 * </ul>
 *
 * <p>Green assertion = observable full-ACK evidence: the job completes without
 * exception AND the shared storage holds multiple completed checkpoints
 * (manifests) — at least one checkpoint must have completed after the restart.
 */
class TestSupervisionLoopCheckpointReconnectE2E {

    /** pad-loop cap so a broken checkpoint path still EOS's (hang protection only). */
    static final int PAD_CAP = 20_000;

    @TempDir
    Path tempDir;

    private static final String JOB_ID = "sl-checkpoint-reconnect";

    @Test
    void testRegionRestartRewiresCheckpointPipeline_checkpointsCompleteAfterRestart() throws Exception {
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean hasFailedOnce = new AtomicBoolean(false);
        /** Opened by the sink per recorded record; the source's live-tail release gate. */
        java.util.concurrent.CountDownLatch replayed = new java.util.concurrent.CountDownLatch(30);
        /** Set when ≥ 2 epoch manifests are durably stored (or the job ended). */
        AtomicBoolean manifestsReady = new AtomicBoolean(false);
        AtomicBoolean jobEnded = new AtomicBoolean(false);
        /** Sink-side count of consumed pad elements — the source's round-trip pacing signal. */
        java.util.concurrent.atomic.AtomicInteger padsSeen = new java.util.concurrent.atomic.AtomicInteger();

        // Source: s1..s30 → wait for the restarted consumer to have consumed 30
        // records → s31..s60 live → keep making collect boundaries (pads) until the
        // checkpoint evidence exists, then EOS.
        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                for (int i = 1; i <= 30; i++) {
                    ctx.collect("s" + i);
                }
                // 显式同步：等重启后的消费者消费 30 条（latch 上限仅作挂死保护）
                replayed.await(60, java.util.concurrent.TimeUnit.SECONDS);
                for (int i = 31; i <= 60; i++) {
                    ctx.collect("s" + i);
                }
                // EOS 门：barrier 只能在 source 的 collect 边界注入，故保持 collect
                // 边界（pad 元素，sink 侧单独计数、不进入结果断言），以「上一个 pad
                // 已被消费者消费」为节奏（往返条件等待），直到 ≥ 2 个 manifest 落盘
                // （checkpoint 全链路 ACK 的可观测证据）再 EOS。上限仅作挂死保护。
                for (int p = 0; !manifestsReady.get() && !jobEnded.get() && p < PAD_CAP; p++) {
                    int before = padsSeen.get();
                    ctx.collect("pad-" + p);
                    int expected = before + 1;
                    io.nop.stream.core.testsupport.TestAwait.until(
                            "pad consumed downstream", () -> padsSeen.get() >= expected, 5000);
                }
            }

            @Override
            public void cancel() {
            }
        };

        // Sink fails once on the FIRST consume (before recording), succeeds after.
        // Pad elements are counted separately and excluded from the result assertions.
        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                if (value.startsWith("pad-")) {
                    padsSeen.incrementAndGet();
                    return;
                }
                if (!hasFailedOnce.getAndSet(true)) {
                    throw new io.nop.stream.core.exceptions.StreamException("Injected first-consume failure (checkpoint reconnect E2E)");
                }
                sinkResults.add(value);
                replayed.countDown();
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        io.nop.stream.core.execution.task.StreamTaskInvokable sourceInvokable =
                new io.nop.stream.core.execution.task.StreamTaskInvokable(sourceChain);
        io.nop.stream.core.execution.task.StreamTaskInvokable sinkInvokable =
                new io.nop.stream.core.execution.task.StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("slck-src", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("slck-sink", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-sl-checkpoint-reconnect");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);

        JobEdge edge = new JobEdge("slck-src", "slck-sink", ResultPartitionType.PIPELINED);
        edge.setMaterializationEnabled(true);
        jobGraph.addEdge(edge);

        assertEquals(2, jobGraph.decomposeRegions().getRegionCount(),
                "Materialization edge should split into 2 regions");

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(300L);
        config.setCheckpointTimeout(1500L);
        config.setMinPause(100L);
        config.setStorageProperty("path", tempDir.toString());

        // Public entry point (Rule #22): the supervision-loop/checkpoint wiring is
        // internal (private static), so the E2E must go through
        // GraphModelCheckpointExecutor.executeWithCheckpoint.
        //
        // EOS-gate watcher: open the source's EOS gate once ≥ 2 epoch manifests are
        // durably stored (observable full-ACK evidence), or as soon as the job ends
        // (abort path — the assertions below then fail deterministically). 条件等待，
        // 无固定窗口；daemon 线程不阻塞测试收尾。
        LocalFileCheckpointStorage gateStorage = new LocalFileCheckpointStorage(tempDir.toString());
        Thread manifestWatcher = new Thread(() -> {
            io.nop.stream.core.testsupport.TestAwait.until(
                    "at least 2 checkpoint manifests durably stored (or the job ended)",
                    () -> jobEnded.get() || manifestCount(gateStorage) >= 2);
            manifestsReady.set(true);
        }, "checkpoint-manifest-gate");
        manifestWatcher.setDaemon(true);
        manifestWatcher.start();

        try {
            GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "test-sl-checkpoint-reconnect", config);
        } finally {
            jobEnded.set(true);
            manifestWatcher.join(5000);
        }

        // The job must NOT have been aborted (no ERR_STREAM_CHECKPOINT_ABORTED),
        // and every record must have been processed exactly once (the failing
        // first consume is replayed by the materialization replay).
        assertEquals(60, sinkResults.size(),
                "All 60 records must be processed exactly once after restart. "
                        + "Results: " + sinkResults);

        // Observable full-ACK evidence: at least 2 completed checkpoints stored
        // (≥1 of them after the restart — post-restart checkpoints full-ACKed).
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        List<EpochManifest> manifests = storage.loadRetainedEpochManifests(
                JOB_ID, "1", Integer.MAX_VALUE);
        assertTrue(manifests.size() >= 2,
                "Multiple checkpoints must complete across the restart (full-ACK wiring evidence). "
                        + "Manifest epochs: " + manifests.stream()
                        .map(EpochManifest::getEpochId)
                        .collect(java.util.stream.Collectors.toList()));

        storage.deleteAllCheckpoints(JOB_ID);
    }

    private static int manifestCount(LocalFileCheckpointStorage storage) {
        try {
            return storage.loadRetainedEpochManifests(JOB_ID, "1", Integer.MAX_VALUE).size();
        } catch (Exception e) {
            return 0;
        }
    }
}
