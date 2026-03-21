/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Checkpoint ID 生成器。
 * 
 * <p>使用 AtomicLong 生成严格递增的 checkpoint ID。
 */
public class CheckpointIDCounter implements Serializable {

    private static final long serialVersionUID = 1L;

    private final AtomicLong counter;

    public CheckpointIDCounter() {
        this.counter = new AtomicLong(0);
    }

    public CheckpointIDCounter(long initialValue) {
        this.counter = new AtomicLong(initialValue);
    }

    /**
     * 获取当前计数器值并递增。
     */
    public long getAndIncrement() {
        return counter.getAndIncrement();
    }

    /**
     * 获取当前计数器值。
     */
    public long get() {
        return counter.get();
    }

    /**
     * 设置计数器值（用于恢复）。
     */
    public void set(long value) {
        counter.set(value);
    }

    /**
     * 递增计数器并返回新值。
     */
    public long incrementAndGet() {
        return counter.incrementAndGet();
    }

    /**
     * 比较并设置值。
     */
    public boolean compareAndSet(long expect, long update) {
        return counter.compareAndSet(expect, update);
    }
}
