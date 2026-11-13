package io.nop.plugin.test;

import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginScope;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * 可门闩停车 activator（锁迁移并发测试夹具）：activate 时先信号 {@link #entered}（此时定义已
 * 处于 ACTIVATING——状态写先行于 activator 执行，同一 lifecycleLock 临界区），再等待
 * {@link #release}，释放后信号 {@link #done}。用于确定性对齐「unloadPlugin × 在途激活」交错窗，
 * 使 reconcileLock 串行化（裁定①）的互斥语义可观测：修复后 unloadPlugin 不得在激活在途时返回。
 */
public class BlockingActivator implements IPluginActivator {

    public static volatile CountDownLatch entered = new CountDownLatch(1);
    public static volatile CountDownLatch release = new CountDownLatch(1);
    public static volatile CountDownLatch done = new CountDownLatch(1);
    /** 释放后是否以失败收尾（在途激活失败路径注入：ACTIVATING → rollback → FAILED） */
    public static volatile boolean failOnRelease = false;

    public static void reset() {
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        done = new CountDownLatch(1);
        failOnRelease = false;
    }

    @Override
    public Disposable activate(IPluginScope scope, Map<String, Object> config) {
        entered.countDown();
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("blocking activator interrupted", e);
        }
        done.countDown();
        if (failOnRelease) {
            throw new IllegalStateException("blocking activator failed after release (injected)");
        }
        return null;
    }
}
