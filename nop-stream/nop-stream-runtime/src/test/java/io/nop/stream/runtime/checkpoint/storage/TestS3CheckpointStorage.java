/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.checkpoint.storage;

import com.amazonaws.services.s3.model.S3Object;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.SavepointMetadata;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI13 (plan 2339): offline storage behavior tests for {@link S3CheckpointStorage}
 * over the in-memory S3 fake — the full SPI surface (checkpoint/manifest/savepoint
 * round-trips, latest = max checkpointId, delete both planes, prune with retained
 * set, key-injection typed rejection) without any network.
 */
class TestS3CheckpointStorage {

    private InMemoryAmazonS3 fake;
    private S3CheckpointStorage storage;

    @BeforeEach
    void setUp() {
        fake = new InMemoryAmazonS3();
        fake.createBucketStore("test-bucket");
        storage = new S3CheckpointStorage(fake, "test-bucket", "nop-stream-checkpoints");
    }

    private static CompletedCheckpoint checkpoint(long id) {
        Map<TaskLocation, TaskStateSnapshot> states = new HashMap<>();
        TaskLocation loc = new TaskLocation("job-1", "pipe-0", "v1", 0);
        TaskStateSnapshot snap = new TaskStateSnapshot(loc, 0L);
        snap.putOperatorState("state1", "data-" + id);
        states.put(loc, snap);
        return new CompletedCheckpoint("job-1", "pipe-0", id, id * 10, id * 10,
                CheckpointType.CHECKPOINT, states);
    }

    private static EpochManifest manifest(long epochId) {
        return new EpochManifest(epochId, "job-1", "pipe-0", epochId * 10,
                CheckpointType.CHECKPOINT, null, new HashMap<>(), null, null);
    }

    @Test
    void checkpointRoundTripAndLatestById() {
        storage.storeCheckPoint(checkpoint(3));
        storage.storeCheckPoint(checkpoint(1));
        storage.storeCheckPoint(checkpoint(2));

        // latest = max checkpointId parsed from the key (mirrors local, zero GETs)
        CompletedCheckpoint latest = storage.getLatestCheckpoint("job-1", "pipe-0");
        assertNotNull(latest);
        assertEquals(3L, latest.getCheckpointId());
        assertEquals("job-1", latest.getJobId());

        List<CompletedCheckpoint> all = storage.getAllCheckpoints("job-1");
        assertEquals(3, all.size());
        assertEquals(1L, all.get(0).getCheckpointId(), "ascending checkpointId order");

        List<CompletedCheckpoint> latest2 = storage.getLatestCheckpoints("job-1", 2);
        assertEquals(2, latest2.size());
        assertEquals(3L, latest2.get(0).getCheckpointId(), "descending order for latest");

        // state round-trips through SerDe
        for (TaskStateSnapshot snap : latest.getTaskStates().values()) {
            assertNotNull(snap);
            assertEquals("data-3", snap.getOperatorState("state1"));
        }
    }

    @Test
    void deleteSingleAndDeleteAllClearBothPlanes() {
        storage.storeCheckPoint(checkpoint(1));
        storage.storeCheckPoint(checkpoint(2));
        storage.storeEpochManifest("job-1", "pipe-0", manifest(1));

        assertTrue(storage.exists("job-1", "pipe-0", 1));
        assertEquals(2, storage.getCheckpointCount("job-1"));

        storage.deleteCheckpoint("job-1", "pipe-0", 1L);
        assertFalse(storage.exists("job-1", "pipe-0", 1));
        assertEquals(1, storage.getCheckpointCount("job-1"));

        storage.deleteAllCheckpoints("job-1");
        assertEquals(0, storage.getCheckpointCount("job-1"));
        assertNull(storage.loadLatestEpochManifest("job-1", "pipe-0"),
                "delete-all must clear the manifest plane too");
    }

    @Test
    void epochManifestRoundTripWithRetainedSetAndPrune() {
        for (long e = 1; e <= 3; e++) {
            storage.storeEpochManifest("job-1", "pipe-0", manifest(e));
        }

        EpochManifest latest = storage.loadLatestEpochManifest("job-1", "pipe-0");
        assertNotNull(latest);
        assertEquals(3L, latest.getEpochId());

        // both overrides present (review Mj3): retained set > 1
        List<EpochManifest> retained = storage.loadRetainedEpochManifests("job-1", "pipe-0", 2);
        assertEquals(2, retained.size());
        assertEquals(3L, retained.get(0).getEpochId(), "descending order (newest first)");
        assertEquals(2L, retained.get(1).getEpochId());

        List<Long> pruned = storage.pruneEpochManifests("job-1", "pipe-0", 1);
        assertEquals(2, pruned.size());
        assertEquals(3L, storage.loadLatestEpochManifest("job-1", "pipe-0").getEpochId(),
                "prune keeps the newest maxRetained manifests");
    }

    @Test
    void savepointRoundTripInsidePrefix() {
        CompletedCheckpoint cp = checkpoint(5);
        String path = storage.storeSavepoint(cp, "nop-stream-checkpoints/job-1/savepoints");

        CompletedCheckpoint loaded = storage.loadSavepoint(path);
        assertNotNull(loaded);
        assertEquals(5L, loaded.getCheckpointId());

        SavepointMetadata meta = storage.loadSavepointMetadata(path);
        assertNotNull(meta);

        // outside basePrefix = typed rejection (mirrors local validatePath)
        StreamException ex = assertThrows(StreamException.class,
                () -> storage.storeSavepoint(cp, "elsewhere/savepoints"));
        assertTrue(String.valueOf(ex).contains("basePrefix"), "rejection must name the boundary");
    }

    @Test
    void keyInjectionRejectedTyped() {
        assertThrows(StreamException.class, () -> storage.storeCheckPoint(
                new CompletedCheckpoint("../evil", "pipe", 1, 1, 1, CheckpointType.CHECKPOINT, new HashMap<>())));
        assertThrows(StreamException.class, () -> storage.exists("job-1", "pipe/../../x", 1));
    }

    @Test
    void absentStateReturnsNull() {
        assertNull(storage.getLatestCheckpoint("no-such-job", "pipe"));
        assertNull(storage.loadLatestEpochManifest("no-such-job", "pipe"));
        assertFalse(storage.exists("no-such-job", "pipe", 1));
        assertEquals(0, storage.getCheckpointCount("no-such-job"));
    }

}
