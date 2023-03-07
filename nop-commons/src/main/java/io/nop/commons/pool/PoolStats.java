/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.pool;

import java.io.Serializable;

public class PoolStats implements Serializable {

    private static final long serialVersionUID = -2807626144795228544L;

    private long acquireCount;
    private long releaseCount;

    private long createCount;
    private long destroyCount;

    private int waitingCount;
    private int activeCount;
    private int creatingCount;

    private int idleCount;
    private long discardCount;

    private long createFailCount;
    private long acquireFailCount;

    private int activePeak;
    private long activePeakTime;

    /**
     * 从缓冲池中获取的次书名
     *
     * @return
     */
    public long getAcquireCount() {
        return acquireCount;
    }

    public void setAcquireCount(long acquireCount) {
        this.acquireCount = acquireCount;
    }

    /**
     * 使用完毕后放回缓冲池的次数
     *
     * @return
     */
    public long getReleaseCount() {
        return releaseCount;
    }

    public void setReleaseCount(long releaseCount) {
        this.releaseCount = releaseCount;
    }

    /**
     * 实际创建次数
     *
     * @return
     */
    public long getCreateCount() {
        return createCount;
    }

    public void setCreateCount(long createCount) {
        this.createCount = createCount;
    }

    /**
     * 实际销毁对象的次数
     */
    public long getDestroyCount() {
        return destroyCount;
    }

    public void setDestroyCount(long destroyCount) {
        this.destroyCount = destroyCount;
    }

    /**
     * 正在等待获取对象的线程数
     *
     * @return
     */
    public int getWaitingCount() {
        return waitingCount;
    }

    public void setWaitingCount(int waitingCount) {
        this.waitingCount = waitingCount;
    }

    /**
     * 从缓冲池获取到，正在被使用的对象数
     *
     * @return
     */
    public int getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(int activeCount) {
        this.activeCount = activeCount;
    }

    /**
     * 缓冲池中没有被使用的空闲对象数
     *
     * @return
     */
    public int getIdleCount() {
        return idleCount;
    }

    public void setIdleCount(int idleCount) {
        this.idleCount = idleCount;
    }

    /**
     * 因为状态不正确或者超时而销毁的次数
     *
     * @return
     */
    public long getDiscardCount() {
        return discardCount;
    }

    public void setDiscardCount(long discardCount) {
        this.discardCount = discardCount;
    }

    /**
     * 创建对象时失败的次数
     *
     * @return
     */
    public long getCreateFailCount() {
        return createFailCount;
    }

    public void setCreateFailCount(long createFailCount) {
        this.createFailCount = createFailCount;
    }

    public int getActivePeak() {
        return activePeak;
    }

    public void setActivePeak(int activePeak) {
        this.activePeak = activePeak;
    }

    public long getActivePeakTime() {
        return activePeakTime;
    }

    public void setActivePeakTime(long activePeakTime) {
        this.activePeakTime = activePeakTime;
    }

    /**
     * 获取对象失败的次数
     *
     * @return
     */
    public long getAcquireFailCount() {
        return acquireFailCount;
    }

    public void setAcquireFailCount(long acquireFailCount) {
        this.acquireFailCount = acquireFailCount;
    }

    public int getCreatingCount() {
        return creatingCount;
    }

    public void setCreatingCount(int creatingCount) {
        this.creatingCount = creatingCount;
    }
}