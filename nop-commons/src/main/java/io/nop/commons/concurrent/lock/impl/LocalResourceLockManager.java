/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.lock.impl;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.lock.IResourceLock;
import io.nop.commons.concurrent.lock.IResourceLockManager;
import io.nop.commons.concurrent.lock.IResourceLockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LocalResourceLockManager implements IResourceLockManager, IResourceLockManagerImplementor {
    static final Logger LOG = LoggerFactory.getLogger(LocalResourceLockManager.class);

    final ConcurrentMap<String, LocalResourceLockState> locks = new ConcurrentHashMap<>();

    private long defaultWaitTime = 1000; // 1s
    private long defaultLeaseTime = 10000; // 10s

    private int expireInterval = 1000; // 1s

    private volatile long lastCleanUpTime;

    private IScheduledExecutor timer;

    Future<?> schedulePromise;

    public void dump() {
        for (LocalResourceLockState lock : locks.values()) {
            LOG.info("nop.lock.dump:lock={}", lock);
        }
    }

    public boolean hasLock() {
        return !locks.isEmpty();
    }

    public void setCleanupTimer(IScheduledExecutor cleanupTimer) {
        this.timer = cleanupTimer;
    }

    public void setExpireInterval(int expireInterval) {
        this.expireInterval = expireInterval;
    }

    public long getDefaultWaitTime() {
        return defaultWaitTime;
    }

    public void setDefaultWaitTime(long defaultWaitTime) {
        this.defaultWaitTime = defaultWaitTime;
    }

    public long getDefaultLeaseTime() {
        return defaultLeaseTime;
    }

    public void setDefaultLeaseTime(long defaultLeaseTime) {
        this.defaultLeaseTime = defaultLeaseTime;
    }

    static class LocalResourceLockState extends ResourceLockState {
        private final CountDownLatch latch = new CountDownLatch(1);

        CountDownLatch getLatch() {
            return latch;
        }
    }

    LocalResourceLockState newLock(String resourceId, String lockerId) {
        LocalResourceLockState lock = new LocalResourceLockState();
        lock.setResourceId(resourceId);
        lock.setLockerId(lockerId);

        long now = CoreMetrics.currentTimeMillis();
        lock.setCreateTime(now);
        lock.setLockTime(now);
        return lock;
    }

    @PostConstruct
    public void init() {
        Guard.checkArgument(schedulePromise == null, "nop.lock.lock-manager-already-inited");
        if (timer == null)
            timer = GlobalExecutors.globalTimer();
        schedulePromise = timer.scheduleWithFixedDelay(this::checkTimeout, expireInterval, expireInterval,
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (schedulePromise != null) {
            schedulePromise.cancel(false);
            schedulePromise = null;
        }
    }

    public void checkTimeout() {
        long now = CoreMetrics.currentTimeMillis();
        LOG.debug("nop.lock.check-lease-expire:lastCleanupTime={}", new Timestamp(lastCleanUpTime));
        lastCleanUpTime = now;
        for (LocalResourceLockState lock : locks.values()) {
            if (lock.getExpireTime() <= now) {
                removeExpiredLock(lock);
            }
        }
    }

    void removeExpiredLock(LocalResourceLockState lock) {
        boolean b = locks.remove(lock.getResourceId(), lock);
        if (b) {
            LOG.info("nop.lock.remove-expired-lock:resourceId={},lock={}", lock.getResourceId(), lock);
            lock.getLatch().countDown();
        }
    }

    @Override
    public IResourceLockState getLockState(@Nonnull String resourceId) {
        return locks.get(resourceId);
    }

    @Override
    public IResourceLockState tryLockWithLease(String resourceId, String lockerId, long waitTime, long leaseTime) {
        long startTime = CoreMetrics.currentTimeMillis();

        LocalResourceLockState lock = this.newLock(resourceId, lockerId);

        do {
            long current = CoreMetrics.currentTimeMillis();
            lock.setLockTime(current);
            lock.setExpireTime(current + leaseTime);

            LocalResourceLockState existingLock = locks.putIfAbsent(resourceId, lock);
            if (existingLock == null) {
                LOG.debug("nop.lock.acquire-success:resourceId={},lock={}", lock.getResourceId(), lock);
                return lock;
            }

            long usedTime = current - startTime;
            if (waitTime <= 0 || waitTime <= usedTime) {
                LOG.debug("nop.lock.acquire-fail:resourceId={},usedTime={},lock={},existingLock={}", resourceId,
                        usedTime, lock, existingLock);
                return null;
            }

            if (existingLock.getExpireTime() < CoreMetrics.currentTimeMillis()) {
                removeExpiredLock(existingLock);
                continue;
            }

            LOG.debug("nop.lock.acquire-wait:resourceId={},lock={},existingLock={},wait={}", resourceId, lock,
                    existingLock, waitTime - usedTime);

            try {
                existingLock.getLatch().await(waitTime - usedTime, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        } while (true);
    }

    @Override
    public void releaseLock(IResourceLockState lock) {
        doReleaseLock((LocalResourceLockState) lock);
    }

    void doReleaseLock(LocalResourceLockState lock) {
        boolean b = locks.remove(lock.getResourceId(), lock);
        if (b) {
            LOG.debug("nop.lock.release-success:resourceId={},lock={}", lock.getResourceId(), lock);
            lock.getLatch().countDown();
        } else {
            LOG.info("nop.lock.release-fail:resourceId={},lock={}", lock.getResourceId(), lock);
        }
    }

    public void releaseLockForLocker(String lockerId) {
        for (ResourceLockState lock : locks.values()) {
            if (lock.getLockerId().equals(lockerId))
                releaseLock(lock);
        }
    }

    @Override
    public IResourceLock getLock(@Nonnull String resourceId, String holderId) {
        return new ResourceLock(this, resourceId, holderId, getDefaultWaitTime(), getDefaultLeaseTime());
    }

    @Override
    public boolean forceUnlock(String resourceId) {
        LocalResourceLockState lock = locks.remove(resourceId);
        if (lock != null) {
            LOG.info("nop.lock.force-unlock-success:resourceId={},lock={}", resourceId, lock);
            lock.getLatch().countDown();
        } else {
            LOG.debug("nop.lock.force-unlock-not-exists:resourceId={}", resourceId);
        }
        return lock != null;
    }

    @Override
    public boolean tryResetLease(IResourceLockState lock, long leaseTime) {
        LocalResourceLockState state = (LocalResourceLockState) lock;

        synchronized (state.getLatch()) {
            long current = CoreMetrics.currentTimeMillis();

            long expireTime = lock.getExpireTime();

            if (expireTime < current) {
                state.setExpireTime(current + leaseTime);
                state.setLockTime(current);

                if (locks.get(state.getResourceId()) == lock) {
                    LOG.debug("nop.lock.reset-lease-success:resourceId={},lock={}", lock.getResourceId(), lock);
                    return true;
                }
            } else {
                removeExpiredLock(state);
            }
        }
        LOG.debug("nop.lock.reset-lease-fail:resourceId={},lock={}", lock.getResourceId(), lock);
        return false;
    }

    @Override
    public boolean isHoldingLock(IResourceLockState lock) {
        LocalResourceLockState state = (LocalResourceLockState) lock;

        synchronized (state.getLatch()) {
            long current = CoreMetrics.currentTimeMillis();

            long expireTime = lock.getExpireTime();

            if (expireTime < current) {
                if (locks.get(state.getResourceId()) == lock) {
                    return true;
                }
            } else {
                removeExpiredLock(state);
            }
        }
        return false;
    }
}