/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.thread;

import io.nop.commons.concurrent.executor.StopPooledThreadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;

/**
 * 平台中所有线程都从此基类继承
 */
public class NopThread extends Thread {
    static final Logger LOG = LoggerFactory.getLogger(NopThread.class);

    private long creationTime = System.currentTimeMillis();

    public NopThread(Runnable target, String name, long stackSize) {
        super(null, target, name, stackSize);
        this.setUncaughtExceptionHandler(DEFAULT_EXCEPTION_HANDLER);
    }

    public NopThread() {
        this.setUncaughtExceptionHandler(DEFAULT_EXCEPTION_HANDLER);
    }

    public NopThread(Runnable target) {
        super(target);
        this.setUncaughtExceptionHandler(DEFAULT_EXCEPTION_HANDLER);
    }

    public NopThread(String name) {
        super(name);
        this.setUncaughtExceptionHandler(DEFAULT_EXCEPTION_HANDLER);
    }

    public long getCreationTime() {
        return creationTime;
    }

    public static final UncaughtExceptionHandler DEFAULT_EXCEPTION_HANDLER = new UncaughtExceptionHandler() {

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            if (e == StopPooledThreadException.INSTANCE)
                return;
            LOG.error("nop.commons.uncaught-exception:tid={}", t.getId(), e);
        }
    };

    public static RejectedExecutionHandler REJECTED_EXECUTION_ABORT_POLICY = new AbortPolicy();
}