/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.EpochState;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-5: validates the already-landed DAG-level {@link StreamModelFingerprint}
 * recovery compatibility contract owned by
 * {@link GraphModelCheckpointExecutor#validateFingerprintCompatibility}.
 *
 * <p>The plan calls for a focused, anti-hollow test file that exercises the
 * exact production validator: same fingerprint succeeds, different fingerprint
 * throws. Per-state schema fingerprint migration ({@code StateMigrationFunction},
 * Stage 33) has landed in the state backend's {@code verifySchemaCompatibility}
 * path and is covered by {@code TestStateMigration} / {@code TestRocksDBStateMigration};
 * those per-state migration scenarios are NOT duplicated here (this test stays
 * focused on the DAG-level fingerprint validator).
 */
public class TestStreamModelFingerprintRecoveryCompat {

    private static final TaskLocation LOC_1 = new TaskLocation("fp-compat-job", "0", "v1", 0);
    private static final TaskLocation LOC_2 = new TaskLocation("fp-compat-job", "0", "v2", 0);

    private static Path tempDir;
    private ICheckpointStorage storage;
    private CheckpointIDCounter idCounter;

    @BeforeAll
    static void setupClass() throws Exception {
        tempDir = Files.createTempDirectory("fingerprint-recovery-compat");
    }

    @AfterAll
    static void teardownClass() {
        deleteRecursively(tempDir.toFile());
    }

    @BeforeEach
    void setup() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        idCounter = new CheckpointIDCounter();
    }

    @AfterEach
    void teardown() throws Exception {
        storage.deleteAllCheckpoints("fp-compat-job");
    }

    @Test
    void sameFingerprintRecoverySucceeds() throws Exception {
        StreamComponents components = new StreamComponents();
        Map<String, io.nop.stream.core.transformation.Transformation<?>> transforms = new LinkedHashMap<>();
        transforms.put("transform-shared", null);
        StreamModel originalModel = new StreamModel(components, transforms);
        StreamModelFingerprint storedFingerprint = originalModel.computeFingerprint();

        storeEpochWithFingerprint(storedFingerprint);

        StreamModel restoreModel = new StreamModel(components, transforms);
        EpochManifest manifest = loadSingleEpoch();

        // Anti-hollow: invoke the real production validator. If the validator
        // is removed or weakened to a no-op, this test still passes — the
        // stronger contract is asserted by the different-fingerprint test below.
        assertDoesNotThrow(() ->
                GraphModelCheckpointExecutor.validateFingerprintCompatibility(manifest, restoreModel, null),
                "Recovery with a matching fingerprint must succeed");
    }

    @Test
    void differentFingerprintRecoveryThrows() throws Exception {
        StreamComponents components = new StreamComponents();
        Map<String, io.nop.stream.core.transformation.Transformation<?>> transformsA = new LinkedHashMap<>();
        transformsA.put("transform-a", null);
        StreamModel originalModel = new StreamModel(components, transformsA);
        StreamModelFingerprint storedFingerprint = originalModel.computeFingerprint();

        storeEpochWithFingerprint(storedFingerprint);

        // Build a genuinely different model (different DAG topology hash).
        Map<String, io.nop.stream.core.transformation.Transformation<?>> transformsB = new LinkedHashMap<>();
        transformsB.put("transform-b", null);
        StreamModel differentModel = new StreamModel(components, transformsB);
        assertFalse(differentModel.computeFingerprint().isCompatibleWith(storedFingerprint),
                "test setup error: the two fingerprints must be incompatible");

        EpochManifest manifest = loadSingleEpoch();

        // Anti-hollow: this is the core rejection assertion. If the validator's
        // throw is removed (e.g. turned into a LOG.warn + return), this test
        // fails because no exception is propagated.
        StreamException thrown = assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.validateFingerprintCompatibility(manifest, differentModel, null));
        assertNotNull(thrown.getMessage());
    }

    /**
     * R-7 (runtime audit 2026-09-01): a manifest that carries a fingerprint but
     * an execution path that provides no fingerprint source must fail fast
     * instead of silently skipping the compatibility check. The JobGraph-only
     * {@code executeWithCheckpoint} entry is a production path
     * (ICheckpointExecutorFactory) that never calls
     * {@code setCurrentFingerprint}; the old warn-skip let an incompatible
     * restore through on exactly that path.
     */
    @Test
    void fingerprintedManifestWithoutCurrentSourceFailsFast() throws Exception {
        StreamComponents components = new StreamComponents();
        Map<String, io.nop.stream.core.transformation.Transformation<?>> transforms = new LinkedHashMap<>();
        transforms.put("transform-source", null);
        StreamModelFingerprint storedFingerprint = new StreamModel(components, transforms).computeFingerprint();
        storeEpochWithFingerprint(storedFingerprint);
        EpochManifest manifest = loadSingleEpoch();

        CheckpointCoordinator noFingerprintCoordinator = new CheckpointCoordinator(
                "fp-compat-job", "0", new CheckpointIDCounter(), storage, new CheckpointConfig());
        try {
            assertNull(noFingerprintCoordinator.getCurrentFingerprint(),
                    "test setup: the JobGraph-entry coordinator carries no fingerprint");

            StreamException ex = assertThrows(StreamException.class,
                    () -> GraphModelCheckpointExecutor.validateFingerprintCompatibility(
                            manifest, null, noFingerprintCoordinator),
                    "no fingerprint source + fingerprinted manifest must fail fast (was a silent warn-skip)");
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("fingerprint"),
                    "error should point at the fingerprint compatibility requirement: " + ex.getMessage());
        } finally {
            noFingerprintCoordinator.shutdown();
        }
    }

    /**
     * R-7 companion: legacy manifests written WITHOUT a fingerprint (e.g. by
     * the JobGraph-only entry itself) stay restorable from a fingerprint-less
     * path — the fail-fast only applies when the manifest demands a check.
     */
    @Test
    void fingerprintlessManifestWithoutSourceStillPasses() {
        EpochManifest legacyManifest = new EpochManifest(
                1L, "fp-compat-job", "0", System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                new LinkedHashMap<>(), null, null);

        CheckpointCoordinator noFingerprintCoordinator = new CheckpointCoordinator(
                "fp-compat-job", "0", new CheckpointIDCounter(), storage, new CheckpointConfig());
        try {
            assertNull(legacyManifest.getStreamModelFingerprint(),
                    "test setup: legacy manifest carries no fingerprint");
            assertDoesNotThrow(() -> GraphModelCheckpointExecutor.validateFingerprintCompatibility(
                    legacyManifest, null, noFingerprintCoordinator),
                    "fingerprint-less manifests must remain restorable without a fingerprint source");
        } finally {
            noFingerprintCoordinator.shutdown();
        }
    }

    private void storeEpochWithFingerprint(StreamModelFingerprint fingerprint) throws Exception {
        CheckpointConfig config = new CheckpointConfig();
        CheckpointCoordinator coordinator = new CheckpointCoordinator("fp-compat-job", "0", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        coordinator.setCurrentFingerprint(fingerprint);

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(),
                io.nop.stream.core.checkpoint.TaskStateSnapshot.builder(LOC_1).putOperatorState("s", "v1").build());
        coordinator.acknowledgeTask(LOC_2, pending.getCheckpointId(),
                io.nop.stream.core.checkpoint.TaskStateSnapshot.builder(LOC_2).putOperatorState("s", "v2").build());

        Thread.sleep(300);
        coordinator.shutdown();
    }

    private EpochManifest loadSingleEpoch() throws Exception {
        CheckpointIDCounter restoreCounter = new CheckpointIDCounter();
        CheckpointCoordinator restoreCoordinator = new CheckpointCoordinator("fp-compat-job", "0", restoreCounter, storage, new CheckpointConfig());
        EpochManifest manifest = restoreCoordinator.restoreLatestEpochManifest();
        restoreCoordinator.shutdown();
        assertNotNull(manifest, "an EpochManifest must be present after setup");
        assertNotNull(manifest.getStreamModelFingerprint(), "the stored fingerprint must not be null");
        return manifest;
    }

    private static void deleteRecursively(java.io.File f) {
        if (f.isDirectory()) {
            java.io.File[] children = f.listFiles();
            if (children != null) {
                for (java.io.File c : children) {
                    deleteRecursively(c);
                }
            }
        }
        f.delete();
    }
}
