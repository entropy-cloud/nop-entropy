package io.nop.plugin.manager;

import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.plugin.api.IPlugin;

import java.util.List;

/**
 * 每一个plugin对应于一个uber jar，通过远程仓库下载，并使用独立的ClassLoader加载
 */
public interface IPluginManager {

    IPlugin loadPlugin(ArtifactCoordinates pluginId);

    void unloadPlugin(ArtifactCoordinates pluginId);

    List<IPlugin> getLoadedPlugins();
}