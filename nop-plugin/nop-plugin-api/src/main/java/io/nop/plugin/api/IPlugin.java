package io.nop.plugin.api;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 最小化的插件接口，不要求插件使用Nop平台实现，只需要引入nop-plugin-api实现这个接口即可。
 * 另外利用ServiceLoader机制注册插件实现类
 */
public interface IPlugin {
    String getPluginGroupId();

    String getPluginArtifactId();

    String getPluginVersion();

    Timestamp getLastChangeTime();

    Timestamp getLoadTime();

    /**
     * 相当于执行本地RPC调用
     */
    CompletionStage<Map<String, Object>> invokeCommand(String command, Map<String, Object> args, IPluginCancelToken cancelToken);

    void start();

    void stop();
}