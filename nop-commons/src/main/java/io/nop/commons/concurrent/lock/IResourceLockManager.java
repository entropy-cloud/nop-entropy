/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock;

import io.nop.api.core.context.ContextProvider;
import io.nop.commons.concurrent.lock.impl.ResourceMultiLock;
import jakarta.annotation.Nonnull;

import java.util.Set;
import java.util.function.Supplier;

/**
 * 分布式锁, 不可重入
 */
public interface IResourceLockManager {
    long getDefaultWaitTime();

    long getDefaultLeaseTime();

    IResourceLock getLock(@Nonnull String resourceId, String holderId);

    default IResourceLock getMultiLock(@Nonnull Set<String> resourceIds, String holderId) {
        return new ResourceMultiLock(this, resourceIds, holderId, getDefaultWaitTime(), getDefaultLeaseTime());
    }

    /**
     * 根据资源id获取当前的锁信息
     *
     * @return 返回null如果资源没有被锁定
     */
    IResourceLockState getLockState(@Nonnull String resourceId);

    boolean forceUnlock(String resourceId);

    default <T> T callWithLock(String resourceId, Supplier<T> task) {
        IResourceLock lock = getLock(resourceId, ContextProvider.currentUserId());
        try {
            lock.lock();
            return task.get();
        } finally {
            lock.unlock();
        }
    }

    default void runWithLock(String resourceId, Runnable task) {
        IResourceLock lock = getLock(resourceId, ContextProvider.currentUserId());
        try {
            lock.lock();
            task.run();
        } finally {
            lock.unlock();
        }
    }
}