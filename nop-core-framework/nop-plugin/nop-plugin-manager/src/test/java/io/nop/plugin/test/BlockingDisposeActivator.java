package io.nop.plugin.test;

import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginScope;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * 可门闩停车 effect activator（deactivate 翻转窗测试夹具）：激活成功并注册一个 dispose 时
 * 停车的 effect（dispose 信号 {@link #disposeEntered} 后等待 {@link #disposeRelease}）——
 * 使 doDeactivate 停留在 DEACTIVATING 中间态的窗口可确定性对齐（scope.close 内 effect 回退
 * 阻塞），供翻转窗消费方 INACTIVE 统一性断言使用。dispose 不抛异常（纯停车，无失败注入）。
 */
public class BlockingDisposeActivator implements IPluginActivator {

    public static volatile CountDownLatch disposeEntered = new CountDownLatch(1);
    public static volatile CountDownLatch disposeRelease = new CountDownLatch(1);

    public static void reset() {
        disposeEntered = new CountDownLatch(1);
        disposeRelease = new CountDownLatch(1);
    }

    @Override
    public Disposable activate(IPluginScope scope, Map<String, Object> config) {
        return () -> {
            disposeEntered.countDown();
            try {
                disposeRelease.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
