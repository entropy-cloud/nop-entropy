/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock.impl;

import io.nop.commons.concurrent.lock.IResourceLockState;

import java.util.Collections;
import java.util.Set;

public class ResourceLock extends AbstractResourceLock {
    private final IResourceLockManagerImplementor lockManager;
    private final String resourceId;
    private final String holderId;

    private IResourceLockState lock;

    public ResourceLock(IResourceLockManagerImplementor lockManager, String resourceId, String holderId,
                        long defaultWaitTime, long defaultLeaseTime) {
        super(defaultWaitTime, defaultLeaseTime);
        this.lockManager = lockManager;
        this.resourceId = resourceId;
        this.holderId = holderId;
    }

    @Override
    public String getHolderId() {
        return holderId;
    }

    @Override
    public boolean isHoldingLock() {
        IResourceLockState lock = this.lock;
        if (lock == null)
            return false;
        return lockManager.isHoldingLock(lock);
    }

    public String getResourceId() {
        return resourceId;
    }

    @Override
    public Set<String> getResourceIds() {
        return Collections.singleton(resourceId);
    }

    @Override
    public boolean tryLockWithLease(long waitTime, long leaseTime) {
        this.lock = lockManager.tryLockWithLease(resourceId, holderId, waitTime, leaseTime);
        return this.lock != null;
    }

    @Override
    public boolean tryResetLease(long leaseTime) {
        return lockManager.tryResetLease(lock, leaseTime);
    }

    @Override
    public synchronized void unlock() {
        IResourceLockState lock = this.lock;
        if (lock != null) {
            this.lock = null;
            lockManager.releaseLock(lock);
        }
    }
}