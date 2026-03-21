/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Checkpoint monitoring metrics.
 */
public class CheckpointMetrics {

    private final AtomicLong numCompletedCheckpoints = new AtomicLong();
    private final AtomicLong numFailedCheckpoints = new AtomicLong();
    private final AtomicLong numAbortedCheckpoints = new AtomicLong();
    private final AtomicLong latestCheckpointSize = new AtomicLong();
    private final AtomicLong latestCheckpointDuration = new AtomicLong();
    private final AtomicLong totalStateSize = new AtomicLong();
    private final AtomicLong lastCheckpointTimestamp = new AtomicLong();

    public void incrementCompletedCheckpoints() {
        numCompletedCheckpoints.incrementAndGet();
    }

    public void incrementFailedCheckpoints() {
        numFailedCheckpoints.incrementAndGet();
    }

    public void incrementAbortedCheckpoints() {
        numAbortedCheckpoints.incrementAndGet();
    }

    public void updateLatestCheckpoint(long size, long duration) {
        latestCheckpointSize.set(size);
        latestCheckpointDuration.set(duration);
        lastCheckpointTimestamp.set(System.currentTimeMillis());
    }

    public void addToTotalStateSize(long size) {
        totalStateSize.addAndGet(size);
    }

    public long getNumCompletedCheckpoints() {
        return numCompletedCheckpoints.get();
    }

    public long getNumFailedCheckpoints() {
        return numFailedCheckpoints.get();
    }

    public long getNumAbortedCheckpoints() {
        return numAbortedCheckpoints.get();
    }

    public long getLatestCheckpointSize() {
        return latestCheckpointSize.get();
    }

    public long getLatestCheckpointDuration() {
        return latestCheckpointDuration.get();
    }

    public long getTotalStateSize() {
        return totalStateSize.get();
    }

    public long getLastCheckpointTimestamp() {
        return lastCheckpointTimestamp.get();
    }

    public void reset() {
        numCompletedCheckpoints.set(0);
        numFailedCheckpoints.set(0);
        numAbortedCheckpoints.set(0);
        latestCheckpointSize.set(0);
        latestCheckpointDuration.set(0);
        totalStateSize.set(0);
        lastCheckpointTimestamp.set(0);
    }

    public CheckpointMetricsSnapshot snapshot() {
        return new CheckpointMetricsSnapshot(
                numCompletedCheckpoints.get(),
                numFailedCheckpoints.get(),
                numAbortedCheckpoints.get(),
                latestCheckpointSize.get(),
                latestCheckpointDuration.get(),
                totalStateSize.get(),
                lastCheckpointTimestamp.get()
        );
    }
}
