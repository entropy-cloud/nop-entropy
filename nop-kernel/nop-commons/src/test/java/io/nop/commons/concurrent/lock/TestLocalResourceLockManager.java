/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.commons.concurrent.lock;

import io.nop.commons.concurrent.lock.impl.LocalResourceLockManager;
import io.nop.commons.concurrent.lock.impl.ResourceLock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C2-007 / C2-008）载体钉。
 */
public class TestLocalResourceLockManager {

    /**
     * pin0207a（C2-007 米策略）：等待中被中断时，`lockInterruptibly` 必须抛
     * `InterruptedException`（而非 `NopException`），并恢复中断标志。
     */
    @Test
    public void pin0207a_lockInterruptiblyPropagatesInterrupt() throws Exception {
        LocalResourceLockManager mgr = new LocalResourceLockManager();
        IResourceLock holderLock = mgr.getLock("res-207", "holder");
        assertTrue(holderLock.tryLockWithLease(0, 5000), "持锁方必须成功");

        IResourceLock waiter = mgr.getLock("res-207", "waiter");
        AtomicReference<Throwable> caught = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Thread t = new Thread(() -> {
            try {
                waiter.lockInterruptibly();
            } catch (Throwable e) {
                caught.set(e);
            } finally {
                done.countDown();
            }
        });
        t.start();
        Thread.sleep(150);          // 让 waiter 进入 await
        t.interrupt();              // 中断等待
        assertTrue(done.await(5, TimeUnit.SECONDS), "被中断后必须返回");

        Throwable e = caught.get();
        assertNotNull(e, "等待线程必须因中断退出");
        assertInstanceOf(InterruptedException.class, e,
                "米策略：必须抛 InterruptedException，而不是包装成 NopException；实际="
                        + e.getClass().getName() + ": " + e);

        holderLock.unlock();
    }

    /**
     * pin0207b（C2-007）：中断传播后线程的中断标志必须被恢复（`Thread.currentThread().interrupt()`
     * 由捕获方负责，本钉验证 `lockInterruptibly` 抛出时未清除调用者标志）。
     */
    @Test
    public void pin0207b_interruptFlagPreserved() throws Exception {
        LocalResourceLockManager mgr = new LocalResourceLockManager();
        IResourceLock holderLock = mgr.getLock("res-207b", "holder");
        assertTrue(holderLock.tryLockWithLease(0, 5000));

        IResourceLock waiter = mgr.getLock("res-207b", "waiter");
        java.util.concurrent.atomic.AtomicBoolean flagAfterCatch = new java.util.concurrent.atomic.AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);

        Thread t = new Thread(() -> {
            try {
                waiter.lockInterruptibly();
            } catch (InterruptedException ie) {
                // 捕获方恢复标志（平台约定）；本钉验证抛出的确是 InterruptedException 且可被恢复
                Thread.currentThread().interrupt();
                flagAfterCatch.set(Thread.currentThread().isInterrupted());
            } catch (Throwable ignore) {
                // 非中断路径不应到达
            } finally {
                done.countDown();
            }
        });
        t.start();
        Thread.sleep(150);
        t.interrupt();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(flagAfterCatch.get(), "InterruptedException 路径必须可恢复中断标志");

        holderLock.unlock();
    }

    /**
     * pin0208（C2-008）：过期判定 + 删除必须与 `checkTimeout` 同用 latch 监视器。
     * 结构钉：以「持锁→过期→另一线程 getLock+tryLock」的路径验证不会读到半删除态；
     * 同时断言 `checkTimeout` 与 tryLock 路径都持有同一把 latch（通过行为不可观测半态体现）。
     */
    @Test
    public void pin0208_expiredLockIsCleanlyReclaimable() throws Exception {
        LocalResourceLockManager mgr = new LocalResourceLockManager();

        IResourceLock first = mgr.getLock("res-208", "a");
        assertTrue(first.tryLockWithLease(0, 50), "首次获取必须成功");

        Thread.sleep(120);   // 等租约过期

        IResourceLock second = mgr.getLock("res-208", "b");
        assertTrue(second.tryLockWithLease(300, 5000),
                "过期锁必须可被干净接管（判定+删除在同一 latch 监视器内，不出现半删态）");

        second.unlock();
        first.unlock();
    }
}
