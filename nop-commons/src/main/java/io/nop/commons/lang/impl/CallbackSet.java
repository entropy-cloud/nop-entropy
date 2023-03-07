/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.lang.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class CallbackSet {
    static final Logger LOG = LoggerFactory.getLogger(CallbackSet.class);

    private ArrayDeque<Runnable> callbacks = null;

    private ArrayDeque<Runnable> makeCallbacks() {
        if (callbacks == null) {
            callbacks = new ArrayDeque<>();
        }
        return callbacks;
    }

    public void addCallback(Runnable callback) {
        synchronized (this) {
            makeCallbacks().add(callback);
        }
    }

    public void removeCallback(Runnable callback) {
        synchronized (this) {
            makeCallbacks().remove(callback);
        }
    }

    public void prependCallback(Runnable callback) {
        synchronized (this) {
            makeCallbacks().addFirst(callback);
        }
    }

    private Iterable<Runnable> fetchCallbacks() {
        synchronized (this) {
            ArrayDeque<Runnable> ret = this.callbacks;
            if (ret != null) {
                callbacks = null;
            }
            return ret;
        }
    }

    public void invokeAndClear() {
        Iterable<Runnable> tasks = fetchCallbacks();
        invokeAll(tasks);
    }

    public void invoke() {
        invokeAll(copyTasks());
    }

    private void invokeAll(Iterable<Runnable> tasks) {
        if (tasks != null) {
            for (Runnable task : tasks) {
                try {
                    task.run();
                } catch (Throwable e) {
                    LOG.error("nop.commons.exec-callback-fail", e);
                }
            }
        }
    }

    private List<Runnable> copyTasks() {
        synchronized (this) {
            if (callbacks == null)
                return null;
            return new ArrayList<>(callbacks);
        }
    }
}
