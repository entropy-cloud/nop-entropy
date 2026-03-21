/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.metrics;

import java.io.Serializable;

/**
 * Snapshot of checkpoint metrics at a point in time.
 */
public class CheckpointMetricsSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long numCompletedCheckpoints;
    private final long numFailedCheckpoints;
    private final long numAbortedCheckpoints;
    private final long latestCheckpointSize;
    private final long latestCheckpointDuration;
    private final long totalStateSize;
    private final long lastCheckpointTimestamp;

    public CheckpointMetricsSnapshot(
            long numCompletedCheckpoints,
            long numFailedCheckpoints,
            long numAbortedCheckpoints,
            long latestCheckpointSize,
            long latestCheckpointDuration,
            long totalStateSize,
            long lastCheckpointTimestamp) {
        this.numCompletedCheckpoints = numCompletedCheckpoints;
        this.numFailedCheckpoints = numFailedCheckpoints;
        this.numAbortedCheckpoints = numAbortedCheckpoints;
        this.latestCheckpointSize = latestCheckpointSize;
        this.latestCheckpointDuration = latestCheckpointDuration;
        this.totalStateSize = totalStateSize;
        this.lastCheckpointTimestamp = lastCheckpointTimestamp;
    }

    public long getNumCompletedCheckpoints() {
        return numCompletedCheckpoints;
    }

    public long getNumFailedCheckpoints() {
        return numFailedCheckpoints;
    }

    public long getNumAbortedCheckpoints() {
        return numAbortedCheckpoints;
    }

    public long getLatestCheckpointSize() {
        return latestCheckpointSize;
    }

    public long getLatestCheckpointDuration() {
        return latestCheckpointDuration;
    }

    public long getTotalStateSize() {
        return totalStateSize;
    }

    public long getLastCheckpointTimestamp() {
        return lastCheckpointTimestamp;
    }

    @Override
    public String toString() {
        return "CheckpointMetricsSnapshot{" +
                "numCompletedCheckpoints=" + numCompletedCheckpoints +
                ", numFailedCheckpoints=" + numFailedCheckpoints +
                ", numAbortedCheckpoints=" + numAbortedCheckpoints +
                ", latestCheckpointSize=" + latestCheckpointSize +
                ", latestCheckpointDuration=" + latestCheckpointDuration +
                ", totalStateSize=" + totalStateSize +
                ", lastCheckpointTimestamp=" + lastCheckpointTimestamp +
                '}';
    }
}
