/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class CoordinatorInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String jobId;
    private String coordinatorId;
    private String fencingToken;
    private long registeredAt;

    public CoordinatorInfo() {
    }

    public CoordinatorInfo(String jobId, String coordinatorId, String fencingToken, long registeredAt) {
        this.jobId = jobId;
        this.coordinatorId = coordinatorId;
        this.fencingToken = fencingToken;
        this.registeredAt = registeredAt;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getCoordinatorId() {
        return coordinatorId;
    }

    public void setCoordinatorId(String coordinatorId) {
        this.coordinatorId = coordinatorId;
    }

    public String getFencingToken() {
        return fencingToken;
    }

    public void setFencingToken(String fencingToken) {
        this.fencingToken = fencingToken;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(long registeredAt) {
        this.registeredAt = registeredAt;
    }
}
