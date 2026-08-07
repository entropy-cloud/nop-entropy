/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.SavepointMetadata;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.incremental.SharedStateRegistry;
import io.nop.stream.core.checkpoint.storage.CheckpointStorageException;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.checkpoint.storage.ISegmentStore;
import io.nop.stream.core.checkpoint.storage.LocalFileSegmentStore;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.rocksdb.RocksDBKeyedStateBackend;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the incremental-persist reference-count leak: when
 * the storage persist fails AFTER {@code buildAndMaterializeSegments} has
 * registered SST handles and materialized SST files, the failure branch must
 * roll back the registry ref-counts and physically discard the now-zero-ref
 * SST files. Without the rollback the ref-counts are stranded (the success
 * branch never populates the GC map) and the SST files leak on disk until the
 * coordinator restart rebuilds the registry.
 *
 * <p>Wiring verification: a recording {@link ISegmentStore} wrapper captures
 * every materialized and discarded hash, so the test proves the failure path
 * actually invokes {@code unregister} + {@code discardSegment}, not just that
 * the registry eventually reports zero (which would also hold if nothing was
 * ever registered).
 */
class TestCheckpointCoordinatorIncrementalPersistRollback {

    @TempDir
    Path tmp;

    private static final class RecordingSegmentStore implements ISegmentStore {
        private final ISegmentStore delegate;
        final Set<String> stored = ConcurrentHashMap.newKeySet();
        final Set<String> discarded = ConcurrentHashMap.newKeySet();

        RecordingSegmentStore(ISegmentStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public void storeSegment(Path sourceFile, String contentHash) throws java.io.IOException {
            stored.add(contentHash);
            delegate.storeSegment(sourceFile, contentHash);
        }

        @Override
        public void discardSegment(String contentHash) throws java.io.IOException {
            discarded.add(contentHash);
            delegate.discardSegment(contentHash);
        }

        @Override
        public boolean segmentExists(String contentHash) {
            return delegate.segmentExists(contentHash);
        }

        @Override
        public Path getSegmentPath(String contentHash) {
            return delegate.getSegmentPath(contentHash);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }
    }

    /**
     * Delegates every method to a real {@link LocalFileCheckpointStorage} except
     * {@code storeCheckPoint}, which throws so the incremental persist fails AFTER
     * segments have been built/registered/materialized (the exact leak window).
     */
    private static final class FailingOnStoreCheckpointStorage implements ICheckpointStorage {
        private final ICheckpointStorage delegate;

        FailingOnStoreCheckpointStorage(ICheckpointStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return "FailingOnStoreCheckpoint(" + delegate.getName() + ")";
        }

        @Override
        public String storeCheckPoint(CompletedCheckpoint checkpoint) throws CheckpointStorageException {
            throw new StreamException("simulated storeCheckPoint failure for cp " + checkpoint.getCheckpointId());
        }

        @Override
        public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) throws CheckpointStorageException {
            return delegate.getLatestCheckpoint(jobId, pipelineId);
        }

        @Override
        public List<CompletedCheckpoint> getAllCheckpoints(String jobId) throws CheckpointStorageException {
            return delegate.getAllCheckpoints(jobId);
        }

        @Override
        public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) throws CheckpointStorageException {
            return delegate.getLatestCheckpoints(jobId, count);
        }

        @Override
        public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) throws CheckpointStorageException {
            delegate.deleteCheckpoint(jobId, pipelineId, checkpointId);
        }

        @Override
        public void deleteAllCheckpoints(String jobId) throws CheckpointStorageException {
            delegate.deleteAllCheckpoints(jobId);
        }

        @Override
        public int getCheckpointCount(String jobId) throws CheckpointStorageException {
            return delegate.getCheckpointCount(jobId);
        }

        @Override
        public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws CheckpointStorageException {
            return delegate.storeSavepoint(checkpoint, targetPath);
        }

        @Override
        public CompletedCheckpoint loadSavepoint(String savepointPath) throws CheckpointStorageException {
            return delegate.loadSavepoint(savepointPath);
        }

        @Override
        public SavepointMetadata loadSavepointMetadata(String savepointPath) throws CheckpointStorageException {
            return delegate.loadSavepointMetadata(savepointPath);
        }

        @Override
        public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) throws CheckpointStorageException {
            delegate.storeEpochManifest(jobId, pipelineId, manifest);
        }

        @Override
        public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) throws CheckpointStorageException {
            return delegate.loadLatestEpochManifest(jobId, pipelineId);
        }
    }

    private static final class AbortedLatch implements CheckpointListener {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile long abortedId = -1;

        @Override
        public void notifyCheckpointComplete(long checkpointId) {
        }

        @Override
        public void notifyCheckpointAborted(long checkpointId) {
            abortedId = checkpointId;
            latch.countDown();
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }

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

    @Test
    void storeCheckPointFailureRollsBackIncrementalSegments() throws Exception {
        LocalFileCheckpointStorage realStorage = new LocalFileCheckpointStorage(tmp.resolve("cp-rb").toString());
        LocalFileSegmentStore realStore = new LocalFileSegmentStore(tmp.resolve("ss-rb"));
        RecordingSegmentStore recording = new RecordingSegmentStore(realStore);
        ICheckpointStorage failingStorage = new FailingOnStoreCheckpointStorage(realStorage);

        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setAsyncSnapshotEnabled(true);
        config.setMaxRetainedCheckpoints(5);
        config.setMinPause(0L);
        TaskLocation loc = new TaskLocation("job-rb", "pipe-rb", "v1", 0);
        CheckpointCoordinator cc = new CheckpointCoordinator(
                "job-rb", "pipe-rb", new CheckpointIDCounter(), failingStorage, config);
        cc.setIncrementalCheckpointEnabled(true);
        cc.setSegmentStore(recording);
        cc.setTasksToAcknowledge(Collections.singleton(loc));

        AbortedLatch latch = new AbortedLatch();
        cc.addListener(latch);

        StateSnapshot keyed = rocksIncrementalSnapshot("db-rb", "ckp-rb");
        TaskStateSnapshot taskSnap = new TaskStateSnapshot(loc);
        taskSnap.putKeyedState("keyed-state", keyed);
        long cpId = cc.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT).getCheckpointId();
        cc.acknowledgeTask(loc, cpId, taskSnap);

        assertTrue(latch.await(30, TimeUnit.SECONDS),
                "checkpoint " + cpId + " must be aborted after storeCheckPoint failure");
        assertEquals(cpId, latch.abortedId, "aborted checkpoint id must match");

        // Segments were materialized before the persist failed.
        assertFalse(recording.stored.isEmpty(),
                "buildAndMaterializeSegments must have materialized SST segments before the persist");

        // Every materialized SST must have been physically discarded on rollback.
        // Give the inline discard (still on the persist executor thread, but
        // completes before the abort notification) a brief grace window.
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
                && !recording.stored.equals(recording.discarded)) {
            Thread.sleep(50);
        }
        assertEquals(recording.stored, recording.discarded,
                "every SST materialized before the failed persist must be discarded; "
                        + "stored=" + recording.stored + " discarded=" + recording.discarded);

        // Registry ref-counts for those segments must be back to 0 (no stranding).
        SharedStateRegistry registry = cc.getSharedStateRegistry();
        for (String hash : recording.stored) {
            assertEquals(0, registry.getReferenceCount(hash),
                    "ref-count for " + hash + " must drop back to 0 after rollback");
            assertFalse(realStore.segmentExists(hash),
                    "physical SST " + hash + " must be reclaimed after rollback");
        }
        cc.shutdown();
    }

    @Test
    void storeEpochManifestFailureRollsBackIncrementalSegments() throws Exception {
        // Second leak window: storeCheckPoint SUCCEEDS but storeEpochManifest
        // throws. The checkpoint is still aborted, so its newly-registered
        // segments must be released too.
        LocalFileCheckpointStorage realStorage = new LocalFileCheckpointStorage(tmp.resolve("cp-rb2").toString());
        LocalFileSegmentStore realStore = new LocalFileSegmentStore(tmp.resolve("ss-rb2"));
        RecordingSegmentStore recording = new RecordingSegmentStore(realStore);
        final AtomicReference<String> storedCpPath = new AtomicReference<>();

        ICheckpointStorage failOnManifest = new ICheckpointStorage() {
            @Override
            public String getName() {
                return "FailingOnManifest";
            }

            @Override
            public String storeCheckPoint(CompletedCheckpoint checkpoint) throws CheckpointStorageException {
                String path = realStorage.storeCheckPoint(checkpoint);
                storedCpPath.set(path);
                return path;
            }

            @Override
            public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) throws CheckpointStorageException {
                return realStorage.getLatestCheckpoint(jobId, pipelineId);
            }

            @Override
            public List<CompletedCheckpoint> getAllCheckpoints(String jobId) throws CheckpointStorageException {
                return realStorage.getAllCheckpoints(jobId);
            }

            @Override
            public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) throws CheckpointStorageException {
                return realStorage.getLatestCheckpoints(jobId, count);
            }

            @Override
            public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) throws CheckpointStorageException {
                realStorage.deleteCheckpoint(jobId, pipelineId, checkpointId);
            }

            @Override
            public void deleteAllCheckpoints(String jobId) throws CheckpointStorageException {
                realStorage.deleteAllCheckpoints(jobId);
            }

            @Override
            public int getCheckpointCount(String jobId) throws CheckpointStorageException {
                return realStorage.getCheckpointCount(jobId);
            }

            @Override
            public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws CheckpointStorageException {
                return realStorage.storeSavepoint(checkpoint, targetPath);
            }

            @Override
            public CompletedCheckpoint loadSavepoint(String savepointPath) throws CheckpointStorageException {
                return realStorage.loadSavepoint(savepointPath);
            }

            @Override
            public SavepointMetadata loadSavepointMetadata(String savepointPath) throws CheckpointStorageException {
                return realStorage.loadSavepointMetadata(savepointPath);
            }

            @Override
            public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) throws CheckpointStorageException {
                throw new StreamException("simulated storeEpochManifest failure");
            }

            @Override
            public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) throws CheckpointStorageException {
                return realStorage.loadLatestEpochManifest(jobId, pipelineId);
            }
        };

        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setAsyncSnapshotEnabled(true);
        config.setMaxRetainedCheckpoints(5);
        config.setMinPause(0L);
        TaskLocation loc = new TaskLocation("job-rb2", "pipe-rb2", "v1", 0);
        CheckpointCoordinator cc = new CheckpointCoordinator(
                "job-rb2", "pipe-rb2", new CheckpointIDCounter(), failOnManifest, config);
        cc.setIncrementalCheckpointEnabled(true);
        cc.setSegmentStore(recording);
        cc.setTasksToAcknowledge(Collections.singleton(loc));

        AbortedLatch latch = new AbortedLatch();
        cc.addListener(latch);

        StateSnapshot keyed = rocksIncrementalSnapshot("db-rb2", "ckp-rb2");
        TaskStateSnapshot taskSnap = new TaskStateSnapshot(loc);
        taskSnap.putKeyedState("keyed-state", keyed);
        long cpId = cc.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT).getCheckpointId();
        cc.acknowledgeTask(loc, cpId, taskSnap);

        assertTrue(latch.await(30, TimeUnit.SECONDS),
                "checkpoint " + cpId + " must be aborted after storeEpochManifest failure");

        assertFalse(recording.stored.isEmpty(),
                "segments must be materialized before the manifest persist");

        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
                && !recording.stored.equals(recording.discarded)) {
            Thread.sleep(50);
        }
        assertEquals(recording.stored, recording.discarded,
                "every SST must be discarded after manifest-persist rollback");

        SharedStateRegistry registry = cc.getSharedStateRegistry();
        for (String hash : recording.stored) {
            assertEquals(0, registry.getReferenceCount(hash),
                    "ref-count for " + hash + " must drop back to 0 after manifest-failure rollback");
            assertFalse(realStore.segmentExists(hash),
                    "physical SST " + hash + " must be reclaimed after manifest-failure rollback");
        }
        cc.shutdown();
    }
}
