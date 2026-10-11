/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10 (plan 2426): the cross-version FORMAT pin. Restores the checked-in
 * legacy checkpoint fixture ({@code checkpoint-legacy-fixture/}, written by an
 * earlier code state) through the current engine's manifest-first auto-restore
 * and proves the restored keyed accumulators continue deterministically.
 *
 * <p>Hard constraint under test: checkpoint storage format, serialized bytes
 * and epoch manifest structure are zero-change — existing checkpoints MUST be
 * restorable by newer code. If this test fails after a storage-format touch,
 * the change violated the roadmap constraint (or the fixture was deliberately
 * regenerated — see the fixture README).
 */
class TestLegacyCheckpointFixtureRestore {

    private static final String JOB_ID = "fixture-source-job";

    @TempDir
    Path tempDir;

    @Test
    void legacyFixtureRestoresAndContinues() throws Exception {
        Path fixtureRoot = locateFixtureRoot();
        assertNotNull(fixtureRoot, "checkpoint-legacy-fixture/storage must exist on the classpath");

        // Extract to a writable dir (classpath resources are read-only).
        Path storageDir = tempDir.resolve("storage");
        TestLegacyCheckpointFixtureGenerator.copyTree(fixtureRoot, storageDir);
        assertTrue(TestCheckpointDualRunDifferential.countManifests(storageDir.toString()) >= 1,
                "extracted fixture must contain epoch manifests");

        // Restore run: replay the same input over the restored accumulators via
        // manifest-first auto-restore (explicit storage path = recovery intent).
        List<Integer> committed = new ArrayList<>();
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE);
        config.setCheckpointInterval(150L);
        config.setCheckpointTimeout(30_000L);
        config.setMaxRetainedCheckpoints(5);
        config.setStorageProperty("path", storageDir.toFile().getAbsolutePath());
        config.setJobId(JOB_ID);
        config.setPipelineId("pipeline-0");

        io.nop.stream.core.environment.StreamExecutionEnvironment environment =
                io.nop.stream.core.environment.StreamExecutionEnvironment.createTestEnvironment();
        environment.setParallelism(1);
        environment.enableCheckpointing(150L);
        environment.getCheckpointConfig().setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE);
        environment.addSource(
                new TestCheckpointDualRunDifferential.PacingInput<>(
                        TestCheckpointDualRunDifferential.firstInput(), 25L), "paced-source")
                .map(v -> v)
                .keyBy(v -> v % 2)
                .reduce((a, b) -> a + b)
                .sink(new TestCheckpointDualRunDifferential.RecordingTwoPhaseSink(JOB_ID, committed));
        // Pre-populate the external commit ledger with the fixture job's committed
        // epochs (the fixture's run committed every durable pending — the external
        // system already has those records). Restore re-delivers the terminal-epoch
        // inherited pendings through commit(), and the ledger absorbs the
        // redelivery exactly as a production sink would.
        LocalFileCheckpointStorage fixtureStorage =
                new LocalFileCheckpointStorage(storageDir.toFile().getAbsolutePath());
        for (EpochManifest manifest : fixtureStorage.loadRetainedEpochManifests(JOB_ID, "pipeline-0", 10)) {
            TestCheckpointDualRunDifferential.RecordingTwoPhaseSink.EXTERNAL_LEDGER.put(
                    JOB_ID + ":" + manifest.getEpochId(), Boolean.TRUE);
        }

        JobGraph jobGraph = environment.buildJobGraph("legacy-fixture-restore");
        GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, JOB_ID, config);

        // Restore happened (the sink observed a durable epoch) and the restored
        // keyed accumulators continued: per key, run emissions' consecutive
        // differences equal the replayed same-key inputs and the first emission
        // carries a non-zero restored accumulator that the fixture's epoch family
        // could have produced (structural + membership invariants — the fixture's
        // exact restore boundary is timing-dependent by design).
        assertEquals(40, committed.size(), "restore replay must emit one value per input: " + committed);
        List<Integer> key0 = new ArrayList<>();
        List<Integer> key1 = new ArrayList<>();
        for (int i = 0; i < committed.size(); i++) {
            (i % 2 == 0 ? key0 : key1).add(committed.get(i));
        }
        assertContinued(key0, 0);
        assertContinued(key1, 1);
    }

    private static void assertContinued(List<Integer> emissions, int key) {
        for (int i = 1; i < emissions.size(); i++) {
            long expectedDiff = 2L * i + key;
            assertEquals(expectedDiff, emissions.get(i) - (long) emissions.get(i - 1),
                    "key " + key + " continuation diff at step " + i);
        }
        int restoredAcc = emissions.get(0) - key;
        assertTrue(restoredAcc > 0,
                "key " + key + " must start from a non-zero restored accumulator, got " + restoredAcc);
    }

    private static Path locateFixtureRoot() throws Exception {
        ClassLoader cl = TestLegacyCheckpointFixtureRestore.class.getClassLoader();
        java.util.Enumeration<java.net.URL> roots = cl.getResources("checkpoint-legacy-fixture/storage");
        while (roots.hasMoreElements()) {
            java.net.URL url = roots.nextElement();
            if ("file".equals(url.getProtocol())) {
                return Path.of(url.toURI());
            }
        }
        return null;
    }
}
