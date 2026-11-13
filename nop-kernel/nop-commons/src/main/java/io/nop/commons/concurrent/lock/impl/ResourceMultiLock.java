/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.concurrent.lock.IResourceLock;
import io.nop.commons.concurrent.lock.IResourceLockManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.nop.commons.CommonErrors.ARG_RESOURCE_IDS;
import static io.nop.commons.CommonErrors.ERR_LOCK_NOT_ALLOW_REENTRANT;

public class ResourceMultiLock extends AbstractResourceLock {
    private final IResourceLockManager lockManager;

    private final Set<String> resourceIds;
    private final String holderId;

    private List<IResourceLock> locks;
    private String lockReason;

    public ResourceMultiLock(IResourceLockManager lockManager, Set<String> resourceIds, String holderId,
                             long defaultWaitTime, long defaultLeaseTime) {
        super(defaultWaitTime, defaultLeaseTime);
        this.lockManager = lockManager;
        this.resourceIds = Set.of(LockHelper.sortResourceIds(resourceIds));
        this.holderId = holderId;
    }

    @Override
    public String getLockReason() {
        return lockReason;
    }

    @Override
    public void setLockReason(String lockReason) {
        this.lockReason = lockReason;
    }

    public String getHolderId() {
        return holderId;
    }

    public Set<String> getResourceIds() {
        return resourceIds;
    }

    @Override
    public boolean isHoldingLock() {
        if (locks == null)
            return false;

        for (IResourceLock lock : locks) {
            if (!lock.isHoldingLock())
                return false;
        }
        return true;
    }

    @Override
    public boolean tryResetLease(long leaseTime) {
        if (locks == null) {
            return false;
        }

        for (IResourceLock lock : locks) {
            if (!lock.tryResetLease(leaseTime))
                return false;
        }
        return true;
    }

    @Override
    public void unlock() {
        List<IResourceLock> locks = this.locks;
        if (locks != null) {
            this.locks = null;
            LockHelper.unlockAll(locks);
        }
    }

    @Override
    public boolean tryLockWithLease(long waitTime, long leaseTime) {
        checkParam(waitTime, leaseTime);
        if (locks != null && !locks.isEmpty())
            throw new NopException(ERR_LOCK_NOT_ALLOW_REENTRANT).param(ARG_RESOURCE_IDS, resourceIds);

        this.locks = new ArrayList<>(resourceIds.size());

        // R4-32（C7-004）：整域原子切换单调时钟；等待预算在类内统一换算纳秒再与 nanoTime 差值比较
        long startTime = CoreMetrics.nanoTime();

        long remainWaitNs = TimeUnit.MILLISECONDS.toNanos(waitTime);

        boolean success = false;
        try {
            for (String resourceId : resourceIds) {
                IResourceLock lock = lockManager.getLock(resourceId, holderId);

                // 为了避免等待时间过程中锁失效，需要增加leaseTime（换回毫秒口径传参）
                long remainWait = TimeUnit.NANOSECONDS.toMillis(remainWaitNs);
                if (!lock.tryLockWithLease(remainWait, leaseTime + remainWait)) {
                    return false;
                }

                // 假定tryLock失败，锁一定不会处于锁定状态。
                locks.add(lock);

                remainWaitNs = TimeUnit.MILLISECONDS.toNanos(waitTime) - (CoreMetrics.nanoTime() - startTime);
                if (remainWaitNs <= 0) {
                    return false;
                }
            }
            success = true;
        } finally {
            if (!success)
                unlock();
        }
        return success;
    }
}