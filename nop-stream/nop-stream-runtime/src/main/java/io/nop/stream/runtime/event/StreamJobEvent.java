/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.event;

import io.nop.api.core.time.CoreMetrics;
import java.io.Serializable;

/**
 * Item 16 (P-REQ-2): a streaming job lifecycle / progress event fired from
 * real coordinator lifecycle paths and dispatched to all registered
 * {@link StreamJobEventListener}s.
 *
 * <p>"Progress" equivalence in the continuous-stream model: CHECKPOINT_*
 * and RECOVERY_* events are the progress anchors (no batch boundary exists).
 */
public class StreamJobEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum EventType {
        JOB_STARTED,
        CHECKPOINT_COMPLETED,
        CHECKPOINT_FAILED,
        CHECKPOINT_ABORTED,
        RECOVERY_STARTED,
        RECOVERY_COMPLETED,
        JOB_FAILED,
        JOB_CANCELED,
        JOB_FINISHED,
        JOB_DEGRADED
    }

    private final String jobId;
    private final EventType type;
    private final long timestamp;
    private final Long checkpointId;
    private final Long durationMs;
    private final Long sizeBytes;
    private final String cause;

    public StreamJobEvent(String jobId, EventType type, long timestamp,
                          Long checkpointId, Long durationMs, Long sizeBytes, String cause) {
        this.jobId = jobId;
        this.type = type;
        this.timestamp = timestamp;
        this.checkpointId = checkpointId;
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.cause = cause;
    }

    public static StreamJobEvent simple(String jobId, EventType type, String cause) {
        return new StreamJobEvent(jobId, type, CoreMetrics.currentTimeMillis(), null, null, null, cause);
    }

    public String getJobId() {
        return jobId;
    }

    public EventType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Long getCheckpointId() {
        return checkpointId;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return "StreamJobEvent{" + type + ", job=" + jobId + ", checkpointId=" + checkpointId
                + ", durationMs=" + durationMs + ", sizeBytes=" + sizeBytes
                + ", cause=" + cause + ", at=" + timestamp + '}';
    }
}
