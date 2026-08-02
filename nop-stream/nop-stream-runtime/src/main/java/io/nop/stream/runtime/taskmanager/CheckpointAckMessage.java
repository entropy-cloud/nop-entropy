/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;

/**
 * Message sent from TaskManager to JobCoordinator to acknowledge a checkpoint.
 *
 * <p>Carries the monotonic fencing epoch so the coordinator can reject stale ACKs.
 */
@DataBean
public class CheckpointAckMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private TaskLocation taskLocation;
    private long checkpointId;
    private TaskStateSnapshot stateSnapshot;
    private long fencingEpoch;

    public CheckpointAckMessage() {
    }

    public CheckpointAckMessage(TaskLocation taskLocation, long checkpointId,
                                TaskStateSnapshot stateSnapshot, long fencingEpoch) {
        this.taskLocation = taskLocation;
        this.checkpointId = checkpointId;
        this.stateSnapshot = stateSnapshot;
        this.fencingEpoch = fencingEpoch;
    }

    public TaskLocation getTaskLocation() {
        return taskLocation;
    }

    public void setTaskLocation(TaskLocation taskLocation) {
        this.taskLocation = taskLocation;
    }

    public long getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(long checkpointId) {
        this.checkpointId = checkpointId;
    }

    public TaskStateSnapshot getStateSnapshot() {
        return stateSnapshot;
    }

    public void setStateSnapshot(TaskStateSnapshot stateSnapshot) {
        this.stateSnapshot = stateSnapshot;
    }

    public long getFencingEpoch() {
        return fencingEpoch;
    }

    public void setFencingEpoch(long fencingEpoch) {
        this.fencingEpoch = fencingEpoch;
    }
}
