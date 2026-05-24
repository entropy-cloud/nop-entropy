/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.runtime.cluster.TaskAssignment;

public interface IStreamTaskRpcService {

    void receiveAssignment(TaskAssignment assignment);

    void triggerCheckpoint(CheckpointBarrier barrier, String fencingToken);

    void cancelTask(String jobId, String vertexId, int subtaskIndex);
}
