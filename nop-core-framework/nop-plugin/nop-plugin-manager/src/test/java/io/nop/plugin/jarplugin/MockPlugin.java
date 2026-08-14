package io.nop.plugin.jarplugin;

import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.test.MockPluginRecorder;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 非状态机感知的模拟插件（仅 start/stop，无 load/unload）——打包进测试 jar 由
 * {@code PluginClassLoader} 加载，经 recorder（importPackages 路由到宿主 classloader）
 * 记录调用序列，验证兼容路径"行为与改造前等价"。
 */
public class MockPlugin implements IPlugin {
    @Override
    public void start(String pluginGroupId, String pluginArtifactId, String pluginVersion,
                      Map<String, Object> config) {
        MockPluginRecorder.onStart(pluginGroupId, pluginArtifactId, pluginVersion, config);
    }

    @Override
    public void stop() {
        MockPluginRecorder.onStop();
    }

    @Override
    public String getPluginGroupId() {
        return null;
    }

    @Override
    public String getPluginArtifactId() {
        return null;
    }

    @Override
    public String getPluginVersion() {
        return null;
    }

    @Override
    public Timestamp getLastChangeTime() {
        return null;
    }

    @Override
    public Timestamp getLoadTime() {
        return null;
    }

    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                   String fieldSelection,
                                                                   IPluginCancelToken cancelToken) {
        return null;
    }

    @Override
    public Map<String, Object> invokeCommand(String command, Map<String, Object> args,
                                             String fieldSelection,
                                             IPluginCancelToken cancelToken) {
        return null;
    }
}
