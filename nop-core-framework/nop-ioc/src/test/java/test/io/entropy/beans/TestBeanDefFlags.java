/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

/**
 * C2-001 死锁夹具的线程对齐门闩（TestConcurrentD 同款静态标志 + wait/notify 模式，可复位，有界等待）。
 */
public class TestBeanDefFlags {
    private static final Object LOCK = new Object();
    private static final long WAIT_MILLIS = 30_000;

    private static boolean goNext;
    private static boolean aCtorEntered;
    private static boolean bCtorEntered;
    private static boolean ctorHangReleased;
    private static volatile boolean ctorHangEntered;

    public static void reset() {
        synchronized (LOCK) {
            goNext = false;
            aCtorEntered = false;
            bCtorEntered = false;
            ctorHangReleased = false;
            LOCK.notifyAll();
        }
        ctorHangEntered = false;
    }

    public static boolean isACtorEntered() {
        return aCtorEntered;
    }

    public static boolean isBCtorEntered() {
        return bCtorEntered;
    }

    public static boolean isCtorHangEntered() {
        return ctorHangEntered;
    }

    public static void signalGoNext() {
        synchronized (LOCK) {
            goNext = true;
            LOCK.notifyAll();
        }
    }

    public static void releaseCtorHang() {
        synchronized (LOCK) {
            ctorHangReleased = true;
            LOCK.notifyAll();
        }
    }

    public static void aCtorEnterAndWait() {
        synchronized (LOCK) {
            aCtorEntered = true;
            LOCK.notifyAll();
            waitWhileLocked(() -> !goNext);
        }
    }

    public static void bCtorEnterAndWait() {
        synchronized (LOCK) {
            bCtorEntered = true;
            LOCK.notifyAll();
            waitWhileLocked(() -> !goNext);
        }
    }

    public static void ctorHangEnterAndWait() {
        ctorHangEntered = true;
        synchronized (LOCK) {
            waitWhileLocked(() -> !ctorHangReleased);
        }
    }

    private static void waitWhileLocked(java.util.function.BooleanSupplier cond) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (cond.getAsBoolean()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0)
                throw new IllegalStateException("fixture gate wait timeout");
            try {
                LOCK.wait(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("fixture gate interrupted", e);
            }
        }
    }
}
