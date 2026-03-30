/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.storage;

import io.nop.stream.core.checkpoint.CompletedCheckpoint;

import java.util.List;

/**
 * Checkpoint 持久化存储接口。
 */
public interface ICheckpointStorage {

    /**
     * 存储 checkpoint。
     *
     * @param checkpoint 要存储的 checkpoint
     * @return 存储路径或标识
     */
    String storeCheckPoint(CompletedCheckpoint checkpoint) throws Exception;

    /**
     * 获取最近的 checkpoint。
     *
     * @param jobId      作业 ID
     * @param pipelineId 流水线 ID
     * @return 最近的 checkpoint，如果不存在则返回 null
     */
    CompletedCheckpoint getLatestCheckpoint(long jobId, int pipelineId) throws Exception;

    /**
     * 获取所有 checkpoint。
     *
     * @param jobId 作业 ID
     * @return checkpoint 列表，按 checkpoint ID 降序排列
     */
    List<CompletedCheckpoint> getAllCheckpoints(long jobId) throws Exception;

    /**
     * 获取指定数量的最近 checkpoint。
     *
     * @param jobId  作业 ID
     * @param count  最大数量
     * @return checkpoint 列表
     */
    List<CompletedCheckpoint> getLatestCheckpoints(long jobId, int count) throws Exception;

    /**
     * 删除 checkpoint。
     *
     * @param jobId       作业 ID
     * @param pipelineId  流水线 ID
     * @param checkpointId checkpoint ID
     */
    void deleteCheckpoint(long jobId, int pipelineId, long checkpointId) throws Exception;

    /**
     * 删除作业的所有 checkpoint。
     *
     * @param jobId 作业 ID
     */
    void deleteAllCheckpoints(long jobId) throws Exception;

    /**
     * 获取存储名称。
     *
     * @return 存储名称
     */
    String getName();

    /**
     * 检查 checkpoint 是否存在。
     *
     * @param jobId       作业 ID
     * @param pipelineId  流水线 ID
     * @param checkpointId checkpoint ID
     * @return true 如果存在
     */
    default boolean exists(long jobId, int pipelineId, long checkpointId) throws Exception {
        List<CompletedCheckpoint> checkpoints = getAllCheckpoints(jobId);
        for (CompletedCheckpoint checkpoint : checkpoints) {
            if (checkpoint.getPipelineId() == pipelineId
                    && checkpoint.getCheckpointId() == checkpointId) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取 checkpoint 数量。
     *
     * @param jobId 作业 ID
     * @return checkpoint 数量
     */
    int getCheckpointCount(long jobId) throws Exception;
}
