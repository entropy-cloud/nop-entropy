/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ThreadHelper {

    public static void checkInterrupted(Throwable e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 从guava项目拷贝的代码 先使用shutdown, 停止接收新任务并尝试完成所有已存在任务.
     * <p>
     * 如果1/2超时时间后, 则调用shutdownNow,取消在workQueue中Pending的任务,并中断所有阻塞函数.
     * <p>
     * 如果1/2超时仍然超时，则强制退出.
     * <p>
     * 另对在shutdown时线程本身被调用中断做了处理.
     * <p>
     * 返回线程最后是否被中断.
     * <p>
     * Shuts down the given executor service gradually, first disabling new submissions and later, if necessary,
     * cancelling remaining tasks.
     *
     * <p>
     * The method takes the following steps:
     * <ol>
     * <li>calls {@link ExecutorService#shutdown()}, disabling acceptance of new submitted tasks.
     * <li>awaits executor service termination for half of the specified timeout.
     * <li>if the timeout expires, it calls {@link ExecutorService#shutdownNow()}, cancelling pending tasks and
     * interrupting running tasks.
     * <li>awaits executor service termination for the other half of the specified timeout.
     * </ol>
     *
     * <p>
     * If, at any step of the process, the calling thread is interrupted, the method calls
     * {@link ExecutorService#shutdownNow()} and returns.
     *
     * @param service the {@code ExecutorService} to shut down
     * @param timeout the maximum time to wait for the {@code ExecutorService} to terminate
     * @param unit    the time unit of the timeout argument
     * @return {@code true} if the {@code ExecutorService} was terminated successfully, {@code false} if the call timed
     * out or was interrupted
     */
    public static boolean shutdownAndAwaitTermination(ExecutorService service, long timeout, TimeUnit unit) {
        long halfTimeoutNanos = unit.toNanos(timeout) / 2;
        // Disable new tasks from being submitted
        service.shutdown();
        try {
            // Wait for half the duration of the timeout for existing tasks to
            // terminate
            if (!service.awaitTermination(halfTimeoutNanos, TimeUnit.NANOSECONDS)) {
                // Cancel currently executing tasks
                service.shutdownNow();
                // Wait the other half of the timeout for tasks to respond to
                // being cancelled
                service.awaitTermination(halfTimeoutNanos, TimeUnit.NANOSECONDS);
            }
        } catch (InterruptedException ie) {
            // Preserve interrupt status
            Thread.currentThread().interrupt();
            // (Re-)Cancel if current thread also interrupted
            service.shutdownNow();
        }
        return service.isTerminated();
    }

    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {  //NOPMD
        }
    }
}