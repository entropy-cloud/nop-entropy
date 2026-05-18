package io.nop.job.api.retry;

public class JobFireFailedEvent {
    private final String jobFireId;
    private final String jobScheduleId;
    private final String retryPolicyId;
    private final String namespaceId;
    private final String groupId;
    private final String jobName;
    private final String executorKind;
    private final String errorCode;
    private final String errorMessage;

    public JobFireFailedEvent(String jobFireId, String jobScheduleId, String retryPolicyId,
                              String namespaceId, String groupId, String jobName,
                              String executorKind, String errorCode, String errorMessage) {
        this.jobFireId = jobFireId;
        this.jobScheduleId = jobScheduleId;
        this.retryPolicyId = retryPolicyId;
        this.namespaceId = namespaceId;
        this.groupId = groupId;
        this.jobName = jobName;
        this.executorKind = executorKind;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getJobFireId() { return jobFireId; }
    public String getJobScheduleId() { return jobScheduleId; }
    public String getRetryPolicyId() { return retryPolicyId; }
    public String getNamespaceId() { return namespaceId; }
    public String getGroupId() { return groupId; }
    public String getJobName() { return jobName; }
    public String getExecutorKind() { return executorKind; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
