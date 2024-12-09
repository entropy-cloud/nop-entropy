package io.nop.plugin.core;

import io.nop.api.core.beans.ArtifactCoordinates;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 每一个plugin对应于一个uber jar，通过远程仓库下载，并使用独立的ClassLoader加载
 */
public interface IPluginManager {

    IPlugin loadPlugin(ArtifactCoordinates pluginId);

    CompletionStage<IPlugin> loadPluginAsync(ArtifactCoordinates pluginId);

    void unloadPlugin(ArtifactCoordinates pluginId);

    List<IPlugin> getLoadedPlugins();
}