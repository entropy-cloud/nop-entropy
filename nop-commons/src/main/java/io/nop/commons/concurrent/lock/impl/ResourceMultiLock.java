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

import static io.nop.commons.CommonErrors.ARG_RESOURCE_IDS;
import static io.nop.commons.CommonErrors.ERR_LOCK_NOT_ALLOW_REENTRANT;

public class ResourceMultiLock extends AbstractResourceLock {
    private final IResourceLockManager lockManager;

    private final Set<String> resourceIds;
    private final String holderId;

    private List<IResourceLock> locks;

    public ResourceMultiLock(IResourceLockManager lockManager, Set<String> resourceIds, String holderId,
                             long defaultWaitTime, long defaultLeaseTime) {
        super(defaultWaitTime, defaultLeaseTime);
        this.lockManager = lockManager;
        this.resourceIds = resourceIds;
        this.holderId = holderId;
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

        String[] sortedIds = LockHelper.sortResourceIds(resourceIds);
        this.locks = new ArrayList<>(sortedIds.length);

        long startTime = CoreMetrics.currentTimeMillis();

        long remainWait = waitTime;

        boolean success = false;
        try {
            for (String resourceId : sortedIds) {
                IResourceLock lock = lockManager.getLock(resourceId, holderId);

                // 为了避免等待时间过程中锁失效，需要增加leaseTime
                if (!lock.tryLockWithLease(remainWait, leaseTime + remainWait)) {
                    return false;
                }

                // 假定tryLock失败，锁一定不会处于锁定状态。
                locks.add(lock);

                remainWait = waitTime - (CoreMetrics.currentTimeMillis() - startTime);
                if (remainWait <= 0) {
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