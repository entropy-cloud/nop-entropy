/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

import java.util.concurrent.TimeUnit;

/**
 * C4-008 F4 夹具：init 阶段阻塞（无任何超时），验证 join 有界超时兜底而非永久 pending。
 */
public class TestStartHangInitBean {
    private static final Object LOCK = new Object();
    private static volatile boolean initEntered;
    private static boolean initReleased;
    private static final long WAIT_MILLIS = 60_000;

    public static void reset() {
        synchronized (LOCK) {
            initReleased = false;
            LOCK.notifyAll();
        }
        initEntered = false;
    }

    public static boolean isInitEntered() {
        return initEntered;
    }

    public static void releaseInit() {
        synchronized (LOCK) {
            initReleased = true;
            LOCK.notifyAll();
        }
    }

    public void initMe() {
        initEntered = true;
        synchronized (LOCK) {
            long deadline = System.currentTimeMillis() + WAIT_MILLIS;
            while (!initReleased) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0)
                    throw new IllegalStateException("fixture gate wait timeout: init");
                try {
                    LOCK.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("fixture gate interrupted", e);
                }
            }
        }
    }
}
