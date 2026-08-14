package io.nop.plugin.jarplugin;

import io.nop.plugin.test.MockPluginStartException;

import java.util.Map;

/**
 * start 抛错的模拟插件——验证兼容路径失败语义（start 失败 → stop → rethrow，entry 不入 map）。
 * 抛 {@link MockPluginStartException}（io.nop.plugin.test 包经 importPackages 路由到宿主
 * classloader，jar 内类可解析；parent 是 bootstrap loader，io.nop.api.core 不可见）。
 */
public class MockPluginFail extends MockPlugin {
    @Override
    public void start(String pluginGroupId, String pluginArtifactId, String pluginVersion,
                      Map<String, Object> config) {
        throw new MockPluginStartException("mock start failure");
    }
}
