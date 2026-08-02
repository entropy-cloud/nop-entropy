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
 * <p>Carries the monotonic fencing epoch so that TaskManagers can reject stale signals.
 */
@DataBean
public class CheckpointBarrierSignal implements Serializable {

    private static final long serialVersionUID = 1L;

    private CheckpointBarrier barrier;
    private long fencingEpoch;
    private String jobId;

    public CheckpointBarrierSignal() {
    }

    public CheckpointBarrierSignal(CheckpointBarrier barrier, long fencingEpoch, String jobId) {
        this.barrier = barrier;
        this.fencingEpoch = fencingEpoch;
        this.jobId = jobId;
    }

    public CheckpointBarrier getBarrier() {
        return barrier;
    }

    public void setBarrier(CheckpointBarrier barrier) {
        this.barrier = barrier;
    }

    public long getFencingEpoch() {
        return fencingEpoch;
    }

    public void setFencingEpoch(long fencingEpoch) {
        this.fencingEpoch = fencingEpoch;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}
