/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.storage;

import java.util.List;

import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.SavepointMetadata;

public interface ICheckpointStorage {

    String storeCheckPoint(CompletedCheckpoint checkpoint) throws CheckpointStorageException;

    CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) throws CheckpointStorageException;

    List<CompletedCheckpoint> getAllCheckpoints(String jobId) throws CheckpointStorageException;

    List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) throws CheckpointStorageException;

    void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) throws CheckpointStorageException;

    void deleteAllCheckpoints(String jobId) throws CheckpointStorageException;

    String getName();

    default boolean exists(String jobId, String pipelineId, long checkpointId) throws CheckpointStorageException {
        List<CompletedCheckpoint> checkpoints = getAllCheckpoints(jobId);
        for (CompletedCheckpoint checkpoint : checkpoints) {
            if (pipelineId.equals(checkpoint.getPipelineId())
                    && checkpoint.getCheckpointId() == checkpointId) {
                return true;
            }
        }
        return false;
    }

    int getCheckpointCount(String jobId) throws CheckpointStorageException;

    String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws CheckpointStorageException;

    CompletedCheckpoint loadSavepoint(String savepointPath) throws CheckpointStorageException;

    SavepointMetadata loadSavepointMetadata(String savepointPath) throws CheckpointStorageException;

    void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) throws CheckpointStorageException;

    EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) throws CheckpointStorageException;

    /**
     * Stage 31: load up to {@code count} most-recent retained EpochManifests for restart
     * recovery (rebuilding {@code SharedStateRegistry} reference counts). The default
     * implementation returns at most the latest manifest; storages that keep per-epoch
     * manifest files (e.g. {@code LocalFileCheckpointStorage}) override to return the
     * full retained set.
     */
    default java.util.List<EpochManifest> loadRetainedEpochManifests(String jobId, String pipelineId, int count)
            throws CheckpointStorageException {
        EpochManifest latest = loadLatestEpochManifest(jobId, pipelineId);
        if (latest == null) {
            return java.util.Collections.emptyList();
        }
        return java.util.Collections.singletonList(latest);
    }

    /**
     * Manifest retention (roadmap item 33, checkpoint-design §9.2 D1/D2): prune the
     * epoch-manifest plane so that at most {@code maxRetained} most-recent manifests
     * per {@code (jobId, pipelineId)} remain, ordered newest-first by epochId — the
     * SAME ordering as {@link #loadRetainedEpochManifests}, so the restore read set
     * is always a subset of the prune keep set. Returns the epoch ids that were
     * pruned (empty when nothing exceeded the bound).
     *
     * <p>The default implementation is a no-op returning an empty list: acceptable
     * ONLY for test doubles (items 28/31 default-method precedent — doubles are not
     * forced to migrate). Production storages that persist per-epoch manifests
     * ({@code LocalFileCheckpointStorage} / {@code JdbcCheckpointStorage}) MUST
     * override with real semantics; the coordinator retention path calls this face
     * every round and the pruning effect is pinned by focused tests, so a missing
     * production override is detectable by tests — never a silent skip.
     */
    default List<Long> pruneEpochManifests(String jobId, String pipelineId, int maxRetained)
            throws CheckpointStorageException {
        return java.util.Collections.emptyList();
    }
}
