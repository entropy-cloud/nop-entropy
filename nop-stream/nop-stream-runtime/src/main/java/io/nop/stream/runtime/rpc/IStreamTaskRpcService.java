/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.runtime.cluster.TaskAssignment;

@Internal
public interface IStreamTaskRpcService {

    void receiveAssignment(TaskAssignment assignment);

    /**
     * @param fencingEpoch monotonic fencing epoch (Stage 39: long, replaces composite String)
     */
    void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch);

    void cancelTask(String jobId, String vertexId, int subtaskIndex);

    /**
     * Stage 39: pushes the rotated monotonic fencing epoch to the task side.
     *
     * @param fencingEpoch the new monotonic fencing epoch
     */
    void updateFencingToken(long fencingEpoch);
}
