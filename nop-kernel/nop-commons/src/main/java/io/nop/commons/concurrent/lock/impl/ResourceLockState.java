/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock.impl;

import io.nop.commons.concurrent.lock.IResourceLockState;

public class ResourceLockState implements IResourceLockState {
    private String resourceId;
    private String lockerId;

    private long createTime;
    // lockTime/expireTime会被后台清理线程与持锁线程并发读写，volatile保证可见性
    private volatile long lockTime;
    private volatile long expireTime;
    private String lockReason;
    private long version;

    // plan 2571：墙钟锚点对（与单调时间域同点相邻捕获）。投影 wall ≈ anchorWall + (value-anchorNano)/1e6
    // 仅服务诊断可观测性（toString/日志），租约判定域保持单调比较不变（R4-32 契约）。0=未设锚点。
    private long anchorNano;
    private long anchorWall;

    public ResourceLockState cloneInstance() {
        ResourceLockState state = new ResourceLockState();
        state.setResourceId(resourceId);
        state.setLockerId(lockerId);
        state.setCreateTime(createTime);
        state.setLockTime(lockTime);
        state.setExpireTime(expireTime);
        state.setLockReason(lockReason);
        state.setWallAnchor(anchorNano, anchorWall);
        return state;
    }

    /**
     * plan 2571：登记墙钟锚点对（单调纳秒与墙钟毫秒同点相邻捕获）。任一为 0 视为未设锚点。
     */
    public void setWallAnchor(long anchorNano, long anchorWall) {
        this.anchorNano = anchorNano;
        this.anchorWall = anchorWall;
    }

    /**
     * plan 2571：把单调纳秒值投影为墙钟毫秒（仅诊断展示用，非 epoch 判定语义）。
     * 未设锚点返回 -1。投影精度受锚点捕获间隙限制（微秒级），且墙钟中途被校准时会产生漂移——
     * 结果必须按「约」解读，租约判定不得使用本值。
     */
    public long wallMillisOf(long nanoValue) {
        if (anchorNano == 0 || anchorWall == 0)
            return -1L;
        return anchorWall + (nanoValue - anchorNano) / 1_000_000L;
    }

    @Override
    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
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
        // R4-32（C7-004[R1-m4/R2-m2]）：时间域随 LocalResourceLockManager 换源为单调时钟（纳秒相对值），
        // 非 epoch 语义，不再包装 Timestamp 展示，直接输出原始值。
        // plan 2571：补充锚点投影墙钟（wall=...），使记录可回答「对应墙钟哪个时刻」；投影仅诊断用
        sb.append("ResourceLockState[resourceId=").append(resourceId).append(",lockerId=").append(lockerId)
                .append(",lockTime=").append(lockTime).append(",createTime=")
                .append(createTime).append(",expireTime=").append(expireTime);
        long createWall = wallMillisOf(createTime);
        if (createWall > 0)
            sb.append(",wall=").append(java.time.Instant.ofEpochMilli(createWall));
        sb.append("]");
        return sb.toString();
    }
}