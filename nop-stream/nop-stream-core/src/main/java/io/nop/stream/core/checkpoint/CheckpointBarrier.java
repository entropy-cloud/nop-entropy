/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;
import java.util.Objects;

/**
 * Checkpoint 屏障，在数据流中传播以触发 checkpoint。
 */
public class CheckpointBarrier implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final long timestamp;
    private final CheckpointType checkpointType;

    public CheckpointBarrier(long id, long timestamp, CheckpointType checkpointType) {
        this.id = id;
        this.timestamp = timestamp;
        this.checkpointType = checkpointType;
    }

    public long getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public CheckpointType getCheckpointType() {
        return checkpointType;
    }

    public boolean snapshot() {
        return true;
    }

    public boolean prepareClose() {
        return checkpointType.isFinalCheckpoint();
    }

    public boolean isCheckpoint() {
        return checkpointType == CheckpointType.CHECKPOINT;
    }

    public boolean isSavepoint() {
        return checkpointType == CheckpointType.SAVEPOINT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckpointBarrier that = (CheckpointBarrier) o;
        return id == that.id &&
                timestamp == that.timestamp &&
                checkpointType == that.checkpointType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, timestamp, checkpointType);
    }

    @Override
    public String toString() {
        return "CheckpointBarrier{" +
                "id=" + id +
                ", timestamp=" + timestamp +
                ", checkpointType=" + checkpointType +
                '}';
    }
}
