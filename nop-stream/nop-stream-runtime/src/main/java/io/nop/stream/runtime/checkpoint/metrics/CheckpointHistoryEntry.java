/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.metrics;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Item 16 (P-REQ-6): one checkpoint observation-history record. Recorded at
 * the REAL completion / failure / abort paths of CheckpointCoordinator and
 * retained in a bounded history queryable through the ops HTTP endpoints.
 * Failed/aborted entries always carry a {@code failureCause}.
 */
@DataBean
public class CheckpointHistoryEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        COMPLETED,
        FAILED,
        ABORTED
    }

    private final long checkpointId;
    private final Status status;
    private final long triggerTimestamp;
    private final long durationMs;
    private final long sizeBytes;
    private final String failureCause;
    private final long recordedAt;

    public CheckpointHistoryEntry(long checkpointId, Status status, long triggerTimestamp,
                                  long durationMs, long sizeBytes, String failureCause, long recordedAt) {
        this.checkpointId = checkpointId;
        this.status = status;
        this.triggerTimestamp = triggerTimestamp;
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.failureCause = failureCause;
        this.recordedAt = recordedAt;
    }

    public long getCheckpointId() {
        return checkpointId;
    }

    public Status getStatus() {
        return status;
    }

    public long getTriggerTimestamp() {
        return triggerTimestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getFailureCause() {
        return failureCause;
    }

    public long getRecordedAt() {
        return recordedAt;
    }

    @Override
    public String toString() {
        return "CheckpointHistoryEntry{" + checkpointId + " " + status
                + " dur=" + durationMs + "ms size=" + sizeBytes
                + " cause=" + failureCause + '}';
    }
}
