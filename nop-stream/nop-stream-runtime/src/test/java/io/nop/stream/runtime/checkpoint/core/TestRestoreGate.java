/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.core;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.EpochState;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI11 (plan 2432): the shared manifest-first restore gate
 * {@link RestoreGates#applyManifestGate} — four scenarios mirroring the
 * JobCoordinator failover gate semantics. Both entry points run this helper:
 * the JobCoordinator LEADERSHIP_GRANT rebuild and the JobCoordinatorMain
 * launch path (where a gate failure throws = the launch fails loud, never a
 * silent fresh start).
 */
class TestRestoreGate {

    private static final String JOB_ID = "restore-gate-job";
    private static final String PIPELINE_ID = "pipeline-0";

    @TempDir
    Path tempDir;

    @Test
    void missingManifestSkipsGate() throws Exception {
        CheckpointIDCounter counter = new CheckpointIDCounter();
        CheckpointCoordinator coord = freshCoordinator(counter);
        // no manifest in storage — gate is a no-op
        RestoreGates.applyManifestGate(coord, JOB_ID);
        // counter untouched (raw path advances it later from the data row)
        assertEquals(0L, counter.get(), "gate without a manifest must not advance the counter");
    }

    @Test
    void compatibleFingerprintPassesAndAdvancesCounter() throws Exception {
        LocalFileCheckpointStorage storage = storage();
        storage.storeEpochManifest(JOB_ID, PIPELINE_ID, manifest(7L, fingerprint("dag-1")));
        CheckpointIDCounter counter = new CheckpointIDCounter();
        CheckpointCoordinator coord = freshCoordinator(storage, counter);
        coord.setCurrentFingerprint(fingerprint("dag-1"));

        RestoreGates.applyManifestGate(coord, JOB_ID);

        assertEquals(8L, counter.get(),
                "counter must advance past the MANIFEST epoch (shadow window closed)");
    }

    @Test
    void mismatchedFingerprintFailsClosed() throws Exception {
        LocalFileCheckpointStorage storage = storage();
        storage.storeEpochManifest(JOB_ID, PIPELINE_ID, manifest(7L, fingerprint("dag-old")));
        CheckpointIDCounter counter = new CheckpointIDCounter();
        CheckpointCoordinator coord = freshCoordinator(storage, counter);
        coord.setCurrentFingerprint(fingerprint("dag-new"));

        StreamException ex = assertThrows(StreamException.class,
                () -> RestoreGates.applyManifestGate(coord, JOB_ID),
                "incompatible manifest fingerprint must fail closed");
        assertTrue(String.valueOf(ex).contains("cannot prove compatible"),
                "rejection must identify the fingerprint gate: " + ex);
        assertEquals(0L, counter.get(), "rejected gate must not advance the counter");
    }

    @Test
    void fingerprintWithoutSourceFailsClosed() throws Exception {
        LocalFileCheckpointStorage storage = storage();
        storage.storeEpochManifest(JOB_ID, PIPELINE_ID, manifest(7L, fingerprint("dag-1")));
        CheckpointIDCounter counter = new CheckpointIDCounter();
        CheckpointCoordinator coord = freshCoordinator(storage, counter);
        // no setCurrentFingerprint — no fingerprint source

        StreamException ex = assertThrows(StreamException.class,
                () -> RestoreGates.applyManifestGate(coord, JOB_ID),
                "unprovable compatibility must fail closed");
        assertTrue(String.valueOf(ex).contains("cannot prove compatible"));
        assertEquals(0L, counter.get());
    }

    // ==================== helpers ====================

    private CheckpointCoordinator freshCoordinator(CheckpointIDCounter counter) {
        return new CheckpointCoordinator(JOB_ID, PIPELINE_ID, counter,
                new LocalFileCheckpointStorage(tempDir.toString()),
                CheckpointConfig.builder().checkpointEnabled(true).build());
    }

    private CheckpointCoordinator freshCoordinator(LocalFileCheckpointStorage storage,
                                                   CheckpointIDCounter counter) {
        return new CheckpointCoordinator(JOB_ID, PIPELINE_ID, counter,
                storage, CheckpointConfig.builder().checkpointEnabled(true).build());
    }

    private LocalFileCheckpointStorage storage() {
        return new LocalFileCheckpointStorage(tempDir.toString());
    }

    private EpochManifest manifest(long epochId, StreamModelFingerprint fingerprint) {
        TaskLocation loc = new TaskLocation(JOB_ID, PIPELINE_ID, "source", 0);
        Map<TaskLocation, TaskStateSnapshot> snapshots = new LinkedHashMap<>();
        snapshots.put(loc, TaskStateSnapshot.empty(loc));
        return new EpochManifest(epochId, JOB_ID, PIPELINE_ID, System.currentTimeMillis(),
                CheckpointType.CHECKPOINT, EpochState.COMMITTED, snapshots, fingerprint, null);
    }

    private StreamModelFingerprint fingerprint(String dag) {
        return new StreamModelFingerprint("1.0", Map.of("c", dag), dag, "req", "part");
    }

}
