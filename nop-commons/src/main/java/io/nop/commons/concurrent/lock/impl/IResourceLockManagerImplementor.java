/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.lock.impl;

import io.nop.commons.concurrent.lock.IResourceLockState;

public interface IResourceLockManagerImplementor {

    IResourceLockState tryLockWithLease(String resourceId, String lockerId, long waitTime, long leaseTime);

    boolean tryResetLease(IResourceLockState lock, long leaseTime);

    boolean isHoldingLock(IResourceLockState lock);

    /**
     * 释放指定资源上的锁
     *
     * @param lock 待释放的锁
     */
    void releaseLock(IResourceLockState lock);
}