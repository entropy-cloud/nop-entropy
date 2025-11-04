/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock.impl;

import io.nop.commons.concurrent.lock.IResourceLockState;

import java.sql.Timestamp;

public class ResourceLockState implements IResourceLockState {
    private String resourceId;
    private String lockerId;

    private long createTime;
    private long lockTime;
    private long expireTime;
    private String lockReason;
    private int version;

    public ResourceLockState cloneInstance() {
        ResourceLockState state = new ResourceLockState();
        state.setResourceId(resourceId);
        state.setLockerId(lockerId);
        state.setCreateTime(createTime);
        state.setLockTime(lockTime);
        state.setExpireTime(expireTime);
        state.setLockReason(lockReason);
        return state;
    }

    @Override
    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public String getLockReason() {
        return lockReason;
    }

    public void setLockReason(String lockReason) {
        this.lockReason = lockReason;
    }

    @Override
    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    @Override
    public String getLockerId() {
        return lockerId;
    }

    public void setLockerId(String lockerId) {
        this.lockerId = lockerId;
    }

    @Override
    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    @Override
    public long getLockTime() {
        return lockTime;
    }

    public void setLockTime(long lockTime) {
        this.lockTime = lockTime;
    }

    @Override
    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ResourceLockState[resourceId=").append(resourceId).append(",lockerId=").append(lockerId)
                .append(",lockTime=").append(new Timestamp(lockTime)).append(",createTime=")
                .append(new Timestamp(createTime)).append(",expireTime=").append(new Timestamp(expireTime)).append("]");
        return sb.toString();
    }
}