/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.lang.impl;

import io.nop.api.core.util.ICancellable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 一个函数容器，用于管理一组资源清理函数。当执行取消动作时，按照注册顺序执行清理函数。 清理函数的执行异常会被自动捕获并忽略，避免影响其他清理函数的执行
 */
public class Cancellable implements ICancellable {
    static final Logger LOG = LoggerFactory.getLogger(Cancellable.class);

    private volatile boolean cancelled;
    private List<Consumer<String>> callbacks;
    private volatile String cancelReason;

    public Cancellable() {
    }

    public Cancellable(Consumer<String> callback) {
        this.callbacks = new ArrayList<>(1);
        this.callbacks.add(callback);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel(String cancelReason) {
        if (cancelled)
            return;
        this.cancelReason = cancelReason;
        this.cancelled = true;
        tryRunCallbacks();
    }

    @Override
    public String getCancelReason() {
        return cancelReason;
    }

    /**
     * 注册清理函数。如果当前已经处于取消状态，则会立刻执行callback函数
     *
     * @param callback 当调用cancel时需要执行的清理函数
     */
    public void appendOnCancel(Consumer<String> callback) {
        if (callback == null)
            return;
        synchronized (this) {
            if (callbacks == null) {
                this.callbacks = new ArrayList<>();
            }
            this.callbacks.add(callback);
        }
        tryRunCallbacks();
    }

    public void prependOnCancel(Consumer<String> callback) {
        if (callback == null)
            return;

        synchronized (this) {
            if (callbacks == null) {
                this.callbacks = new ArrayList<>();
            }
            this.callbacks.add(0, callback);
        }
        tryRunCallbacks();
    }

    public void append(ICancellable task) {
        appendOnCancel(task::cancel);
    }

    public void prepend(ICancellable task) {
        prependOnCancel(task::cancel);
    }

    @Override
    public void removeOnCancel(Consumer<String> callback) {
        if (callback == null)
            return;

        synchronized (this) {
            if (callbacks != null)
                callbacks.remove(callback);
        }
    }

    void tryRunCallbacks() {
        List<Consumer<String>> tasks = null;
        synchronized (this) {
            if (cancelled) {
                tasks = this.callbacks;
                this.callbacks = null;
            }
        }
        if (tasks != null) {
            for (Consumer<String> task : tasks) {
                try {
                    task.accept(cancelReason);
                } catch (Throwable e) {
                    LOG.error("nop.err.commons.invoke-cancel-callback-fail:io.nop.task={}", task, e);
                }
            }
        }
    }
}