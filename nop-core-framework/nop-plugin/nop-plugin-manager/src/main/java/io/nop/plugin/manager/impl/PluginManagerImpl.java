package io.nop.plugin.manager.impl;

import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.commons.util.IoHelper;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.manager.IPluginConfigProvider;
import io.nop.plugin.manager.IPluginManager;
import io.nop.plugin.manager.classloader.PluginClassLoader;
import io.nop.plugin.manager.resolver.IPluginResourceResolver;
import jakarta.inject.Inject;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PluginManagerImpl implements IPluginManager {
    private final Map<ArtifactCoordinates, PluginHolder> plugins = new ConcurrentHashMap<>();

    private IPluginResourceResolver resourceResolver;
    private IPluginConfigProvider pluginConfigProvider;

    static class PluginHolder {
        final IPlugin plugin;
        final PluginClassLoader classLoader;

        public PluginHolder(IPlugin plugin, PluginClassLoader classLoader) {
            this.plugin = plugin;
            this.classLoader = classLoader;
        }
    }

    @Inject
    public void setResourceResolver(IPluginResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Inject
    public void setPluginConfigProvider(IPluginConfigProvider pluginConfigProvider) {
        this.pluginConfigProvider = pluginConfigProvider;
    }

    @Override
    public IPlugin loadPlugin(ArtifactCoordinates pluginId) {
        return plugins.computeIfAbsent(pluginId, k -> {
            List<URL> urls = resourceResolver.resolvePluginResource(pluginId);
            PluginClassLoader classLoader = new PluginClassLoader(urls.toArray(new URL[0]),
                    this.getClass().getClassLoader());

            IPlugin plugin = classLoader.loadPlugin();
            Map<String, Object> config = pluginConfigProvider.getPluginConfig(pluginId);
            try {
                plugin.start(pluginId.getGroupId(), pluginId.getArtifactId(), pluginId.getVersion(), config);
                return new PluginHolder(plugin, classLoader);
            } catch (RuntimeException e) {
                plugin.stop();
                throw e;
            }
        }).plugin;
    }

    @Override
    public void unloadPlugin(ArtifactCoordinates pluginId) {
        PluginHolder holder = plugins.remove(pluginId);
        if (holder != null) {
            try {
                holder.plugin.stop();
            } finally {
                IoHelper.safeCloseObject(holder.classLoader);
            }
        }
    }

    @Override
    public List<IPlugin> getLoadedPlugins() {
        return plugins.values().stream().map(holder -> holder.plugin).collect(Collectors.toList());
    }
}
