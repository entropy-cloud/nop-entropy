package io.nop.plugin.api;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface IPlugin {
    String getPluginGroupId();

    String getPluginArtifactId();

    String getPluginVersion();

    Timestamp getLastChangeTime();

    Timestamp getLoadTime();

    /**
     * 相当于执行本地RPC调用
     */
    CompletionStage<Map<String, Object>> invokeCommand(String command, Map<String, Object> args);
}