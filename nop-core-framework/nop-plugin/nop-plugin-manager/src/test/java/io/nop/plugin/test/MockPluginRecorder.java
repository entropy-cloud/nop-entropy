package io.nop.plugin.test;

import java.util.Map;

/**
 * 跨 classloader 调用序列记录器：jar 内插件类（PluginClassLoader 隔离加载）经 plugin.json 的
 * importPackages 路由到宿主 classloader 访问本类，测试侧据此断言 start/stop 真实被调用。
 */
public class MockPluginRecorder {
    public static volatile boolean started = false;
    public static volatile boolean stopped = false;
    public static volatile String groupId;
    public static volatile String artifactId;
    public static volatile String version;

    public static void reset() {
        started = false;
        stopped = false;
        groupId = null;
        artifactId = null;
        version = null;
    }

    public static void onStart(String groupId, String artifactId, String version, Map<String, Object> config) {
        started = true;
        MockPluginRecorder.groupId = groupId;
        MockPluginRecorder.artifactId = artifactId;
        MockPluginRecorder.version = version;
    }

    public static void onStop() {
        stopped = true;
    }
}
