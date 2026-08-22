package io.nop.plugin.test;

import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginScope;
import io.nop.plugin.api.PluginState;

import java.util.Map;

/**
 * 六态可观测性探针 activator：probedPlugin 由测试在激活前注入——
 * activator 执行期捕获 stateAtActivate（ACTIVATING）、effect 回退期（scope.close 内）
 * 捕获 stateAtDispose（DEACTIVATING），使中间态转换链在单激活流中可断言。
 */
public class StateProbeActivator implements IPluginActivator {

    public static volatile IPlugin probedPlugin;
    public static volatile PluginState stateAtActivate;
    public static volatile PluginState stateAtDispose;

    public static void reset() {
        probedPlugin = null;
        stateAtActivate = null;
        stateAtDispose = null;
    }

    @Override
    public Disposable activate(IPluginScope scope, Map<String, Object> config) {
        IPlugin plugin = probedPlugin;
        if (plugin != null) {
            stateAtActivate = plugin.getState();
        }
        scope.effect(() -> {
            IPlugin p = probedPlugin;
            if (p != null) {
                stateAtDispose = p.getState();
            }
        });
        return null;
    }
}
