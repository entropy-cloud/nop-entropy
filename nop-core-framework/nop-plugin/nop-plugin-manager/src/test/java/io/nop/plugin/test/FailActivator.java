package io.nop.plugin.test;

import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginScope;

import java.util.Map;

/**
 * 激活失败测试 activator：activate 抛错 → 实例回退 DEACTIVATED 并记录错误（错误带实例 key 参数）。
 *
 * <p>W5 改造：{@link #attemptCount} 激活尝试计数器（失败阈值暂停可观测性断言依赖——
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
