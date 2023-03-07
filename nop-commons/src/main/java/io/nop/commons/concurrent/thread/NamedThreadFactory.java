/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {
    static final Logger LOG = LoggerFactory.getLogger(NamedThreadFactory.class);

    static final AtomicInteger s_poolNumber = new AtomicInteger(1);

    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final int poolNumber;
    private String namePrefix;
    private boolean isDaemon;
    private int priority;

    private UncaughtExceptionHandler uncaughtExceptionHandler = NopThread.DEFAULT_EXCEPTION_HANDLER;

    public NamedThreadFactory(String name) {
        this(name, false, Thread.NORM_PRIORITY);
    }

    public NamedThreadFactory(String name, boolean daemon) {
        this(name, daemon, Thread.NORM_PRIORITY);
    }

    public NamedThreadFactory(String prefix, boolean daemon, int priority) {
        poolNumber = s_poolNumber.getAndIncrement();
        namePrefix = prefix == null ? "nop" : prefix;
        isDaemon = daemon;
        this.priority = priority;
    }

    public void setUncaughtExceptionHandler(UncaughtExceptionHandler exceptionHandler) {
        this.uncaughtExceptionHandler = exceptionHandler;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    public boolean isDaemon() {
        return isDaemon;
    }

    public void setDaemon(boolean daemon) {
        isDaemon = daemon;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public static NamedThreadFactory newThreadFactory(String namePrefix, boolean daemon) {
        return new NamedThreadFactory(namePrefix, daemon);
    }

    public static NamedThreadFactory newThreadFactory(String name) {
        return new NamedThreadFactory(name);
    }

    public Thread newThread(Runnable r) {
        Thread t = new NopThread(r, namePrefix + '-' + poolNumber + '-' + threadNumber.getAndIncrement(), 0);
        t.setDaemon(isDaemon);
        if (t.getPriority() != priority) {
            t.setPriority(priority);
        }
        if (uncaughtExceptionHandler != null)
            t.setUncaughtExceptionHandler(uncaughtExceptionHandler);
        return t;
    }
}