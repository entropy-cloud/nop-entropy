/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.context;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ContextTaskQueue.syncGet drain→await 丢失唤醒窗回归（CRA R1-04，plan 2499 Phase 2，裁定④ C2-016。
 * 设计权威来源：{@code ai-dev/design/concurrency/bindcancel-context-unification.md} §3.4）。
 *
 * <p>修复选型：drain 段退出复用 flush 面已修的 endProcessIfIdle 原子形态（排空-复检-退出），
 * 与 flush 共用同一个「退出处理态」原语。C10-009 采纳同文件 flush 面的内部锁冻结手法
 * （反射取 ReentrantLock，把纳秒级窗口放大为确定性步骤）。
 */
public class TestContextTaskQueueSyncGetWindow {

    /**
     * C2-016 修复前红：syncGet 处理线程最后一次 poll 返回 null 之后、退出处理态之前，
     * 线程 B enqueue（返回 false）并调用 flush，会因 processingThread 仍为 A 而静默放弃；
     * A 随后无条件退出处理态并进入 await——任务滞留队列、无人 signal，syncGet 永久挂死。
     */
    @Test
    public void testSyncGetDrainToAwaitTransitionDoesNotStrandTask() throws Exception {
        ContextTaskQueue queue = new ContextTaskQueue();
        ReentrantLock lock = internalLock(queue);

        CompletableFuture<String> promise = new CompletableFuture<>();
        CountDownLatch t1Started = new CountDownLatch(1);
        CountDownLatch releaseT1 = new CountDownLatch(1);
        CountDownLatch waiterDone = new CountDownLatch(1);
        AtomicBoolean t2Run = new AtomicBoolean(false);

        // t1 阻塞，使 syncGet 等待线程停留在 drain 循环内
        queue.enqueue(() -> {
            t1Started.countDown();
            try {
                releaseT1.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        // tMarker 在 t1 之后执行；tMarker 已运行即证明 A 的后续 poll 只能返回 null（t2 尚未入队）
        AtomicBoolean markerRun = new AtomicBoolean(false);
        queue.enqueue(() -> markerRun.set(true));

        Thread waiter = new Thread(() -> {
            try {
                queue.syncGet(promise);
            } finally {
                waiterDone.countDown();
            }
        });
        waiter.setDaemon(true);
        waiter.start();
        assertTrue(t1Started.await(5, TimeUnit.SECONDS), "waiter must be running t1 inside the drain loop");

        // 持有内部锁：A 在执行 tMarker、最后一次 poll 到 null 之后，阻塞在退出临界区的 lock.lock() 上
        lock.lock();
        try {
            releaseT1.countDown();
            awaitWaiterParkedAtExit(waiter, markerRun);

            // B 视角：enqueue 返回 false（无 sync 等待者），调用方需自行 flush
            assertFalse(queue.enqueue(() -> {
                t2Run.set(true);
                promise.complete("t2-done");
            }), "enqueue should return false when no syncing waiter");

            // B 的 flush：A 仍占用 processingThread，静默放弃。
            // 修复前：A 随后无条件退出处理态并 await，t2 滞留队列、promise 永不完成 → syncGet 挂死
            queue.flush();
        } finally {
            lock.unlock();
        }

        assertTrue(waiterDone.await(5, TimeUnit.SECONDS),
                "syncGet must not hang: task enqueued in the drain-to-await window must be processed");
        assertTrue(t2Run.get(), "task enqueued during syncGet drain-to-await window must be executed");
    }

    /**
     * 不变式钉：enqueue 返回 true（sync 等待者已注册并被 signal）的场合，等待方必然被唤醒并
     * 处理该任务——修复不得破坏 signal 路径。
     */
    @Test
    public void testEnqueueReturningTrueWakesSyncWaiter() throws Exception {
        ContextTaskQueue queue = new ContextTaskQueue();
        ReentrantLock lock = internalLock(queue);

        CompletableFuture<String> promise = new CompletableFuture<>();
        CountDownLatch waiterDone = new CountDownLatch(1);
        AtomicBoolean taskRun = new AtomicBoolean(false);

        // 空 queue：syncGet 直接进入 await 段。先持锁挡住 waiter，使其必然走到 await 段入口
        Thread waiter = new Thread(() -> {
            try {
                queue.syncGet(promise);
            } finally {
                waiterDone.countDown();
            }
        });
        waiter.setDaemon(true);
        waiter.start();

        lock.lock();
        try {
            Thread.sleep(100);
        } finally {
            lock.unlock();
        }
        // 释放锁后 waiter 注册 syncing>0 并 await（enqueue 的返回值是注册事实的确定性观测）
        Thread.sleep(100);

        assertTrue(queue.enqueue(() -> {
            taskRun.set(true);
            promise.complete("ok");
        }), "enqueue must report true while a sync waiter is registered");

        assertTrue(waiterDone.await(5, TimeUnit.SECONDS), "sync waiter must be woken by the enqueue signal");
        assertTrue(taskRun.get(), "woken waiter must drain the enqueued task");
    }

    /**
     * 基本语义钉：syncGet 在 promise 完成时返回结果；先入队、后 syncGet 的任务在等待期间被 drain。
     */
    @Test
    public void testSyncGetDrainsQueuedTasksUntilPromiseCompletes() {
        ContextTaskQueue queue = new ContextTaskQueue();
        CompletableFuture<String> promise = new CompletableFuture<>();
        AtomicBoolean ran = new AtomicBoolean(false);

        queue.enqueue(() -> {
            ran.set(true);
            promise.complete("done");
        });

        assertEquals("done", queue.syncGet(promise));
        assertTrue(ran.get(), "queued task must be drained by the syncGet processing loop");
    }

    private static void awaitWaiterParkedAtExit(Thread waiter, AtomicBoolean markerRun)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!(markerRun.get() && waiter.getState() == Thread.State.WAITING)) {
            if (System.nanoTime() > deadline)
                throw new IllegalStateException("waiter not parked at exit critical section, state="
                        + waiter.getState() + ", markerRun=" + markerRun.get());
            // noinspection BusyWait
            Thread.sleep(5);
        }
    }

    private static ReentrantLock internalLock(ContextTaskQueue queue) throws Exception {
        Field field = ContextTaskQueue.class.getDeclaredField("lock");
        field.setAccessible(true);
        return (ReentrantLock) field.get(queue);
    }
}
