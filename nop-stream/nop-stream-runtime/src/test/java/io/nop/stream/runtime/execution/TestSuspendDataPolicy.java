/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.JobTerminationMode;
import io.nop.stream.core.checkpoint.SuspendDataPolicy;
import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.source.CollectionReplayableSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SKIP_GAP_UNSUPPORTED_SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9 (plan 2333): SUSPEND downtime-gap contract, end-to-end over the embedded
 * {@link GraphModelCheckpointExecutor}.
 *
 * <p>Harness (audit P5 pin): the {@code CollectionReplayableSource} holds a
 * reference to the caller's (shared, mutable) list, so the test can append data
 * WHILE the job is suspended — the downtime gap — and re-submit the same jobId
 * against the same explicit storage path (the recovery is the resubmission).
 * Each round runs with {@code jobTerminationMode=SUSPEND}, so every execution
 * ends with a durable terminal savepoint.
 *
 * <p>Determinism: round 1's source drains its list in microseconds, so no
 * periodic checkpoint completes mid-run; the SUSPEND terminal savepoint is
 * assembled from inherited terminal states and carries an (honestly) empty task
 * state. Round 2 therefore restores no source offset — which is exactly what
 * makes the two policies observable:
 * <ul>
 *   <li>{@code REPLAY_GAP}: the source starts at 0 and re-emits everything
 *       including the downtime data (recompute).</li>
 *   <li>{@code SKIP_GAP}: the WI9 hook seeks the collection source to its
 *       {@code size()}, so nothing is re-emitted. Without the hook this test is
 *       red (the source would replay from 0) — mutation-sensitive by
 *       construction.</li>
 * </ul>
 */
class TestSuspendDataPolicy {

    private static final String JOB_ID = "suspend-data-policy";
    private static final String PIPELINE_ID = "pipeline-0";

    @TempDir
    Path tempDir;

    @Test
    void replayGapReplaysDowntimeData() throws Exception {
        List<String> data = Collections.synchronizedList(new ArrayList<>(Arrays.asList("a", "b", "c")));
        List<String> sink1 = new ArrayList<>();
        suspendOnce(data, sink1, SuspendDataPolicy.REPLAY_GAP);
        assertEquals(Arrays.asList("a", "b", "c"), sink1, "round 1 consumes the initial list");

        // downtime: the "external source" gains data while the job is suspended
        data.add("d");
        data.add("e");

        List<String> sink2 = new ArrayList<>();
        suspendOnce(data, sink2, SuspendDataPolicy.REPLAY_GAP);

        assertTrue(sink2.contains("d") && sink2.contains("e"),
                "REPLAY_GAP (default) must replay the downtime data, got: " + sink2);
    }

    @Test
    void skipGapSkipsDowntimeDataAndNewDataStillFlows() throws Exception {
        List<String> data = Collections.synchronizedList(new ArrayList<>(Arrays.asList("a", "b", "c")));
        List<String> sink1 = new ArrayList<>();
        suspendOnce(data, sink1, SuspendDataPolicy.REPLAY_GAP);
        assertEquals(Arrays.asList("a", "b", "c"), sink1, "round 1 consumes the initial list");

        data.add("d");
        data.add("e");

        List<String> sink2 = new ArrayList<>();
        suspendOnce(data, sink2, SuspendDataPolicy.SKIP_GAP);
        assertTrue(sink2.isEmpty(),
                "SKIP_GAP must not re-emit the downtime data (d,e) nor replay old data, got: " + sink2);

        // data arriving AFTER the recovery is consumed normally
        data.add("f");
        List<String> sink3 = new ArrayList<>();
        suspendOnce(data, sink3, SuspendDataPolicy.REPLAY_GAP);
        assertTrue(sink3.contains("f"),
                "new data must still flow after a SKIP_GAP recovery, got: " + sink3);
    }

    /**
     * SKIP_GAP on a replayable source WITHOUT a deterministic latest position
     * is rejected typed (fail-fast, no guessed skip). The rejection happens in
     * the WI9 restore hook, i.e. before any task starts.
     */
    @Test
    void skipGapRejectsNonCollectionReplayableSource() {
        CheckpointConfig config = baseConfig(SuspendDataPolicy.SKIP_GAP);
        JobGraph jobGraph = buildJobGraph("skip-gap-unsupported",
                new OffsetListSource(Arrays.asList("a", "b")), new ArrayList<>());

        StreamException ex = assertThrows(StreamException.class,
                () -> GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "skip-gap-unsupported", config));
        assertEquals(ERR_STREAM_SKIP_GAP_UNSUPPORTED_SOURCE.getErrorCode(), ex.getErrorCode(),
                "rejection must use the dedicated SKIP_GAP error code");
        assertTrue(String.valueOf(ex).contains(OffsetListSource.class.getName()),
                "error must name the offending source type: " + ex);
    }

    /**
     * The policy rides the CheckpointConfig construction surface (builder +
     * setter). It is deliberately NOT persisted in the manifest, so the config
     * is its only carrier.
     */
    @Test
    void suspendDataPolicyRoundTripsThroughConfigAndBuilder() {
        CheckpointConfig config = new CheckpointConfig();
        assertEquals(SuspendDataPolicy.REPLAY_GAP, config.getSuspendDataPolicy(),
                "default must be REPLAY_GAP (zero-change contract)");

        config.setSuspendDataPolicy(SuspendDataPolicy.SKIP_GAP);
        assertEquals(SuspendDataPolicy.SKIP_GAP, config.getSuspendDataPolicy());

        config.setSuspendDataPolicy(null);
        assertEquals(SuspendDataPolicy.REPLAY_GAP, config.getSuspendDataPolicy(),
                "null must normalize to REPLAY_GAP");

        CheckpointConfig built = CheckpointConfig.builder()
                .jobId(JOB_ID)
                .suspendDataPolicy(SuspendDataPolicy.SKIP_GAP)
                .build();
        assertEquals(SuspendDataPolicy.SKIP_GAP, built.getSuspendDataPolicy(), "builder passthrough");

        CheckpointConfig defaultBuilt = CheckpointConfig.builder().build();
        assertEquals(SuspendDataPolicy.REPLAY_GAP, defaultBuilt.getSuspendDataPolicy(),
                "builder default must be REPLAY_GAP");
    }

    // ==================== harness ====================

    /**
     * One SUSPEND execution over the shared list. The collection source keeps a
     * reference to {@code data}, so appends made by the test between calls are
     * visible to the next round's source (the downtime gap).
     */
    private void suspendOnce(List<String> data, List<String> sink, SuspendDataPolicy policy) throws Exception {
        JobGraph jobGraph = buildJobGraph("suspend-data-policy",
                new CollectionReplayableSource<>(data), sink);
        CheckpointConfig config = baseConfig(policy);
        GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "suspend-data-policy", config);
    }

    private CheckpointConfig baseConfig(SuspendDataPolicy policy) {
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId(PIPELINE_ID);
        config.setCheckpointEnabled(true);
        // a huge interval: no periodic checkpoint lands mid-run; only the SUSPEND
        // terminal savepoint is written (deterministic per run)
        config.setCheckpointInterval(3600_000L);
        config.setCheckpointTimeout(30_000L);
        config.setStorageType("local");
        config.setStorageProperty("path", tempDir.toString());
        config.setJobTerminationMode(JobTerminationMode.SUSPEND);
        if (policy != null) {
            config.setSuspendDataPolicy(policy);
        }
        return config;
    }

    private static JobGraph buildJobGraph(String name, SourceFunction<String> source, List<String> sink) {
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sink::add);

        OperatorChain chain = new OperatorChain(Arrays.asList(sourceOp, sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex("v1", "Chain", 1, Collections.singletonList(chain), invokable);

        JobGraph jobGraph = new JobGraph(name);
        jobGraph.addVertex(vertex);
        return jobGraph;
    }

    /**
     * A replayable source with an offset but NO deterministic latest position
     * (the shape of a batch-loader / cursor source): SKIP_GAP must reject it
     * typed instead of guessing what to skip.
     */
    static final class OffsetListSource implements ReplayableSourceFunction<String> {
        private static final long serialVersionUID = 1L;

        private final List<String> data;
        private long offset;

        OffsetListSource(List<String> data) {
            this.data = data;
        }

        @Override
        public void seek(long offset) {
            this.offset = offset;
        }

        @Override
        public long getCurrentOffset() {
            return offset;
        }

        @Override
        public void run(SourceContext<String> ctx) {
            while (offset < data.size()) {
                ctx.collect(data.get((int) offset++));
            }
        }

        @Override
        public void cancel() {
        }
    }
}
