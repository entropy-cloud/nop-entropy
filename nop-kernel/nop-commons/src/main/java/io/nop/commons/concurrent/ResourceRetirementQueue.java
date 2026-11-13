/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_RETIREMENT_GRACE_PERIOD;

/**
 * 退役代际平台机制的唯一载体（plan 2504 裁定①③）：旧代资源「发布新代 + 延迟退役关闭」统一形态。
 *
 * <p>三站消费（RoundRobinSupplier 收缩窗 / DefaultVirtualFileSystem zip 句柄在飞读者窗 /
 * ResourceCacheEntry 失效销毁窗）共享同一 retire/drain/grace 契约与排空调度缝：
 * <ul>
 * <li>{@link #retire}：登记退役动作，deadline = 当前时间 + 宽限期；同窗多笔退役合并为一次排空调度；</li>
 * <li>{@link #drainDue}：deadline 已到的退役动作按 FIFO 依序执行（关闭/销毁动作唯一执行位）；</li>
 * <li>宽限期配置键 {@code nop.commons.concurrent.retirement.grace-period}（默认 5s）；
 * <b>≤0 = 关闭档</b>：retire 退化为同步立即执行 closeAction（= 修复前语义，确定性测试缝与显式回退通道）；</li>
 * <li>时间锚（F4）：deadline 计算唯一经 {@link CoreMetrics#currentTimeMillis}，不引入可注入时钟；
 * 确定性来自 grace 可配置 + 排空调度器可注入（测试经 {@link #drainAllNow()} 手动触发，零 sleep）。</li>
 * </ul>
 *
 * <p>fail-safe：排空调度不可达（调度器已销毁/拒绝）时退化为同步立即关闭 + debug 日志，
 * 无静默泄漏。队列无界性：退役速率上界 = 替换点触发频率（resize/refresh/失效为低频运维动作），
 * 排空延迟上界 = 宽限期 —— 队列长度有界，无独立熔断必要。
 */
public class ResourceRetirementQueue {
    static final Logger LOG = LoggerFactory.getLogger(ResourceRetirementQueue.class);

    /**
     * 排空调度缝：接收排空任务与其触发时间点（epoch millis）。默认实现经 globalTimer 一次性调度 +
     * globalWorker 卸载执行（排空动作可能含阻塞 I/O：连接 close、ZipFile close，不占用 timer 线程）。
     */
    public interface DrainScheduler {
        void scheduleDrain(Runnable drainTask, long triggerAtMillis);
    }

    private static final class GlobalDrainScheduler implements DrainScheduler {
        @Override
        public void scheduleDrain(Runnable drainTask, long triggerAtMillis) {
            long delay = Math.max(0, triggerAtMillis - CoreMetrics.currentTimeMillis());
            GlobalExecutors.globalTimer().schedule(() -> {
                try {
                    GlobalExecutors.globalWorker().execute(drainTask);
                } catch (Throwable e) {
                    // 卸载执行不可达：由排空任务体内的 fail-safe 兜底
                    drainTask.run();
                }
                return null;
            }, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    private static final ResourceRetirementQueue INSTANCE = new ResourceRetirementQueue();

    /**
     * 平台共享退役队列：三站（RoundRobinSupplier / DefaultVirtualFileSystem / ResourceCacheEntry）
     * 与延迟销毁共用同一实例、同一 grace 键、同一排空调度缝与时间锚。
     */
    public static ResourceRetirementQueue global() {
        return INSTANCE;
    }

    private final ConcurrentLinkedDeque<Retirement> pending = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();

    private final DrainScheduler scheduler;

    public ResourceRetirementQueue() {
        this(new GlobalDrainScheduler());
    }

    public ResourceRetirementQueue(DrainScheduler scheduler) {
        this.scheduler = scheduler;
    }

    static final class Retirement {
        final Object resource;
        final Consumer<Object> closeAction;
        final long deadline;
        boolean closed;

        Retirement(Object resource, Consumer<Object> closeAction, long deadline) {
            this.resource = resource;
            this.closeAction = closeAction;
            this.deadline = deadline;
        }

        void close() {
            if (closed)
                return;
            closed = true;
            try {
                closeAction.accept(resource);
            } catch (Throwable e) {
                LOG.warn("nop.commons.retire-close-fail:resource={}", resource, e);
            }
        }
    }

    /**
     * 退役登记：资源替换点只发布新代、不内联关闭旧代；旧代经此登记延迟关闭。
     * 关闭档（grace ≤0）退化为同步立即执行 closeAction（= 修复前语义）。
     */
    public <T> void retire(T resource, Consumer<T> closeAction) {
        long graceMillis = graceMillis();
        if (graceMillis <= 0) {
            // 关闭档：现行同步语义（确定性测试缝）
            try {
                closeAction.accept(resource);
            } catch (Throwable e) {
                LOG.warn("nop.commons.retire-close-fail:resource={}", resource, e);
            }
            return;
        }

        long deadline = CoreMetrics.currentTimeMillis() + graceMillis;
        pending.addLast(new Retirement(resource, (Consumer<Object>) closeAction, deadline));
        // 合并调度：同窗多笔退役只排一次
        if (drainScheduled.compareAndSet(false, true)) {
            try {
                scheduler.scheduleDrain(this::drainDue, deadline);
            } catch (Throwable e) {
                // fail-safe：排空调度不可达（调度器已销毁/拒绝）→ 同步立即关闭，无静默泄漏
                drainScheduled.set(false);
                LOG.debug("nop.commons.retire-drain-schedule-fail:fallBackToSyncClose", e);
                drainAllNow();
            }
        }
    }

    /**
     * 排空 deadline 已到的退役动作（FIFO）；剩余未到期的重新合并调度。
     * 由排空调度器在 deadline 时刻调用。
     */
    public void drainDue() {
        drainScheduled.set(false);
        long now = CoreMetrics.currentTimeMillis();

        Retirement head;
        while ((head = pending.peekFirst()) != null) {
            if (head.deadline > now)
                break;
            Retirement polled = pending.pollFirst();
            if (polled == null)
                break;
            polled.close();
        }

        Retirement next = pending.peekFirst();
        if (next != null && drainScheduled.compareAndSet(false, true)) {
            try {
                scheduler.scheduleDrain(this::drainDue, next.deadline);
            } catch (Throwable e) {
                drainScheduled.set(false);
                LOG.debug("nop.commons.retire-drain-schedule-fail:fallBackToSyncClose", e);
                drainAllNow();
            }
        }
    }

    /**
     * 立即按 FIFO 关闭全部已登记退役（不等待 deadline）：确定性测试触发缝（零 sleep）
     * 与关闭路径（{@link #close()}）共用。
     */
    public void drainAllNow() {
        drainScheduled.set(false);
        Retirement r;
        while ((r = pending.pollFirst()) != null) {
            r.close();
        }
    }

    public int pendingCount() {
        return pending.size();
    }

    /**
     * 宽限期配置读取：每次 retire 动态读取（grace 可配置 = 测试缝①），≤0 = 关闭档。
     */
    static long graceMillis() {
        Duration grace = CFG_CONCURRENT_RETIREMENT_GRACE_PERIOD.get();
        if (grace == null || grace.isNegative())
            return 0;
        return grace.toMillis();
    }

    /**
     * 队列销毁：同步关闭全部未排空退役，不丢失任何关闭动作。
     */
    public void close() {
        drainAllNow();
    }
}
