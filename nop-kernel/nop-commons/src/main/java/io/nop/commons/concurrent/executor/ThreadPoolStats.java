/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class ThreadPoolStats implements Serializable {
    /**
     * C4-012(c)（plan 2548）：零值统计——虚拟线程 per-task 池等无固定池统计的实现统一返回，
     * 避免 stats() 返回 null 给调用方判空负担。
     * <p>
     * plan 2573 补充复查修正：原形态为 {@code public static final ThreadPoolStats ZERO} 共享可变常量——
     * 本类是 {@code @DataBean}（全字段 public setter），任何调用方对返回实例调 setter 即污染全局常量、
     * 波及后续所有 zero-stats 消费方。改为 {@link #zero()} 工厂每次返回新实例（调用方可安全持有/修改）。
     */
    public static ThreadPoolStats zero() {
        return new ThreadPoolStats();
    }

    private static final long serialVersionUID = 6535088446065132264L;

    private int activeCount;
    private long completedTaskCount;
    private int corePoolSize;
    private int maxPoolSize;
    private int largestPoolSize;
    private int queueSize;
    private int queueCapacity;

    public int getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(int activeCount) {
        this.activeCount = activeCount;
    }

    public long getCompletedTaskCount() {
        return completedTaskCount;
    }

    public void setCompletedTaskCount(long completedTaskCount) {
        this.completedTaskCount = completedTaskCount;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getLargestPoolSize() {
        return largestPoolSize;
    }

    public void setLargestPoolSize(int largestPoolSize) {
        this.largestPoolSize = largestPoolSize;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}