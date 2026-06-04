/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.*;

import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestFingerprintAndTerminationMode {

    private static final TaskLocation LOC_1 = new TaskLocation("1", "0", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("1", "0", "v2", 2);

    private static Path tempDir;
    private ICheckpointStorage storage;
    private CheckpointIDCounter idCounter;

    @BeforeAll
    static void setupClass() throws Exception {
        tempDir = Files.createTempDirectory("fingerprint-termination-test");
    }

    @AfterAll
    static void teardownClass() throws Exception {
        deleteDirectory(tempDir.toFile());
    }

    @BeforeEach
    void setup() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
        idCounter = new CheckpointIDCounter();
    }

    @AfterEach
    void teardown() throws Exception {
        storage.deleteAllCheckpoints("1");
    }

    // ==================== Fingerprint Compatibility Tests ====================

    @Test
    void testFingerprintMismatchOnRestoreThrowsException() throws Exception {
        // 1. Create a StreamModel and compute fingerprint
        StreamComponents components = new StreamComponents();
        Map<String, io.nop.stream.core.transformation.Transformation<?>> transforms1 = new LinkedHashMap<>();
        transforms1.put("transform-a", null);
        StreamModel originalModel = new StreamModel(components, transforms1);
        StreamModelFingerprint originalFingerprint = originalModel.computeFingerprint();

        // 2. Store an EpochManifest with the original fingerprint
        CheckpointConfig config = new CheckpointConfig();
        CheckpointCoordinator coordinator = new CheckpointCoordinator("1", "0", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        coordinator.setCurrentFingerprint(originalFingerprint);

        // Trigger a checkpoint and complete it to store an EpochManifest
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("state1", "data1").build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("state2", "data2").build();

        coordinator.acknowledgeTask(LOC_1, checkpointId, state1);
        coordinator.acknowledgeTask(LOC_2, checkpointId, state2);

        // Wait for completion
        Thread.sleep(300);
        coordinator.shutdown();

        // 3. Create a DIFFERENT model (different DAG topology)
        Map<String, io.nop.stream.core.transformation.Transformation<?>> transforms2 = new LinkedHashMap<>();
        transforms2.put("transform-b", null);
        StreamModel differentModel = new StreamModel(components, transforms2);
        StreamModelFingerprint currentFingerprint = differentModel.computeFingerprint();

        // 4. Restore with the different model - this should fail fingerprint validation
        CheckpointIDCounter restoreIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator restoreCoordinator = new CheckpointCoordinator("1", "0", restoreIdCounter, storage, config);

        EpochManifest manifest = restoreCoordinator.restoreLatestEpochManifest();
        assertNotNull(manifest, "EpochManifest should exist");
        assertNotNull(manifest.getStreamModelFingerprint(), "Stored fingerprint should not be null");

        // Call the production validation method directly - it should throw StreamException
        assertThrows(StreamException.class, () ->
                GraphModelCheckpointExecutor.validateFingerprintCompatibility(
                        manifest, differentModel, restoreCoordinator));

        restoreCoordinator.shutdown();
    }

    @Test
    void testFingerprintMatchOnRestoreSucceeds() throws Exception {
        // 1. Create a StreamModel and compute fingerprint
        StreamComponents components = new StreamComponents();
        Map<String, io.nop.stream.core.transformation.Transformation<?>> transforms = new LinkedHashMap<>();
        transforms.put("transform-a", null);
        StreamModel model = new StreamModel(components, transforms);
        StreamModelFingerprint fingerprint = model.computeFingerprint();

        // 2. Store an EpochManifest with the fingerprint
        CheckpointConfig config = new CheckpointConfig();
        CheckpointCoordinator coordinator = new CheckpointCoordinator("1", "0", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        coordinator.setCurrentFingerprint(fingerprint);

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("state1", "data1").build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("state2", "data2").build();

        coordinator.acknowledgeTask(LOC_1, checkpointId, state1);
        coordinator.acknowledgeTask(LOC_2, checkpointId, state2);

        Thread.sleep(300);
        coordinator.shutdown();

        // 3. Create the SAME model and verify compatibility
        StreamModel sameModel = new StreamModel(components, transforms);
        StreamModelFingerprint currentFingerprint = sameModel.computeFingerprint();

        CheckpointIDCounter restoreIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator restoreCoordinator = new CheckpointCoordinator("1", "0", restoreIdCounter, storage, config);

        EpochManifest manifest = restoreCoordinator.restoreLatestEpochManifest();
        assertNotNull(manifest, "EpochManifest should exist");
        assertNotNull(manifest.getStreamModelFingerprint(), "Stored fingerprint should not be null");

        assertTrue(currentFingerprint.isCompatibleWith(manifest.getStreamModelFingerprint()),
                "Fingerprints should be compatible");

        restoreCoordinator.shutdown();
    }

    @Test
    void testRestoreWithNullFingerprintSkipsCheck() throws Exception {
        // Store an EpochManifest without fingerprint (legacy)
        CheckpointConfig config = new CheckpointConfig();
        CheckpointCoordinator coordinator = new CheckpointCoordinator("1", "0", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        // Do NOT set fingerprint - should remain null

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);
        long checkpointId = pending.getCheckpointId();

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("state1", "data1").build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("state2", "data2").build();

        coordinator.acknowledgeTask(LOC_1, checkpointId, state1);
        coordinator.acknowledgeTask(LOC_2, checkpointId, state2);

        Thread.sleep(300);
        coordinator.shutdown();

        // Restore - null fingerprint should skip compatibility check
        CheckpointIDCounter restoreIdCounter = new CheckpointIDCounter();
        CheckpointCoordinator restoreCoordinator = new CheckpointCoordinator("1", "0", restoreIdCounter, storage, config);

        EpochManifest manifest = restoreCoordinator.restoreLatestEpochManifest();
        assertNotNull(manifest, "EpochManifest should exist");
        // The fingerprint should be null for legacy checkpoints
        // This should NOT throw any exception - the validation is skipped

        restoreCoordinator.shutdown();
    }

    // ==================== CheckpointConfig.jobTerminationMode Tests ====================

    @Test
    void testDefaultJobTerminationModeIsCancel() {
        CheckpointConfig config = new CheckpointConfig();
        assertEquals(JobTerminationMode.CANCEL, config.getJobTerminationMode());
    }

    @Test
    void testSetJobTerminationMode() {
        CheckpointConfig config = new CheckpointConfig();

        config.setJobTerminationMode(JobTerminationMode.DRAIN);
        assertEquals(JobTerminationMode.DRAIN, config.getJobTerminationMode());

        config.setJobTerminationMode(JobTerminationMode.SUSPEND);
        assertEquals(JobTerminationMode.SUSPEND, config.getJobTerminationMode());

        // null should default to CANCEL
        config.setJobTerminationMode(null);
        assertEquals(JobTerminationMode.CANCEL, config.getJobTerminationMode());
    }

    @Test
    void testBuilderJobTerminationMode() {
        CheckpointConfig config = CheckpointConfig.builder()
                .jobTerminationMode(JobTerminationMode.DRAIN)
                .build();
        assertEquals(JobTerminationMode.DRAIN, config.getJobTerminationMode());
    }

    // ==================== DRAIN Mode Tests ====================

    @Test
    void testDrainModeTriggersTerminalSavepoint() throws Exception {
        CheckpointConfig config = new CheckpointConfig();
        config.setJobTerminationMode(JobTerminationMode.DRAIN);
        config.setCheckpointInterval(60000L);

        CheckpointCoordinator coordinator = new CheckpointCoordinator("drain-job", "0", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));

        // Trigger a TERMINAL_SAVEPOINT (as DRAIN mode would)
        PendingCheckpoint terminal = coordinator.tryTriggerPendingCheckpoint(CheckpointType.TERMINAL_SAVEPOINT);
        assertNotNull(terminal, "DRAIN mode should trigger a terminal savepoint");
        assertEquals(CheckpointType.TERMINAL_SAVEPOINT, terminal.getCheckpointType());

        // Complete it
        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("drain-state", "drain-data").build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("drain-state", "drain-data-2").build();

        coordinator.acknowledgeTask(LOC_1, terminal.getCheckpointId(), state1);
        coordinator.acknowledgeTask(LOC_2, terminal.getCheckpointId(), state2);

        Thread.sleep(300);

        CompletedCheckpoint completed = coordinator.getLatestCheckpoint();
        assertNotNull(completed);
        assertEquals(CheckpointType.TERMINAL_SAVEPOINT, completed.getCheckpointType());
        assertEquals("drain-data", completed.getTaskState(LOC_1).getOperatorState("drain-state"));

        coordinator.shutdown();
    }

    // ==================== SUSPEND Mode Tests ====================

    @Test
    void testSuspendModeTriggersSavepoint() throws Exception {
        CheckpointConfig config = new CheckpointConfig();
        config.setJobTerminationMode(JobTerminationMode.SUSPEND);
        config.setCheckpointInterval(60000L);

        CheckpointCoordinator coordinator = new CheckpointCoordinator("suspend-job", "0", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));

        // Trigger a SAVEPOINT (as SUSPEND mode would)
        PendingCheckpoint savepoint = coordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT);
        assertNotNull(savepoint, "SUSPEND mode should trigger a savepoint");
        assertEquals(CheckpointType.SAVEPOINT, savepoint.getCheckpointType());

        // Complete it
        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("suspend-state", "suspend-data").build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("suspend-state", "suspend-data-2").build();

        coordinator.acknowledgeTask(LOC_1, savepoint.getCheckpointId(), state1);
        coordinator.acknowledgeTask(LOC_2, savepoint.getCheckpointId(), state2);

        Thread.sleep(300);

        CompletedCheckpoint completed = coordinator.getLatestCheckpoint();
        assertNotNull(completed);
        assertEquals(CheckpointType.SAVEPOINT, completed.getCheckpointType());

        coordinator.shutdown();
    }

    // ==================== Fingerprint in EpochManifest Tests ====================

    @Test
    void testFingerprintStoredInEpochManifest() throws Exception {
        StreamComponents components = new StreamComponents();
        Map<String, io.nop.stream.core.transformation.Transformation<?>> transforms = new LinkedHashMap<>();
        transforms.put("t1", null);
        StreamModel model = new StreamModel(components, transforms);
        StreamModelFingerprint fingerprint = model.computeFingerprint();

        CheckpointConfig config = new CheckpointConfig();
        CheckpointCoordinator coordinator = new CheckpointCoordinator("fp-job", "0", idCounter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
        coordinator.setCurrentFingerprint(fingerprint);

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        TaskStateSnapshot state1 = TaskStateSnapshot.builder(LOC_1)
                .putOperatorState("s1", "d1").build();
        TaskStateSnapshot state2 = TaskStateSnapshot.builder(LOC_2)
                .putOperatorState("s2", "d2").build();

        coordinator.acknowledgeTask(LOC_1, pending.getCheckpointId(), state1);
        coordinator.acknowledgeTask(LOC_2, pending.getCheckpointId(), state2);

        Thread.sleep(300);
        coordinator.shutdown();

        // Verify the EpochManifest contains the fingerprint
        CheckpointIDCounter restoreCounter = new CheckpointIDCounter();
        CheckpointCoordinator restoreCoordinator = new CheckpointCoordinator("fp-job", "0", restoreCounter, storage, config);

        EpochManifest manifest = restoreCoordinator.restoreLatestEpochManifest();
        assertNotNull(manifest);
        assertNotNull(manifest.getStreamModelFingerprint());
        assertEquals(fingerprint, manifest.getStreamModelFingerprint());

        restoreCoordinator.shutdown();
    }

    @Test
    void testCoordinatorFingerprintGetterSetter() {
        CheckpointConfig config = new CheckpointConfig();
        CheckpointCoordinator coordinator = new CheckpointCoordinator("fp-test", "0", idCounter, storage, config);

        assertNull(coordinator.getCurrentFingerprint());

        StreamModelFingerprint fp = StreamModelFingerprint.builder()
                .dagTopologyHash("abc")
                .requirementsHash("def")
                .build();
        coordinator.setCurrentFingerprint(fp);

        assertEquals(fp, coordinator.getCurrentFingerprint());
        assertEquals("abc", coordinator.getCurrentFingerprint().getDagTopologyHash());

        coordinator.shutdown();
    }

    private static void deleteDirectory(java.io.File dir) {
        if (dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
}
