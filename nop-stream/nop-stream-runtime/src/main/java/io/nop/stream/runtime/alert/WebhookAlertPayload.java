package io.nop.stream.runtime.alert;

import io.nop.api.core.annotations.data.DataBean;

/**
 * webhook 投递 payload（ST-5 修复：替代手写 JSON 拼接——原实现 severity 原样内插且
 * 手写 json() 转义遗漏控制字符，message 含特殊字符时产生非法 JSON）。
 */
@DataBean
public class WebhookAlertPayload {
    private final String jobId;
    private final String severity;
    private final String eventType;
    private final String message;
    private final long timestamp;

    public WebhookAlertPayload(AlertEvent event) {
        this.jobId = event.getJobId();
        this.severity = event.getSeverity() == null ? null : event.getSeverity().name();
        this.eventType = event.getEventType();
        this.message = event.getMessage();
        this.timestamp = event.getTimestamp();
    }

    public String getJobId() {
        return jobId;
    }

    public String getSeverity() {
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
}
