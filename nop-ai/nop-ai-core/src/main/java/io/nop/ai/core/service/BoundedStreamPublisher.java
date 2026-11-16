/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.service;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.function.BiPredicate;

/**
 * 单订阅者有界流式 publisher：同步 onSubscribe + 异步投递（受管 executor 承载）。
 *
 * <p>替代 {@link java.util.concurrent.SubmissionPublisher}（best-effort 语义）消除
 * pre-onSubscribe 静默丢元素缺陷：SubmissionPublisher 的 onSubscribe 经 executor 异步
 * 投递，{@code subscribe()} 同步注册后与异步 onSubscribe 落地之间存在窗口——窗口内的
 * offer 元素被静默丢弃（满载下流式首包全丢、窗口内失败误分类重订阅的实测形态）。
 * 本实现改为在 {@code subscribe()} 内<b>同步</b>发出 onSubscribe（Flow 合法形态）——
 * 返回时下游的 request 已落地；offer 元素一律先入自有 FIFO 缓冲（订阅前后都不丢），
 * 经 executor 异步按 demand 投递。
 *
 * <p>投递线程为受管 executor（plan 2505 3.1-C3-001 ③ 裁定：流载体必须是平台受管池
 * 线程，不得是调用线程/commonPool）；offer 仅入缓冲 + 触发投递任务，读线程永不阻塞
 * （C3-001 慢下游 fail-loud 语义）；元素按 FIFO 受 demand 门控，缓冲满 = onDrop 语义
 * （调用方 fail-loud）；终态按入队序排在缓冲元素之后交付（缓冲空 + 已 demand 耗尽才
 * 出终态，对齐终态最后序契约）；取消后静默丢弃（Flow 规范 3.13）。
 *
 * <p>锁序：本监视器 → 消费者侧监视器（单向；teardownCurrentAttempt 的 token.cancel
 * 在其 monitor 外调用 closeExceptionally，无反向边）。
 */
final class BoundedStreamPublisher<T> implements Flow.Publisher<T>, Flow.Subscription {
    private final Executor executor;
    private final ArrayDeque<T> buffer; // guarded by this：已接收待投递元素（FIFO）
    private final int capacity;

    private Flow.Subscriber<? super T> subscriber; // guarded by this
    private boolean subscribed;                    // guarded by this
    private boolean cancelled;                     // guarded by this（下游 cancel）
    private long demand;                           // guarded by this（饱和累加）
    private boolean terminated;                    // guarded by this（error/completed 已置位）
    private Throwable error;                       // guarded by this
    private boolean completed;                     // guarded by this
    private boolean terminalTaken;                 // guarded by this（终态已被 drain 任务取走）
    private boolean delivering;                    // guarded by this（单飞投递守卫）

    BoundedStreamPublisher(Executor executor, int capacity) {
        this.executor = Objects.requireNonNull(executor);
        if (capacity <= 0)
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        this.capacity = capacity;
        this.buffer = new ArrayDeque<>();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> s) {
        Objects.requireNonNull(s);
        synchronized (this) {
            if (subscribed) {
                // 单订阅者契约（与 FailoverStreamFlow 同型）：第二订阅者显式失败，不静默吞
                s.onSubscribe(NOOP);
                s.onError(new IllegalStateException(
                        "BoundedStreamPublisher supports a single subscriber"));
                return;
            }
            subscribed = true;
            subscriber = s;
            // 同步 onSubscribe（Flow 合法形态）——缺陷修复核心：返回时下游 request 已落地，
            // 不存在"已注册但未就绪"的丢元素窗口
            s.onSubscribe(this);
        }
        scheduleDrain();
    }

    /**
     * 发布元素。一律先入自有 FIFO 缓冲（订阅前后都不丢），缓冲满 = onDrop 语义
     * （调用方 fail-loud，返回后同步检测）。仅入队 + 触发投递任务，读线程永不阻塞。
     *
     * @return 滞留深度估计（JDK offer 形态对齐，调用方未用）
     */
    int offer(T item, BiPredicate<Flow.Subscriber<? super T>, T> onDrop) {
        Objects.requireNonNull(item);
        boolean dropped;
        Flow.Subscriber<? super T> s;
        synchronized (this) {
            if (cancelled) {
                return 0; // 下游已取消：静默丢弃（Flow 规范 3.13）
            }
            if (buffer.size() >= capacity) {
                dropped = true;
                s = subscriber;
            } else {
                buffer.addLast(item);
                scheduleDrain();
                return buffer.size();
            }
        }
        if (dropped && onDrop != null) {
            onDrop.test(s, item);
        }
        return -1;
    }

    /** 正常完成：按入队序排在缓冲元素之后交付。 */
    void close() {
        synchronized (this) {
            if (cancelled || terminated)
                return;
            completed = true;
            terminated = true;
        }
        scheduleDrain();
    }

    /** 异常完成：按入队序排在缓冲元素之后交付。 */
    void closeExceptionally(Throwable t) {
        Objects.requireNonNull(t);
        synchronized (this) {
            if (cancelled || terminated)
                return;
            error = t;
            terminated = true;
        }
        scheduleDrain();
    }

    /** 有待投递面（缓冲元素或待出终态）时触发单飞投递任务（受管 executor 承载）。 */
    private void scheduleDrain() {
        synchronized (this) {
            if (!subscribed || delivering || cancelled)
                return;
            if (buffer.isEmpty() && (!terminated || terminalTaken))
                return;
            delivering = true;
        }
        try {
            executor.execute(this::drainTask);
        } catch (Throwable e) {
            // 提交失败：复位守卫，保留缓冲由后续 offer/request/close 重试
            synchronized (this) {
                delivering = false;
            }
        }
    }

    /** 投递任务（executor 线程）：持锁取快照 → 出锁投递 → 循环补取（与 flow drain 同型）。 */
    private void drainTask() {
        while (true) {
            List<T> batch;
            Throwable deliverError;
            boolean deliverComplete;
            synchronized (this) {
                if (cancelled) {
                    delivering = false;
                    return;
                }
                batch = new ArrayList<>();
                while (demand > 0 && !buffer.isEmpty()) {
                    demand--;
                    batch.add(buffer.pollFirst());
                }
                deliverError = null;
                deliverComplete = false;
                if (terminated && !terminalTaken) {
                    // 终态不需 demand（Flow 规则）。异常终态 = fail-loud abort，立即交付
                    // （缓冲残留元素不再交付——错误已显式终态化，且 demand=0 时等待会悬挂，
                    // 既有 C3-001 慢下游 fail-fast 契约）；成功终态仍等缓冲清空（不吞尾包）。
                    if (buffer.isEmpty() || error != null) {
                        terminalTaken = true;
                        deliverError = error;
                        deliverComplete = completed;
                    }
                }
                if (batch.isEmpty() && !terminalTaken) {
                    delivering = false;
                    return; // 无 demand 或无待投递面：等待下一次 offer/request/终态触发
                }
                if (terminalTaken) {
                    delivering = false;
                }
            }
            Flow.Subscriber<? super T> s = subscriber();
            if (s == null) {
                return;
            }
            for (T item : batch) {
                s.onNext(item);
            }
            if (deliverError != null) {
                s.onError(deliverError);
                return;
            }
            if (deliverComplete) {
                s.onComplete();
                return;
            }
        }
    }

    private Flow.Subscriber<? super T> subscriber() {
        synchronized (this) {
            return cancelled ? null : subscriber;
        }
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            // Reactive Streams 规范 3.9
            throw new IllegalArgumentException("request amount must be positive: " + n);
        }
        synchronized (this) {
            if (cancelled)
                return;
            demand = demand + n < 0 ? Long.MAX_VALUE : demand + n; // 饱和
        }
        scheduleDrain();
    }

    @Override
    public void cancel() {
        synchronized (this) {
            cancelled = true;
            buffer.clear();
            // 终态不再交付（Flow 规范：取消后 publisher 停止发送）
        }
    }

    private static final Flow.Subscription NOOP = new Flow.Subscription() {
        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {
        }
    };
}
