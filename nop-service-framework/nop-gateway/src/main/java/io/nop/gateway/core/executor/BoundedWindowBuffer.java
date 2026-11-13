/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.executor;

import io.nop.api.core.time.CoreMetrics;

import java.util.ArrayDeque;

/**
 * 共享有界窗口缓冲状态核（plan 2509 R2-06 裁定①：物理共享泛型缓冲组件，落 nop-gateway）。
 *
 * <p><b>六合一状态</b>：有界缓冲（容量 bufferSize 越窗前后同界）+ 窗口态（passed/forwarded）
 * + demand 记账 + pendingComplete 终态挂起 + cancel 清理 + disabled 直通档
 * （passthrough=true = 零缓冲直传，2.3 侧 bufferEnabled=false 形态）。
 *
 * <p><b>线程约定</b>：本组件自身<b>不加锁</b>——由持有它的宿主（如
 * {@link BufferedStreamingPublisher}）以自身 monitor 保护全部调用；宿主同时保留
 * attempt/consumer-identity 守卫、出锁投递（drain-loop）与上游授信/编排面
 * （裁定①：fetch/重试编排不上移组件）。
 *
 * <p><b>容量语义</b>：窗口期 N 满越窗 / T 超时越窗双门维持（窗口内 add 前容量检查由
 * {@link #shouldBufferInWindow()} 承担，满即提前越窗）；越窗后
 * {@link #add(Object)} 容量满返回 false = fail-loud 溢出终态（非静默丢弃、非水位排队）。
 *
 * <p><b>终态挂起语义</b>（C3-001 正确先例对齐）：complete 路径冲刷后缓冲非空即挂起
 * （{@link #setPendingComplete(boolean)}），下游 request 冲空缓冲后由宿主补发
 * onComplete；cancel 清理挂起终态（{@link #clearForCancel()}）。
 */
public final class BoundedWindowBuffer<T> {

    private final int capacity;
    private final boolean passthrough;
    private final long windowTimeMs;
    private final ArrayDeque<T> buffer = new ArrayDeque<>();

    private long windowStart;
    private boolean passed;    // 越窗：已开始向下游转发（窗口语义只延迟转发，不吞数据）
    private boolean forwarded; // 已有元素送达下游（窗口内外失败判定的唯一依据）
    private long demand;
    private boolean pendingComplete;
    private boolean cancelled;

    public BoundedWindowBuffer(int capacity, boolean passthrough, long windowTimeMs) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1: " + capacity);
        }
        if (windowTimeMs < 0) {
            throw new IllegalArgumentException("windowTimeMs must be >= 0: " + windowTimeMs);
        }
        this.capacity = capacity;
        this.passthrough = passthrough;
        this.windowTimeMs = windowTimeMs;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isPassthrough() {
        return passthrough;
    }

    // ======================= 窗口生命周期 =======================

    /**
     * 开始新窗口（每个 attempt 一次）：重置窗口起点与越窗标记（缓冲区此时必须已为空）。
     */
    public void beginWindow() {
        windowStart = CoreMetrics.currentTimeMillis();
        passed = false;
        forwarded = false;
    }

    /**
     * 窗口内缓冲判定（N/T 先到者越窗）：直通档恒 false；窗口期满或容量满 = 触发越窗。
     */
    public boolean shouldBufferInWindow() {
        return !passthrough && !passed && buffer.size() < capacity
                && CoreMetrics.currentTimeMillis() - windowStart < windowTimeMs;
    }

    public boolean isPassed() {
        return passed;
    }

    public void markPassed() {
        passed = true;
    }

    public boolean isForwarded() {
        return forwarded;
    }

    public void markForwarded() {
        forwarded = true;
    }

    // ======================= 有界缓冲 =======================

    /**
     * 入缓冲：容量（bufferSize）越窗前后同界。满 = 返回 false（宿主以类型化溢出错误
     * fail-loud 断流，禁静默丢弃/无界增长）。
     */
    public boolean add(T item) {
        if (buffer.size() >= capacity) {
            return false;
        }
        buffer.add(item);
        return true;
    }

    public T poll() {
        return buffer.poll();
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public int size() {
        return buffer.size();
    }

    public void clear() {
        buffer.clear();
    }

    // ======================= demand 记账 =======================

    public void addDemand(long n) {
        demand += n;
        if (demand < 0) {
            demand = Long.MAX_VALUE;
        }
    }

    public boolean hasDemand() {
        return demand > 0;
    }

    public void decDemand() {
        if (demand > 0) {
            demand--;
        }
    }

    // ======================= pendingComplete 终态挂起 =======================

    public boolean isPendingComplete() {
        return pendingComplete;
    }

    public void setPendingComplete(boolean pendingComplete) {
        this.pendingComplete = pendingComplete;
    }

    // ======================= cancel 清理 =======================

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * 取消清理：清空缓冲 + 挂起终态不再补发 + demand 归零 + 终态化。
     */
    public void clearForCancel() {
        buffer.clear();
        pendingComplete = false;
        demand = 0;
        cancelled = true;
    }
}
