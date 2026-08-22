package io.nop.plugin.test;

import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginScope;

import java.util.Map;

/**
 * 激活失败测试 activator：activate 抛错 → 定义置 FAILED（资源已回退）并记录
 * lastActivationError（错误经定义级激活错误通道，异常带 pluginId 参数）；
 * FAILED 可重试（显式 activatePlugin 恢复尝试）。
 *
 * <p>{@link #attemptCount} 激活尝试计数器（失败阈值暂停可观测性断言依赖——
 * 阈值后 reconcile 不再尝试 = 计数不变；显式 activate() 恢复 = 计数 +1）。
 */
public class FailActivator implements IPluginActivator {

    public static volatile int attemptCount = 0;

    @Override
    public Disposable activate(IPluginScope scope, Map<String, Object> config) {
        attemptCount++;
        throw new IllegalStateException("activator boom");
    }
}
