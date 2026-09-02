/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.alert;

/**
 * Item 16 (P-REQ-12): an outbound alert derived from a job fault-semantic
 * event. Immutable value object.
 */
public final class AlertEvent {

    public enum Severity {
        INFO,
        WARN,
        ERROR
    }

    private final String jobId;
    private final Severity severity;
    private final String eventType;
    private final String message;
    private final long timestamp;

    public AlertEvent(String jobId, Severity severity, String eventType, String message, long timestamp) {
        this.jobId = jobId;
        this.severity = severity;
        this.eventType = eventType;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getJobId() {
        return jobId;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getEventType() {
        return eventType;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "AlertEvent{job=" + jobId + ", severity=" + severity + ", type=" + eventType
                + ", message=" + message + ", at=" + timestamp + '}';
    }
}
