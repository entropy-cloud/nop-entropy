/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.lock.impl.LocalResourceLockManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLocalLockManager {
    IScheduledExecutor timer;

    LocalResourceLockManager lockManager;

    @BeforeEach
    public void setUp() {
        lockManager = new LocalResourceLockManager();
        lockManager.setCleanupTimer(GlobalExecutors.globalTimer());
        lockManager.init();
    }

    @AfterEach
    public void tearDown() {
        if (lockManager != null)
            lockManager.destroy();
    }

    @Test
    public void testLock() {
        int n = 10;
        CountDownLatch latch = new CountDownLatch(n);

        CountDownLatch startLatch = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            final int index = i;
            final String holderId = "thread_" + i;
            new Thread() {
                public void run() {
                    startLatch.countDown();

                    try {
                        startLatch.await();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    IResourceLock lock = lockManager.getLock("a" + (index % 2), holderId);
                    for (int k = 0; k < 10; k++) {
                        try {
                            if (lock.tryLockWithLease(200L, 50)) {
                                try {
                                    Thread.sleep(100 + ThreadLocalRandom.current().nextInt(300));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                lock.unlock();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(50));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    latch.countDown();
                }
            }.start();
        }

        try {
            latch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }

        lockManager.dump();
        assertTrue(!lockManager.hasLock());
    }

    @Test
    public void testTryResetLease() {
        IResourceLockState state = lockManager.tryLockWithLease("reset-a", "h1", 100, 10000, null);
        assertTrue(state != null);

        // 租约仍然有效时续租应成功
        assertTrue(lockManager.tryResetLease(state, 20000));
        // 续租后锁仍然被持有
        assertTrue(lockManager.isHoldingLock(state));
        assertTrue(lockManager.getLockState("reset-a") == state);

        lockManager.releaseLock(state);
        assertTrue(!lockManager.hasLock());
    }

    @Test
    public void testTryResetLeaseOnExpired() throws InterruptedException {
        IResourceLockState state = lockManager.tryLockWithLease("reset-b", "h1", 100, 100, null);
        assertTrue(state != null);
        Thread.sleep(250);

        // 租约已过期，续租应失败
        assertTrue(!lockManager.tryResetLease(state, 10000));
        assertTrue(lockManager.getLockState("reset-b") == null);
    }

    @Test
    public void testIsHoldingLockOnExpired() throws InterruptedException {
        IResourceLockState state = lockManager.tryLockWithLease("hold-b", "h1", 100, 100, null);
        assertTrue(state != null);
        Thread.sleep(250);

        // 已过期的锁不应被报告为持有
        assertTrue(!lockManager.isHoldingLock(state));
        assertTrue(lockManager.getLockState("hold-b") == null);
    }

    @Test
    public void testResourceLockTryResetLeaseWithoutLock() {
        IResourceLock lock = lockManager.getLock("no-lock", "h1");
        // 未获取锁时调用tryResetLease应返回false，而不是NPE
        assertTrue(!lock.tryResetLease(1000));
    }
}
