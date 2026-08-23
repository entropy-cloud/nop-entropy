/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.context;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestContextTaskQueue {

    /**
     * 复现flush退出窗口的check-then-act竞态：处理线程A最后一次poll返回null之后、清理processingThread之前，
     * 线程B enqueue并调用flush，会因processingThread仍为A而放弃执行；A已跳出循环不会再poll，任务滞留队列。
     * <p>
     * 通过反射获取内部锁，在A进入退出临界区前冻结锁，把纳秒级窗口放大为确定性步骤。
     */
    @Test
    public void testFlushExitWindowDoesNotLoseTask() throws Exception {
        ContextTaskQueue queue = new ContextTaskQueue();
        ReentrantLock lock = internalLock(queue);

        CountDownLatch t1Started = new CountDownLatch(1);
        CountDownLatch releaseT1 = new CountDownLatch(1);
        CountDownLatch t2Done = new CountDownLatch(1);
        AtomicBoolean t2Run = new AtomicBoolean(false);

        // t1 会阻塞，用于让flush线程A停留在处理循环内
        queue.enqueue(() -> {
            t1Started.countDown();
            try {
                releaseT1.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        // tMarker 在t1之后执行；tMarker已运行即证明A的后续poll只能返回null（t2尚未入队）
        AtomicBoolean markerRun = new AtomicBoolean(false);
        queue.enqueue(() -> markerRun.set(true));

        Thread flusher = new Thread(queue::flush);
        flusher.setDaemon(true);
        flusher.start();
        assertTrue(t1Started.await(5, TimeUnit.SECONDS), "t1 should start running");

        // 持有内部锁：A在完成t1、执行tMarker、最后一次poll到null之后，
        // 将阻塞在退出临界区（endProcess/endProcessIfIdle的lock.lock()）上
        lock.lock();
        try {
            releaseT1.countDown();
            // 等待A跑到退出临界区（状态WAITING且marker已执行）
            awaitFlusherParked(flusher, markerRun);

            // B视角：enqueue返回false（无sync等待者），调用方需自行flush
            assertFalse(queue.enqueue(() -> {
                t2Run.set(true);
                t2Done.countDown();
            }), "enqueue should return false when no syncing waiter");

            // B的flush：A仍占用processingThread，直接放弃。
            // 修复前：A随后也会直接退出，t2滞留队列永不执行
            queue.flush();
        } finally {
            lock.unlock();
        }

        // 修复后：A在退出判定中看到非空队列，继续作为处理线程执行t2
        assertTrue(t2Done.await(5, TimeUnit.SECONDS),
                "task enqueued during flush exit window must be executed, run=" + t2Run.get());
    }

    private static void awaitFlusherParked(Thread flusher, AtomicBoolean markerRun) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!(markerRun.get() && flusher.getState() == Thread.State.WAITING)) {
            if (System.nanoTime() > deadline)
                throw new IllegalStateException("flusher not parked at exit critical section, state="
                        + flusher.getState() + ", markerRun=" + markerRun.get());
            // noinspection BusyWait
            Thread.sleep(5);
        }
    }

    private static ReentrantLock internalLock(ContextTaskQueue queue) throws Exception {
        Field field = ContextTaskQueue.class.getDeclaredField("lock");
        field.setAccessible(true);
        return (ReentrantLock) field.get(queue);
    }

    @Test
    public void testFlushProcessesEnqueuedTasks() throws Exception {
        ContextTaskQueue queue = new ContextTaskQueue();
        CountDownLatch done = new CountDownLatch(2);
        queue.enqueue(done::countDown);
        queue.enqueue(done::countDown);
        // 无其他处理线程时enqueue返回false，调用方flush负责执行
        queue.flush();
        assertTrue(done.await(1, TimeUnit.SECONDS));
    }
}
