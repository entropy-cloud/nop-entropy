/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.lock.IResourceLock;
import jakarta.annotation.Nonnull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import static io.nop.commons.CommonErrors.ARG_HOLDER_ID;
import static io.nop.commons.CommonErrors.ARG_LEASE_TIME;
import static io.nop.commons.CommonErrors.ARG_RESOURCE_ID;
import static io.nop.commons.CommonErrors.ARG_RESOURCE_IDS;
import static io.nop.commons.CommonErrors.ARG_WAIT_TIME;
import static io.nop.commons.CommonErrors.ERR_LOCK_ACQUIRE_FAIL;
import static io.nop.commons.CommonErrors.ERR_LOCK_INVALID_LEASE_TIME;
import static io.nop.commons.CommonErrors.ERR_LOCK_INVALID_WAIT_TIME;

public abstract class AbstractResourceLock implements IResourceLock {

    private long defaultLeaseTime;
    private long defaultWaitTime;

    public AbstractResourceLock(long defaultWaitTime, long defaultLeaseTime) {
        this.defaultWaitTime = defaultWaitTime;
        this.defaultLeaseTime = defaultLeaseTime;
    }

    public String toString() {
        return getClass().getSimpleName() + "[resourceIds=" + getResourceIds() + ",holderId=" + getHolderId() + ","
                + "defaultLeaseTime=" + defaultLeaseTime + ",defaultWaitTime=" + defaultWaitTime + "]";
    }

    protected void checkParam(long waitTime, long leaseTime) {
        if (waitTime < 0)
            throw new NopException(ERR_LOCK_INVALID_WAIT_TIME).param(ARG_WAIT_TIME, waitTime);

        if (leaseTime < 0)
            throw new NopException(ERR_LOCK_INVALID_LEASE_TIME).param(ARG_LEASE_TIME, leaseTime);
    }

    @Override
    public void lock() {
        if (!tryLockWithLease(defaultWaitTime, defaultLeaseTime))
            throw new NopException(ERR_LOCK_ACQUIRE_FAIL).param(ARG_RESOURCE_ID, getResourceIds()).param(ARG_HOLDER_ID,
                    getHolderId());
    }

    /**
     * 1.7-C2-007（plan 2547 裁定 B，米策略）：{@code tryLockWithLease} 不抛受检异常（保持源兼容），
     * 但中断被 {@code LocalResourceLockManager} 转为 {@code NopException} 时保留了
     * {@code InterruptedException} 作为 cause。此处识别该 cause 并**重抛为 InterruptedException**，
     * 恢复 {@link java.util.concurrent.locks.Lock#lockInterruptibly()} 的可中断语义。
     */
    @Override
    public void lockInterruptibly() throws InterruptedException {
        try {
            lock();
        } catch (NopException e) {
            InterruptedException ie = extractInterrupt(e);
            if (ie != null) {
                Thread.currentThread().interrupt();
                throw ie;
            }
            throw e;
        }
    }

    @Override
    public boolean tryLock() {
        return tryLockWithLease(0, defaultLeaseTime);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        // 1.7-C2-007（米策略）：同 lockInterruptibly，识别中断 cause 并重抛
        try {
            return tryLockWithLease(unit.toMillis(time), defaultLeaseTime);
        } catch (NopException e) {
            InterruptedException ie = extractInterrupt(e);
            if (ie != null) {
                Thread.currentThread().interrupt();
                throw ie;
            }
            throw e;
        }
    }

    /**
     * 1.7-C2-007：从异常 cause 链中提取 {@link InterruptedException}（深入 3 层足够覆盖
     * {@code NopException.adapt(e)} 的单层包装）。
     */
    static InterruptedException extractInterrupt(Throwable err) {
        Throwable cur = err;
        for (int i = 0; i < 3 && cur != null; i++) {
            if (cur instanceof InterruptedException)
                return (InterruptedException) cur;
            cur = cur.getCause();
        }
        return null;
    }

    @Override
    public @Nonnull Condition newCondition() {
        throw new UnsupportedOperationException("Lock.newCondition");
    }
}