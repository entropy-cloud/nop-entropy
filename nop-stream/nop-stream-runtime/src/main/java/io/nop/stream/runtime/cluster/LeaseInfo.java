/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class LeaseInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String nodeId;
    private long leaseStartAt;
    private long leaseExpireAt;
    private boolean active;

    public LeaseInfo() {
    }

    public LeaseInfo(String nodeId, long leaseStartAt, long leaseExpireAt, boolean active) {
        this.nodeId = nodeId;
        this.leaseStartAt = leaseStartAt;
        this.leaseExpireAt = leaseExpireAt;
        this.active = active;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public long getLeaseStartAt() {
        return leaseStartAt;
    }

    public void setLeaseStartAt(long leaseStartAt) {
        this.leaseStartAt = leaseStartAt;
    }

    public long getLeaseExpireAt() {
        return leaseExpireAt;
    }

    public void setLeaseExpireAt(long leaseExpireAt) {
        this.leaseExpireAt = leaseExpireAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
