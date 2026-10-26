/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

/**
 * C2-002 有界等待夹具：属性赋值阶段（markPropSet 之前）阻塞，bean 已进入 scope 但
 * PROPERTY_SET 未推进——验证等待方超时类型化错误而非永久 wait()。
 */
public class TestPbiSlowSetupBean {
    private static final Object LOCK = new Object();
    private static volatile boolean setterEntered;
    private static boolean setterReleased;
    private static final long WAIT_MILLIS = 60_000;

    private String gate;

    public static void reset() {
        synchronized (LOCK) {
            setterReleased = false;
            LOCK.notifyAll();
        }
        setterEntered = false;
    }

    public static boolean isSetterEntered() {
        return setterEntered;
    }

    public static void releaseSetter() {
        synchronized (LOCK) {
            setterReleased = true;
            LOCK.notifyAll();
        }
    }

    public String getGate() {
        return gate;
    }

    public void setGate(String gate) {
        setterEntered = true;
        synchronized (LOCK) {
            long deadline = System.currentTimeMillis() + WAIT_MILLIS;
            while (!setterReleased) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0)
                    throw new IllegalStateException("fixture gate wait timeout: setter");
                try {
                    LOCK.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("fixture gate interrupted", e);
                }
            }
        }
        this.gate = gate;
    }
}
