/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.time.IClock;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.lock.impl.LocalResourceLockManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLocalLockManager {
    IScheduledExecutor timer;

    LocalResourceLockManager lockManager;

    // R4-32（plan 2566）：单调时钟双针 stub——两个原真实 sleep 用例（C10-test-protection :370
    // 「时钟源不敏感」判定随换源改形）改 stub 推进，保护力保持（断言同一过期/续租语义）+ 回拨腿新增
    static class StubClock implements IClock {
        long wallMillis = 1_700_000_000_000L;
        long nanoTicks = 1_000_000_000L;

        void advanceNanos(long deltaNanos) {
            nanoTicks += deltaNanos;
        }

        void rollbackWall(long deltaMillis) {
            wallMillis -= deltaMillis;
        }

        @Override
        public long currentTimeMillis() {
            return wallMillis;
        }

        @Override
        public long nanoTime() {
            return nanoTicks;
        }

        @Override
        public LocalDate currentDate() {
            return LocalDate.now();
        }

        @Override
        public LocalDateTime currentDateTime() {
            return LocalDateTime.now();
        }
    }

    StubClock stubClock;

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
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
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
    public void testTryResetLeaseOnExpired() {
        // R4-32 改形：真实 sleep 250ms → stub 单调推进 250ms（语义不变：租约过期续租失败）
        stubClock = new StubClock();
        CoreMetrics.registerClock(stubClock);
        IResourceLockState state = lockManager.tryLockWithLease("reset-b", "h1", 0, 100, null);
        assertTrue(state != null);
        stubClock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(250));

        // 租约已过期，续租应失败
        assertTrue(!lockManager.tryResetLease(state, 10000));
        assertTrue(lockManager.getLockState("reset-b") == null);
    }

    @Test
    public void testIsHoldingLockOnExpired() {
        // R4-32 改形：真实 sleep 250ms → stub 单调推进；并新增墙钟回拨腿（回拨不使过期租约复活）
        stubClock = new StubClock();
        CoreMetrics.registerClock(stubClock);
        IResourceLockState state = lockManager.tryLockWithLease("hold-b", "h1", 0, 100, null);
        assertTrue(state != null);
        stubClock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(250));

        // 已过期的锁不应被报告为持有
        assertTrue(!lockManager.isHoldingLock(state));
        assertTrue(lockManager.getLockState("hold-b") == null);

        // 回拨腿新增：墙钟回拨不能复活已过期的租约——回拨后重新获取租约，
        // 其有效期仍按单调流逝正常过期（不受回拨影响）
        stubClock.rollbackWall(3_600_000);
        IResourceLockState state2 = lockManager.tryLockWithLease("hold-b", "h2", 0, 100, null);
        assertTrue(state2 != null);
        assertTrue(lockManager.isHoldingLock(state2));
        stubClock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(150));
        assertTrue(!lockManager.isHoldingLock(state2));
    }

    @Test
    public void testResourceLockTryResetLeaseWithoutLock() {
        IResourceLock lock = lockManager.getLock("no-lock", "h1");
        // 未获取锁时调用tryResetLease应返回false，而不是NPE
        assertTrue(!lock.tryResetLease(1000));
    }
}
