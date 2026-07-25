/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.coordinator;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

/**
 * G23: serializable job-status snapshot returned by
 * {@code IStreamCoordinatorRpcService.getJobStatus()}. Carries both the
 * {@link JobStatus} terminal/running state and the captured failure cause so a
 * remote caller (Stage 39) can render diagnostics without a second round-trip.
 *
 * <p>The {@code failureCause} is the {@code toString()} of the throwable captured
 * by {@code JobCoordinator.failJob(Throwable)}; it is {@code null} unless the job
 * has reached the {@link JobStatus#FAILED} terminal state.
 */
@DataBean
public class JobStatusResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private JobStatus jobStatus;
    private String failureCause;

    public JobStatusResponse() {
    }

    public JobStatusResponse(JobStatus jobStatus, String failureCause) {
        this.jobStatus = jobStatus;
        this.failureCause = failureCause;
    }

    public JobStatus getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    public String getFailureCause() {
        return failureCause;
    }

    public void setFailureCause(String failureCause) {
        this.failureCause = failureCause;
    }
}
