/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api.alarm;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class JobAlarmEvent {
    private final String jobFireId;
    private final String jobScheduleId;
    private final String jobName;
    private final String namespaceId;
    private final String groupId;
    private final String errorCode;
    private final String errorMessage;
    private final Long durationMs;

    public JobAlarmEvent(@JsonProperty("jobFireId") String jobFireId,
                         @JsonProperty("jobScheduleId") String jobScheduleId,
                         @JsonProperty("jobName") String jobName,
                         @JsonProperty("namespaceId") String namespaceId,
                         @JsonProperty("groupId") String groupId,
                         @JsonProperty("errorCode") String errorCode,
                         @JsonProperty("errorMessage") String errorMessage,
                         @JsonProperty("durationMs") Long durationMs) {
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
