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
    /**
     * 单调 fencing epoch（Stage 39：取代原复合 String fencingToken）。
     * {@code JdbcClusterRegistry} 在持久化边界以 {@code String.valueOf(long)} 单值写入
     * 既有 {@code fencing_token VARCHAR(255)} 列（Decision 2 Option B：不迁移 DDL）。
     */
    private long fencingEpoch;
    private long registeredAt;

    public CoordinatorInfo() {
    }

    public CoordinatorInfo(String jobId, String coordinatorId, long fencingEpoch, long registeredAt) {
        this.jobId = jobId;
        this.coordinatorId = coordinatorId;
        this.fencingEpoch = fencingEpoch;
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

    public long getFencingEpoch() {
        return fencingEpoch;
    }

    public void setFencingEpoch(long fencingEpoch) {
        this.fencingEpoch = fencingEpoch;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(long registeredAt) {
        this.registeredAt = registeredAt;
    }
}
