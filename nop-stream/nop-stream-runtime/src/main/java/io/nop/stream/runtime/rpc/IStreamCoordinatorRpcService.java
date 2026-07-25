/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import java.util.List;

import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.runtime.coordinator.TaskProgress;
import io.nop.stream.runtime.coordinator.TaskStatusReport;
import io.nop.stream.runtime.taskmanager.CheckpointAckMessage;

@Internal
public interface IStreamCoordinatorRpcService {

    void receiveCheckpointAck(CheckpointAckMessage ack);

    /**
     * G52: per-task terminal-state report from a RunningTask. The coordinator
     * uses this to detect a task FAILURE even when its host node is still alive
     * (the gap that node-level lease detection cannot close). Also serves as a
     * strong terminal signal for COMPLETED tasks.
     *
     * <p>Implementations must surface failures (not silently swallow). At
     * minimum, log the report and update per-subtask liveness; the
     * {@code JobCoordinator} additionally triggers recovery on FAILED reports.
     */
    void reportTaskStatus(TaskStatusReport report);

    /**
     * G52: per-node batched liveness piggybacked on {@code TaskManager.heartbeat()}.
     * Each entry carries one task's {@code lastProgressTime}; the coordinator
     * detects stalls by comparing against {@code taskTimeout}.
     *
     * @param nodeId the reporting node
     * @param progress per-task liveness for tasks currently running on {@code nodeId}
     */
    void reportNodeTaskLiveness(String nodeId, List<TaskProgress> progress);
}
