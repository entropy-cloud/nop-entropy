/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.barrier;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;

import java.io.Serializable;

public class AlignedBarrier implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long checkpointId;
    private final CheckpointType checkpointType;
    private final long triggerTimestamp;
    private final long alignedTimestamp;
    private final int inputCount;

    public AlignedBarrier(
            long checkpointId,
            CheckpointType checkpointType,
            long triggerTimestamp,
            long alignedTimestamp,
            int inputCount) {
        this.checkpointId = checkpointId;
        this.checkpointType = checkpointType;
        this.triggerTimestamp = triggerTimestamp;
        this.alignedTimestamp = alignedTimestamp;
        this.inputCount = inputCount;
    }

    public long getCheckpointId() {
        return checkpointId;
    }

    public CheckpointType getCheckpointType() {
        return checkpointType;
    }
    public long getTriggerTimestamp() {
        return triggerTimestamp;
    }
    public long getAlignedTimestamp() {
        return alignedTimestamp;
    }

    public int getInputCount() {
        return inputCount;
    }

    public long getAlignmentDuration() {
        return alignedTimestamp - triggerTimestamp;
    }

    @Override
    public String toString() {
        return "AlignedBarrier{" +
                "checkpointId=" + checkpointId +
                ", checkpointType=" + checkpointType +
                ", triggerTimestamp=" + triggerTimestamp +
                ", alignedTimestamp=" + alignedTimestamp +
                ", alignmentDuration=" + getAlignmentDuration() + "ms" +
                ", inputCount=" + getInputCount() +
                '}';
    }
}
