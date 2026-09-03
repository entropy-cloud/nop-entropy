package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.EpochState;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-03: proves the checkpoint ID counter is advanced monotonically past the
 * restored EpochManifest epoch, so new checkpoints after a manifest restore
 * produce strictly greater epoch ids (never re-entering the shadow window
 * [0, R) below the restored epoch).
 *
 * <p>E2E proof (Rule #22): the job is executed three times against the SAME
 * checkpoint storage. Each run must produce a latest manifest whose epoch id
 * is strictly greater than the previous run's. Before the fix, every run
 * restarted the counter at 0, so run 2/3's manifests were <= run 1's (shadow
 * window re-entry) — the max-id manifest selection kept returning the stale
 * run-1 manifest.
 */
class TestE2EManifestRestoreIdAdvance {

    @TempDir
    Path tempDir;

    private static final String JOB_ID = "manifest-id-advance";
    private static final String PIPELINE_ID = "1";

    /**
     * Deterministic slow source: same element count + pacing in every run, so
     * each run completes the same number of periodic checkpoints plus the
     * final termination checkpoint. The source must outlive at least one
     * checkpoint interval so multiple manifests are stored per run.
     */
    private static SourceFunction<Integer> slowSource() {
        return new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) {
                for (int i = 1; i <= 10; i++) {
                    ctx.collect(i);
                    try {
                        Thread.sleep(60);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            @Override
            public void cancel() {
            }
        };
    }

    private JobGraph buildJobGraph() {
        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(slowSource());
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
            }
        });

        OperatorChain chain = new OperatorChain(Arrays.asList(sourceOp, sinkOp));
        io.nop.stream.core.execution.StreamTaskInvokable invokable =
                new io.nop.stream.core.execution.StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex("v1", "Chain", 1, Collections.singletonList(chain), invokable);

        JobGraph jobGraph = new JobGraph("manifest-id-advance-graph");
        jobGraph.addVertex(vertex);
        return jobGraph;
    }

    private CheckpointConfig buildConfig() {
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId(PIPELINE_ID);
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(100L);
        config.setCheckpointTimeout(5000L);
        config.setMinPause(150L);
        config.setStorageProperty("path", tempDir.toString());
        return config;
    }

    private long runOnceAndGetLatestManifestEpoch() throws Exception {
        GraphModelCheckpointExecutor.executeWithCheckpoint(buildJobGraph(), "manifest-id-advance", buildConfig());
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        EpochManifest manifest = storage.loadLatestEpochManifest(JOB_ID, PIPELINE_ID);
        assertNotNull(manifest, "Each run must store at least one EpochManifest (final checkpoint)");
        return manifest.getEpochId();
    }

    /**
     * E2E: three consecutive runs against the same storage — each run's latest
     * manifest epoch must be strictly greater than the previous run's. Red
     * before the P0-03 fix (counter restart → run 2/3 stay <= run 1), green
     * after (monotonic advance past the restored epoch).
     */
    @Test
    void testManifestRestoreAdvancesCheckpointIdAcrossRepeatedRuns() throws Exception {
        long run1 = runOnceAndGetLatestManifestEpoch();
        long run2 = runOnceAndGetLatestManifestEpoch();
        long run3 = runOnceAndGetLatestManifestEpoch();

        assertTrue(run2 > run1,
                "Run 2 must produce a strictly greater manifest epoch than run 1 "
                        + "(shadow-window re-entry detected: run1=" + run1 + ", run2=" + run2 + ")");
        assertTrue(run3 > run2,
                "Run 3 must produce a strictly greater manifest epoch than run 2 "
                        + "(shadow-window re-entry detected: run2=" + run2 + ", run3=" + run3 + ")");
    }

    /**
     * Unit-level proof of the coordinator contract: the counter advance is
     * monotonic (max(restoredId+1, current)) and the next triggered checkpoint
     * id is strictly greater than the restored epoch id.
     */
    @Test
    void testAdvanceCheckpointIdCounterMonotonicSemantics() {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());

        // --- manifest restore path: fresh counter + stored manifest at epoch 7
        CheckpointIDCounter counter = new CheckpointIDCounter();
        CheckpointCoordinator coordinator = new CheckpointCoordinator(
                "unit-manifest", "1", counter, storage, new CheckpointConfig());
        coordinator.registerTask(new TaskLocation("unit-manifest", "1", "v1", 0));

        EpochManifest manifest = new EpochManifest(
                7L, "unit-manifest", "1", System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                Collections.emptyMap(), null, Collections.emptyList(), Collections.emptyMap());
        try {
            storage.storeEpochManifest("unit-manifest", "1", manifest);
        } catch (Exception e) {
            throw new StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR, e);
        }

        coordinator.advanceCheckpointIdCounterAfterRestore(manifest.getEpochId());
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        assertTrue(pending.getCheckpointId() > manifest.getEpochId(),
                "Next checkpoint id must be > restored epoch: " + pending.getCheckpointId());
        coordinator.shutdown();

        // --- max semantics: counter already beyond restoredId is left untouched
        CheckpointIDCounter advancedCounter = new CheckpointIDCounter(10L);
        CheckpointCoordinator advancedCoordinator = new CheckpointCoordinator(
                "unit-advanced", "1", advancedCounter, storage, new CheckpointConfig());
        advancedCoordinator.registerTask(new TaskLocation("unit-advanced", "1", "v1", 0));
        advancedCoordinator.advanceCheckpointIdCounterAfterRestore(5L);
        PendingCheckpoint afterLower = advancedCoordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(afterLower);
        assertEquals(10L, afterLower.getCheckpointId(),
                "Advance with restoredId < current counter must not move the counter");
        advancedCoordinator.shutdown();

        // --- max semantics: restoredId beyond the current counter moves it to restoredId+1
        CheckpointIDCounter advancedCounter2 = new CheckpointIDCounter(10L);
        CheckpointCoordinator advancedCoordinator2 = new CheckpointCoordinator(
                "unit-advanced-2", "1", advancedCounter2, storage, new CheckpointConfig());
        advancedCoordinator2.registerTask(new TaskLocation("unit-advanced-2", "1", "v1", 0));
        advancedCoordinator2.advanceCheckpointIdCounterAfterRestore(15L);
        PendingCheckpoint afterHigher = advancedCoordinator2.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(afterHigher);
        assertEquals(16L, afterHigher.getCheckpointId(),
                "Advance with restoredId >= current counter must move counter to restoredId+1");
        advancedCoordinator2.shutdown();
    }
}
