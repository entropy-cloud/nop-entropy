package io.nop.job.api.alarm;

public class JobAlarmEvent {
    private final String jobFireId;
    private final String jobScheduleId;
    private final String jobName;
    private final String namespaceId;
    private final String groupId;
    private final String errorCode;
    private final String errorMessage;
    private final Long durationMs;

    public JobAlarmEvent(String jobFireId, String jobScheduleId, String jobName,
                         String namespaceId, String groupId, String errorCode,
                         String errorMessage, Long durationMs) {
        this.jobFireId = jobFireId;
        this.jobScheduleId = jobScheduleId;
        this.jobName = jobName;
        this.namespaceId = namespaceId;
        this.groupId = groupId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
    }

    public String getJobFireId() { return jobFireId; }
    public String getJobScheduleId() { return jobScheduleId; }
    public String getJobName() { return jobName; }
    public String getNamespaceId() { return namespaceId; }
    public String getGroupId() { return groupId; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Long getDurationMs() { return durationMs; }
}
