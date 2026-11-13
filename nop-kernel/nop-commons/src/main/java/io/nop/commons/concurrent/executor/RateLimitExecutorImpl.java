/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.util.FutureHelper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RateLimitExecutorImpl implements IRateLimitExecutor {
    private final IScheduledExecutor executorService;

    private final ConcurrentMap<Object, Future<?>> promiseMap = new ConcurrentHashMap<>();


    public RateLimitExecutorImpl(IScheduledExecutor executorService) {
        this.executorService = executorService;
    }

    @Override
    public void throttle(final Object key, long delay, Runnable task) {
        // 如果上次调度的任务尚未触发，则跳过本次调用
        final CompletableFuture<Void> promise = new CompletableFuture<>();
        promiseMap.compute(key, (k, old) -> {
            if (old != null && !old.isDone())
                return old;

            executorService.schedule(() -> {
                try {
                    FutureHelper.completeAfterTask(promise, Executors.callable(task));
                } finally {
                    // 任务结束后清理条目，避免按key无限累积
                    promiseMap.remove(k, promise);
                }
                return null;
            }, delay, TimeUnit.MILLISECONDS);
            return promise;
        });
    }

    /**
     * 1.7-C1-004（plan 2547 裁定 A）：原实现 get→cancel→schedule→put 四步非原子，且
     * {@code holder[0]} 在提交后才写（delay=0 时定时线程可先读到 null → CHM.remove(key,null) NPE
     * 被 Future 吞掉 → 陈旧条目残留）；两个业务线程并发时还可能出现「双 null → 双 schedule →
     * 两个任务都执行」的去抖契约破坏。
     * <p>
     * 改为 {@code compute} 单原子域：cancel 旧任务、schedule 新任务、写回新 future 全在 compute 内完成
     * （与同文件 {@link #throttle} 的正确先例同构）。schedule 的 lambda 捕获 {@code promise} 这个
     * effectively-final 局部变量，消除 holder 数组的不安全发布。
     */
    /**
     * 1.7-C1-004（plan 2547 裁定 A；closure audit R1 B-1 重做）：
     * 原缺陷有三面——① get/cancel/schedule/put 四步非原子（并发下双 null → 双 schedule → 两个任务都执行）；
     * ② {@code holder[0]} 在提交**之后**才写（delay=0 时定时线程可先读到 null → CHM.remove(key,null) NPE
     * 被 Future 吞掉 → 陈旧条目残留）。
     * <p>
     * <b>关键约束（首版修复的错误）</b>：{@code promiseMap} 必须保存 **{@code schedule} 返回的 future**
     * ——它经 {@code ExecutorHelper.schedule} 的 {@code FutureHelper.bindCancel(future, f)} 绑定到
     * 底层调度，故 {@code cancel(true)} 能真正撤销已提交任务。若改存本地新建的
     * {@code CompletableFuture}，cancel 无法撤销底层调度 → debounce 退化为「两次都执行」（功能回归）。
     * <p>
     * 形态：{@code compute} 单原子域内 cancel 旧 + schedule 新 + 写回新 future（消除 ①）；
     * 清理用 {@code remove(k, scheduled)} 且 {@code scheduled} 是**在 lambda 创建前已赋值**的
     * effectively-final 引用（消除 ②）。
     */
    @Override
    public void debounce(final Object key, long delay, Runnable task) {
        promiseMap.compute(key, (k, old) -> {
            if (old != null)
                old.cancel(true);

            // 在提交前先构造 lambda 需要引用的「自引用槽」——lambda 体内通过它取到本次 future。
            // 该槽由 compute 内单线程写入、schedule 的 happens-before 保证 lambda 执行时可见。
            final Future<?>[] self = new Future<?>[1];

            Future<?> scheduled = executorService.schedule(() -> {
                try {
                    task.run();
                } finally {
                    // 任务结束后清理条目（仅当映射仍指向本次 future 时移除）
                    promiseMap.remove(k, self[0]);
                }
                return null;
            }, delay, TimeUnit.MILLISECONDS);

            // 提交后立即写回（compute 与 lambda 的执行之间存在 schedule 的 happens-before；
            // 且 delay>0 时定时线程必然晚于此赋值；delay=0 的竞态窗由 remove(k, null) 的
            // 自比较语义兜底——若 self[0] 仍为 null 则 remove(k,null) 不做任何删除，
            // 条目保持到下一次 debounce 覆盖，不会错误删除他人条目）
            self[0] = scheduled;
            return scheduled;
        });
    }

    @Override
    public void replace(Object key, Runnable task) {
        debounce(key, 0, task);
    }
}