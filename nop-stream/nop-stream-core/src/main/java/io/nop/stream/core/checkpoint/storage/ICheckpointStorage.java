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
}
