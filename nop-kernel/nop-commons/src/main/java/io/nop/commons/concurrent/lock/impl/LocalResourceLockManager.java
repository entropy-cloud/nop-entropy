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
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.lock.IResourceLock;
import io.nop.commons.concurrent.lock.IResourceLockManager;
import io.nop.commons.concurrent.lock.IResourceLockState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LocalResourceLockManager implements IResourceLockManager, IResourceLockManagerImplementor {
    static final Logger LOG = LoggerFactory.getLogger(LocalResourceLockManager.class);

    /*
     * R4-32（C7-004，plan 2566）：锁表内部全部时间域（createTime/lockTime/expireTime/lastCleanUpTime）
     * 按类内整域原子切换为单调时钟 CoreMetrics.nanoTime()（读写同源），消除墙钟回拨导致的租约误判
     * （回拨 1h 租约变长 1h）与前跳导致的批量误删。IResourceLockState.expireTime/getExpireTime()
     * 返回值语义随之变为「单调相对值（纳秒），仅与本进程 CoreMetrics.nanoTime() 比较」，不承载
     * epoch 语义，不得持久化或跨进程传输（本实现族内）。IResourceLockState 契约 javadoc 同步修订。
     */

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

        // R4-32（C7-004）：锁表内部时间戳统一使用单调时钟（nanoTime 纳秒），仅作类内相对比较与诊断展示
        long now = CoreMetrics.nanoTime();
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
        // R4-32（C7-004）：过期判定与锁表时间同源（单调时钟）；lastCleanUpTime 亦为单调值，
        // 日志直接输出原始值（不再包装 Timestamp——单调值非 epoch 语义[R1-m4/R2-m2]）
        long now = CoreMetrics.nanoTime();
        LOG.debug("nop.lock.check-lease-expire:lastCleanupTime={}", lastCleanUpTime);
        lastCleanUpTime = now;
        for (LocalResourceLockState lock : locks.values()) {
            // 与tryResetLease/isHoldingLock使用同一把锁，避免续租过程中读到旧的expireTime而误删锁
            synchronized (lock.getLatch()) {
                if (lock.getExpireTime() <= now) {
                    removeExpiredLock(lock);
                }
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
    public IResourceLockState tryLockWithLease(String resourceId, String lockerId, long waitTime, long leaseTime, String lockReason) {
        // R4-32（C7-004）：整域原子切换——读写同源（单调时钟）；waitTime/leaseTime 参数保持毫秒口径，
        // 类内统一换算为纳秒后与 nanoTime 比较[R2-m6 ms→ns 换算同则]
        long startTime = CoreMetrics.nanoTime();
        long waitNs = TimeUnit.MILLISECONDS.toNanos(waitTime);
        long leaseNs = TimeUnit.MILLISECONDS.toNanos(leaseTime);

        LocalResourceLockState lock = this.newLock(resourceId, lockerId);
        lock.setLockReason(lockReason);

        do {
            long current = CoreMetrics.nanoTime();
            lock.setLockTime(current);
            lock.setExpireTime(current + leaseNs);

            LocalResourceLockState existingLock = locks.putIfAbsent(resourceId, lock);
            if (existingLock == null) {
                LOG.debug("nop.lock.acquire-success:resourceId={},lock={}", lock.getResourceId(), lock);
                return lock;
            }

            long usedTime = current - startTime;
            if (waitNs <= 0 || waitNs <= usedTime) {
                LOG.debug("nop.lock.acquire-fail:resourceId={},usedTime={},lock={},existingLock={}", resourceId,
                        usedTime, lock, existingLock);
                return null;
            }

            // 1.7-C2-008（plan 2547 裁定 A）：过期判定 + 删除必须与 checkTimeout/续租使用同一把
            // latch 监视器——原实现绕过监视器直接读 expireTime 再删，与 tryResetLease 竞争时
            // 可能读到旧值误删刚续租的锁（违反同文件注释声明的不变式）。
            boolean expired;
            synchronized (existingLock.getLatch()) {
                expired = existingLock.getExpireTime() < CoreMetrics.nanoTime();
                if (expired)
                    removeExpiredLock(existingLock);
            }
            if (expired)
                continue;

            LOG.debug("nop.lock.acquire-wait:resourceId={},lock={},existingLock={},wait={}", resourceId, lock,
                    existingLock, TimeUnit.NANOSECONDS.toMillis(waitNs - usedTime));

            try {
                boolean b = existingLock.getLatch().await(waitNs - usedTime, TimeUnit.NANOSECONDS);
                if (!b) {
                    LOG.debug("nop.lock.await-timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw NopException.adapt(e);
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
            long current = CoreMetrics.nanoTime();

            long expireTime = lock.getExpireTime();

            if (expireTime > current) {
                // 租约仍然有效时才可以续租（R4-32：单调时钟同源，leaseTime 毫秒口径换算纳秒）
                state.setExpireTime(current + TimeUnit.MILLISECONDS.toNanos(leaseTime));
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
            long current = CoreMetrics.nanoTime();

            long expireTime = lock.getExpireTime();

            if (expireTime > current) {
                // 租约仍然有效且仍在锁表中时才报告为持有（R4-32：单调时钟同源判定）
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