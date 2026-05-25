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

    String storeCheckPoint(CompletedCheckpoint checkpoint) throws Exception;

    CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) throws Exception;

    List<CompletedCheckpoint> getAllCheckpoints(String jobId) throws Exception;

    List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) throws Exception;

    void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) throws Exception;

    void deleteAllCheckpoints(String jobId) throws Exception;

    String getName();

    default boolean exists(String jobId, String pipelineId, long checkpointId) throws Exception {
        List<CompletedCheckpoint> checkpoints = getAllCheckpoints(jobId);
        for (CompletedCheckpoint checkpoint : checkpoints) {
            if (pipelineId.equals(checkpoint.getPipelineId())
                    && checkpoint.getCheckpointId() == checkpointId) {
                return true;
            }
        }
        return false;
    }

    int getCheckpointCount(String jobId) throws Exception;

    String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws Exception;

    CompletedCheckpoint loadSavepoint(String savepointPath) throws Exception;

    SavepointMetadata loadSavepointMetadata(String savepointPath) throws Exception;

    void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) throws Exception;

    EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) throws Exception;
}
