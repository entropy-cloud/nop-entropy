/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

/**
 * C2-002 夹具对齐标志（静态标志 + 有界等待，可复位）。
 */
public class TestPbiRingFlags {
    private static final long WAIT_MILLIS = 30_000;

    private static volatile boolean aInitStarted;
    private static volatile boolean bCtorEntered;
    private static volatile boolean bInitStarted;

    public static void reset() {
        aInitStarted = false;
        bCtorEntered = false;
        bInitStarted = false;
    }

    public static boolean isAInitStarted() {
        return aInitStarted;
    }

    public static boolean isBCtorEntered() {
        return bCtorEntered;
    }

    public static void markBCtorEntered() {
        bCtorEntered = true;
    }

    public static void markAInitStarted() {
        aInitStarted = true;
    }

    public static void markBInitStarted() {
        bInitStarted = true;
    }

    public static boolean isBInitStarted() {
        return bInitStarted;
    }

    public static void awaitBInitStarted() {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (!bInitStarted) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0)
                throw new IllegalStateException("fixture gate wait timeout: bInitStarted");
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("fixture gate interrupted", e);
            }
        }
    }

    public static void awaitAInitStarted() {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (!aInitStarted) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0)
                throw new IllegalStateException("fixture gate wait timeout: aInitStarted");
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("fixture gate interrupted", e);
            }
        }
    }
}
