/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread factory for nop-stream background executors.
 *
 * <p>Current semantics: every thread is created as a daemon thread and named
 * {@code <prefix>-N}, where {@code N} is a per-factory counter starting at 1 and
 * incremented by one for each new thread. The prefix identifies the owning component
 * and instance (for example {@code "jc-failure-detector-" + jobId}); log analysis and
 * tests locate these threads by prefix.
 *
 * <p>Threads are daemons so background executors never block JVM shutdown. No
 * uncaught exception handler is installed by this factory.
 */
public final class NopStreamThreadFactory implements ThreadFactory {

    private final String prefix;

    private final AtomicLong index = new AtomicLong(0);

    private NopStreamThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Creates a factory whose threads are named {@code <prefix>-1}, {@code <prefix>-2}, ...
     * and are all daemon threads.
     *
     * @param prefix the stable name prefix identifying the owning component and instance
     * @return a new {@link NopStreamThreadFactory}
     */
    public static NopStreamThreadFactory named(String prefix) {
        return new NopStreamThreadFactory(prefix);
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, prefix + index.incrementAndGet());
        t.setDaemon(true);
        return t;
    }
}
