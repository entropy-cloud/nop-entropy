/**
 * 并发初始化死锁复现测试辅助类：
 * D 的构造函数在静态门闩上阻塞，用于协调三个线程的时序。
 */
package test.io.entropy.beans;

import jakarta.annotation.PostConstruct;

public class TestConcurrentD {
    private static final Object LOCK = new Object();
    private static volatile boolean entered;
    private static volatile boolean release;
    private static volatile boolean inited;

    public static void reset() {
        synchronized (LOCK) {
            entered = false;
            release = false;
            inited = false;
        }
    }

    public static boolean isEntered() {
        return entered;
    }

    public static boolean isInited() {
        return inited;
    }

    public static void release() {
        synchronized (LOCK) {
            release = true;
            LOCK.notifyAll();
        }
    }

    public TestConcurrentD() {
        synchronized (LOCK) {
            entered = true;
            while (!release) {
                try {
                    LOCK.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for constructor release", e);
                }
            }
        }
    }

    @PostConstruct
    public void init() {
        inited = true;
    }
}