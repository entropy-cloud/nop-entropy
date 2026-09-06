package io.nop.plugin.jarplugin;

import io.nop.plugin.test.MockPluginRecorder;
import io.nop.plugin.test.MockStopControl;

/**
 * stop 抛错的模拟插件——验证 unloadPlugin 失败路径不关闭 PluginClassLoader
 * （保留 holder 与注册表状态，保证 unload 可重试）。
 * 开关放在 io.nop.plugin.test.MockStopControl（importPackages 路由到宿主 classloader，
 * jar 内类与测试代码共享同一静态字段）；抛 java.lang 异常（bootstrap loader 可见）。
 */
public class MockPluginStopFail extends MockPlugin {
    @Override
    public void stop() {
        if (MockStopControl.failStop)
            throw new IllegalStateException("mock stop failure");
        MockPluginRecorder.onStop();
    }
}
