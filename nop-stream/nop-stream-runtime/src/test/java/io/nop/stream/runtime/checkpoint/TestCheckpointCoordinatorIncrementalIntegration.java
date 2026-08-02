/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.StateSegmentDescriptor;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.incremental.SharedStateRegistry;
import io.nop.stream.core.checkpoint.storage.LocalFileSegmentStore;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.rocksdb.RocksDBKeyedStateBackend;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 31 Phase 4: end-to-end coordinator integration for incremental checkpoints.
 * The coordinator must actually invoke {@code SharedStateRegistry.register}, materialize
 * SST files into {@code ISegmentStore}, fill {@code EpochManifest.segments}, and discard
 * zero-reference files on subsumption — not just expose the API (Anti-Hollow check).
 */
class TestCheckpointCoordinatorIncrementalIntegration {

    @TempDir
    Path tmp;

    private static final TaskLocation LOC = new TaskLocation("job-inc", "pipe-inc", "v1", 0);

    private static final class CompletionLatch implements CheckpointListener {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile long completedId = -1;

        @Override
        public void notifyCheckpointComplete(long checkpointId) {
            completedId = checkpointId;
            latch.countDown();
        }

        @Override
        public void notifyCheckpointAborted(long checkpointId) {
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }

    private CheckpointCoordinator newCoordinator(LocalFileCheckpointStorage storage,
                                                  LocalFileSegmentStore store,
                                                  int maxRetained,
                                                  String jobId,
                                                  String pipelineId) {
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setAsyncSnapshotEnabled(true);
        config.setMaxRetainedCheckpoints(maxRetained);
        config.setMinPause(0L);
        CheckpointCoordinator cc = new CheckpointCoordinator(
                jobId, pipelineId, new CheckpointIDCounter(), storage, config);
        cc.setIncrementalCheckpointEnabled(true);
        cc.setSegmentStore(store);
        cc.setTasksToAcknowledge(Collections.singleton(new TaskLocation(jobId, pipelineId, "v1", 0)));
        return cc;
    }

    /** Produce a real RocksDB incremental keyed-state snapshot carrying SST handles. */
    private StateSnapshot rocksIncrementalSnapshot(String dbSub, String ckpBase) throws Exception {
        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                tmp.resolve(dbSub).toString(), String.class, 1, null);
        backend.setIncrementalCheckpointEnabled(true);
        backend.setCheckpointBaseDir(tmp.resolve(ckpBase).toString());
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(7L);
        backend.setCurrentKey("k2");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(13L);
        StateSnapshot snapshot = backend.snapshotState();
        backend.close();
        return snapshot;
    }

    private TaskStateSnapshot taskStateWithKeyed(TaskLocation loc, StateSnapshot keyed) {
        TaskStateSnapshot ts = new TaskStateSnapshot(loc);
        ts.putKeyedState("keyed-state", keyed);
        return ts;
    }

    private long runCheckpoint(CheckpointCoordinator cc, TaskLocation loc, StateSnapshot keyed) throws Exception {
        CompletionLatch latch = new CompletionLatch();
        cc.addListener(latch);
        long cpId = cc.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT).getCheckpointId();
        cc.acknowledgeTask(loc, cpId, taskStateWithKeyed(loc, keyed));
        assertTrue(latch.await(20, TimeUnit.SECONDS), "checkpoint " + cpId + " did not complete in time");
        assertEquals(cpId, latch.completedId);
        return cpId;
    }

    private Set<String> hashesOf(List<StateSegmentDescriptor> segs) {
        Set<String> s = new HashSet<>();
        for (StateSegmentDescriptor seg : segs) {
            s.add(seg.getPath());
        }
        return s;
    }

    // ---- anti-hollow: coordinator builds segments, registers, materializes files ----

    @Test
    void incrementalCheckpointBuildsAndMaterializesSegments() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tmp.resolve("cp1").toString());
        LocalFileSegmentStore store = new LocalFileSegmentStore(tmp.resolve("ss1"));
        TaskLocation loc = new TaskLocation("job-inc", "pipe-inc", "v1", 0);
        CheckpointCoordinator cc = newCoordinator(storage, store, 5, "job-inc", "pipe-inc");

        StateSnapshot keyed = rocksIncrementalSnapshot("db1", "ckp1");
        long cpId = runCheckpoint(cc, loc, keyed);

        EpochManifest manifest = storage.loadLatestEpochManifest("job-inc", "pipe-inc");
        assertNotNull(manifest);
        assertEquals(cpId, manifest.getEpochId());
        assertFalse(manifest.getSegments().isEmpty(),
                "EpochManifest must carry non-empty segments for an incremental checkpoint");

        Set<String> hashes = new HashSet<>();
        for (StateSegmentDescriptor seg : manifest.getSegments()) {
            assertEquals(StateSegmentDescriptor.SEGMENT_TYPE_ROCKSDB_SST, seg.getSegmentType());
            assertEquals(StateSegmentDescriptor.CODEC_IDENTITY, seg.getCodec());
            assertEquals(seg.getPath(), seg.getChecksum());
            hashes.add(seg.getPath());
        }

        SharedStateRegistry registry = cc.getSharedStateRegistry();
        assertNotNull(registry);
        for (String hash : hashes) {
            assertEquals(1, registry.getReferenceCount(hash));
            assertTrue(store.segmentExists(hash), "segment " + hash + " must be materialized in the store");
        }
        cc.shutdown();
    }

    // ---- dedup: second checkpoint shares SST handles with the first ----

    @Test
    void secondCheckpointDeduplicatesSharedSstHandles() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tmp.resolve("cp2").toString());
        LocalFileSegmentStore store = new LocalFileSegmentStore(tmp.resolve("ss2"));
        TaskLocation loc = new TaskLocation("job-dedup", "pipe-dedup", "v1", 0);
        CheckpointCoordinator cc = newCoordinator(storage, store, 5, "job-dedup", "pipe-dedup");

        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                tmp.resolve("db-shared").toString(), String.class, 1, null);
        backend.setIncrementalCheckpointEnabled(true);
        backend.setCheckpointBaseDir(tmp.resolve("ckp-shared").toString());
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(100L);
        StateSnapshot s1 = backend.snapshotState();
        StateSnapshot s2 = backend.snapshotState(); // no changes
        backend.close();

        long cp1 = runCheckpoint(cc, loc, s1);
        long cp2 = runCheckpoint(cc, loc, s2);
        assertTrue(cp2 > cp1);

        SharedStateRegistry registry = cc.getSharedStateRegistry();
        Set<String> cp1Hashes = hashesOf(cc.getCheckpointSegments(cp1));
        Set<String> cp2Hashes = hashesOf(cc.getCheckpointSegments(cp2));
        assertEquals(cp1Hashes, cp2Hashes, "no-change checkpoint must reuse the same SST hashes");

        for (String hash : cp1Hashes) {
            assertEquals(2, registry.getReferenceCount(hash),
                    "shared SST " + hash + " must have ref-count 2 after two checkpoints");
        }
        cc.shutdown();
    }

    // ---- subsumption GC: discarding an old checkpoint physically deletes zero-ref files ----

    @Test
    void subsumptionGcDiscardsZeroReferenceSegmentFiles() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tmp.resolve("cp3").toString());
        LocalFileSegmentStore store = new LocalFileSegmentStore(tmp.resolve("ss3"));
        TaskLocation loc = new TaskLocation("job-gc", "pipe-gc", "v1", 0);
        CheckpointCoordinator cc = newCoordinator(storage, store, 1, "job-gc", "pipe-gc");

        RocksDBKeyedStateBackend<String> b1 = new RocksDBKeyedStateBackend<>(
                tmp.resolve("db-gc1").toString(), String.class, 1, null);
        b1.setIncrementalCheckpointEnabled(true);
        b1.setCheckpointBaseDir(tmp.resolve("ckp-gc1").toString());
        b1.setCurrentKey("aaa");
        b1.getState(new ValueStateDescriptor<>("vs", Long.class)).update(1L);
        StateSnapshot s1 = b1.snapshotState();
        b1.close();

        long cp1 = runCheckpoint(cc, loc, s1);
        Set<String> cp1Hashes = hashesOf(cc.getCheckpointSegments(cp1));
        assertFalse(cp1Hashes.isEmpty());
        for (String h : cp1Hashes) {
            assertTrue(store.segmentExists(h));
        }

        // cp2 with distinct state on a fresh RocksDB -> new SST hashes; maxRetained=1 subsumes cp1
        RocksDBKeyedStateBackend<String> b2 = new RocksDBKeyedStateBackend<>(
                tmp.resolve("db-gc2").toString(), String.class, 1, null);
        b2.setIncrementalCheckpointEnabled(true);
        b2.setCheckpointBaseDir(tmp.resolve("ckp-gc2").toString());
        b2.setCurrentKey("zzz-unique-key");
        b2.getState(new ValueStateDescriptor<>("vs", Long.class)).update(999L);
        StateSnapshot s2 = b2.snapshotState();
        b2.close();

        runCheckpoint(cc, loc, s2);

        // Wait for the off-loaded discard executor to delete zero-ref files.
        long deadline = System.currentTimeMillis() + 5000;
        boolean allGone = false;
        while (System.currentTimeMillis() < deadline && !allGone) {
            allGone = true;
            for (String h : cp1Hashes) {
                if (store.segmentExists(h)) {
                    allGone = false;
                }
            }
            if (!allGone) {
                Thread.sleep(100);
            }
        }

        Set<String> cp2Hashes = hashesOf(cc.getCheckpointSegments(storage.loadLatestEpochManifest("job-gc", "pipe-gc").getEpochId()));
        SharedStateRegistry registry = cc.getSharedStateRegistry();
        for (String h : cp1Hashes) {
            if (!cp2Hashes.contains(h)) {
                assertFalse(store.segmentExists(h),
                        "subsumed unique segment " + h + " must be physically deleted");
                assertEquals(0, registry.getReferenceCount(h));
            }
        }
        assertTrue(allGone, "at least one subsumed unique segment must have been deleted");
        cc.shutdown();
    }

    // ---- backward compat: non-incremental checkpoint has empty segments ----

    @Test
    void nonIncrementalCheckpointHasEmptySegments() throws Exception {
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setAsyncSnapshotEnabled(true);
        config.setMaxRetainedCheckpoints(5);
        config.setMinPause(0L);
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tmp.resolve("cp-bc").toString());
        TaskLocation loc = new TaskLocation("job-bc", "pipe-bc", "v1", 0);
        CheckpointCoordinator cc = new CheckpointCoordinator(
                "job-bc", "pipe-bc", new CheckpointIDCounter(), storage, config);
        cc.setTasksToAcknowledge(Collections.singleton(loc));
        // intentionally NOT enabling incremental
        CompletionLatch latch = new CompletionLatch();
        cc.addListener(latch);
        long cpId = cc.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT).getCheckpointId();
        cc.acknowledgeTask(loc, cpId, TaskStateSnapshot.empty(loc));
        assertTrue(latch.await(20, TimeUnit.SECONDS));

        EpochManifest manifest = storage.loadLatestEpochManifest("job-bc", "pipe-bc");
        assertNotNull(manifest);
        assertTrue(manifest.getSegments().isEmpty(),
                "non-incremental checkpoint must have empty segments (backward compat)");
        assertEquals(null, cc.getSharedStateRegistry());
        cc.shutdown();
    }

    // ---- restart recovery: registry ref-counts rebuilt from retained manifests ----

    @Test
    void restartRecoveryRebuildsRegistryFromRetainedManifests() throws Exception {
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setAsyncSnapshotEnabled(true);
        config.setMaxRetainedCheckpoints(5);
        config.setMinPause(0L);
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tmp.resolve("cp-rr").toString());
        LocalFileSegmentStore segmentStore = new LocalFileSegmentStore(tmp.resolve("ss-rr"));
        TaskLocation loc = new TaskLocation("job-rr", "pipe-rr", "v1", 0);

        CheckpointCoordinator cc = new CheckpointCoordinator(
                "job-rr", "pipe-rr", new CheckpointIDCounter(), storage, config);
        cc.setIncrementalCheckpointEnabled(true);
        cc.setSegmentStore(segmentStore);
        cc.setTasksToAcknowledge(Collections.singleton(loc));

        RocksDBKeyedStateBackend<String> b = new RocksDBKeyedStateBackend<>(
                tmp.resolve("db-rr").toString(), String.class, 1, null);
        b.setIncrementalCheckpointEnabled(true);
        b.setCheckpointBaseDir(tmp.resolve("ckp-rr").toString());
        b.setCurrentKey("k");
        b.getState(new ValueStateDescriptor<>("vs", Long.class)).update(5L);
        StateSnapshot s = b.snapshotState();
        b.close();

        long cp = runCheckpoint(cc, loc, s);
        Set<String> hashes = hashesOf(cc.getCheckpointSegments(cp));
        cc.shutdown();

        // Simulate restart: a fresh coordinator over the same storage + segment store.
        CheckpointCoordinator cc2 = new CheckpointCoordinator(
                "job-rr", "pipe-rr", new CheckpointIDCounter(), storage, config);
        cc2.setIncrementalCheckpointEnabled(true);
        cc2.setSegmentStore(segmentStore);
        cc2.restoreSharedStateRegistry();

        SharedStateRegistry registry2 = cc2.getSharedStateRegistry();
        assertNotNull(registry2);
        for (String h : hashes) {
            assertEquals(1, registry2.getReferenceCount(h),
                    "registry must be rebuilt with ref-count 1 for retained segment " + h);
        }
        assertEquals(hashes, hashesOf(cc2.getCheckpointSegments(cp)));
        cc2.shutdown();
    }
}
