package io.nop.plugin.test;

import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginScope;

import java.util.Map;

/**
 * 激活失败测试 activator：activate 抛错 → 实例回退 DEACTIVATED 并记录错误（错误带实例 key 参数）。
 */
public class FailActivator implements IPluginActivator {

    @Override
    public Disposable activate(IPluginScope scope, Map<String, Object> config) {
        throw new IllegalStateException("activator boom");
    }
}
