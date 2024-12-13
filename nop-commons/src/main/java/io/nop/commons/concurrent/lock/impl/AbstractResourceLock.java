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

    @Override
    public void lockInterruptibly() {
        lock();
    }

    @Override
    public boolean tryLock() {
        return tryLockWithLease(0, defaultLeaseTime);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) {
        return tryLockWithLease(unit.toMillis(time), defaultLeaseTime);
    }

    @Override
    public @Nonnull Condition newCondition() {
        throw new UnsupportedOperationException("Lock.newCondition");
    }
}