package io.nop.job.api.log;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

/**
 * 任务日志行（plan 2254，worker → coordinator 日志上报契约）。
 * 日志行以 {@code jobTaskId}（= DB jobTaskId）归组；同一 taskId 可包含多次
 * startJob（重试/恢复）产生的日志。
 */
@DataBean
public class TaskLogEntry {
    private String jobTaskId;
    private long logTime;
    private String logLevel;
    private String logMessage;
    private Map<String, Object> logPayload;

    public String getJobTaskId() {
        return jobTaskId;
    }

    public void setJobTaskId(String jobTaskId) {
        this.jobTaskId = jobTaskId;
    }

    public long getLogTime() {
        return logTime;
    }

    public void setLogTime(long logTime) {
        this.logTime = logTime;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public void setLogMessage(String logMessage) {
        this.logMessage = logMessage;
    }

    public Map<String, Object> getLogPayload() {
        return logPayload;
    }

    public void setLogPayload(Map<String, Object> logPayload) {
        this.logPayload = logPayload;
    }
}
