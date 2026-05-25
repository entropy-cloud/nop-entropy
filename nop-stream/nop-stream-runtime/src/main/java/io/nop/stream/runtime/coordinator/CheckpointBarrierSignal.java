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

import io.nop.stream.core.checkpoint.CheckpointBarrier;

/**
 * Control message sent from JobCoordinator to TaskManagers to trigger a checkpoint.
 *
 * <p>Carries the fencing token so that TaskManagers can reject stale signals.
 */
@DataBean
public class CheckpointBarrierSignal implements Serializable {

    private static final long serialVersionUID = 1L;

    private CheckpointBarrier barrier;
    private String fencingToken;
    private String jobId;

    public CheckpointBarrierSignal() {
    }

    public CheckpointBarrierSignal(CheckpointBarrier barrier, String fencingToken, String jobId) {
        this.barrier = barrier;
        this.fencingToken = fencingToken;
        this.jobId = jobId;
    }

    public CheckpointBarrier getBarrier() {
        return barrier;
    }

    public void setBarrier(CheckpointBarrier barrier) {
        this.barrier = barrier;
    }

    public String getFencingToken() {
        return fencingToken;
    }

    public void setFencingToken(String fencingToken) {
        this.fencingToken = fencingToken;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}
