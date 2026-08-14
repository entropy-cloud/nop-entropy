package io.nop.plugin.manager.impl;

import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.NopPluginConstants;
import io.nop.plugin.manager.IPluginConfigProvider;
import io.nop.plugin.manager.IPluginManager;
import io.nop.plugin.manager.classloader.PluginClassLoader;
import io.nop.plugin.manager.resolver.IPluginResourceResolver;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.inject.Inject;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_DEFINITION_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_INSTANCE_KEY;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_DEFINITION_NOT_LOADED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_NOT_SUPPORTED;

/**
 * 双轨来源（设计文档 01-architecture-baseline.md §二）的统一编排：
 * <ul>
 *     <li><b>uber jar 轨</b>：id 为 Maven 坐标（{@link ArtifactCoordinates#parse} 成功），
 *     保留 resolver → {@link PluginClassLoader} → plugin.json → 实例化链路（类隔离不变）。</li>
 *     <li><b>VFS 轨</b>：id 为本地 VFS 路径，经 plugin.xdef（{@link DslModelParser}）解析
 *     *.plugin.xml 为静态定义，由 {@link VfsPluginDefinition}（定义持有类）持有。</li>
 * </ul>
 *
 * <p>状态机感知路由（§7.1 兼容机制）：aware → 定义级状态机（loadPlugin 只到 LOADED，
 * 不调用 start）；非 aware → 兼容路径（loadPlugin 执行旧 start 语义，失败时 stop 清理、
 * entry 不写入 map）。VFS 轨 config 来源 = 空 Map（定义级 coeffect 读全局配置，W5）。
 */
public class PluginManagerImpl implements IPluginManager {
    private final Map<String, PluginHolder> plugins = new ConcurrentHashMap<>();

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
    public IPlugin loadPlugin(String pluginId) {
        ArtifactCoordinates coords = tryParseCoordinates(pluginId);
        if (coords != null)
            return loadPluginFromJar(coords);
        return loadPluginFromVfs(pluginId);
    }

    @Override
    public void unloadPlugin(String pluginId) {
        PluginHolder holder = plugins.get(pluginId);
        if (holder != null) {
            try {
                if (holder.plugin.isStateMachineAware()) {
                    // unload 守卫：有实例时抛异常（定义保留在 map，可先 destroyInstance 再重试 unload）
                    holder.plugin.unload();
                } else {
                    holder.plugin.stop();
                }
                plugins.remove(pluginId);
            } finally {
                IoHelper.safeCloseObject(holder.classLoader);
            }
        }
    }

    @Override
    public IPluginInstance getInstance(String pluginId, String instanceKey) {
        PluginHolder holder = plugins.get(pluginId);
        return holder == null ? null : holder.plugin.getInstance(instanceKey);
    }

    @Override
    public List<IPluginInstance> getInstances(String pluginId) {
        PluginHolder holder = plugins.get(pluginId);
        return holder == null ? Collections.emptyList() : holder.plugin.getInstances();
    }

    @Override
    public IPluginInstance createInstance(String pluginId, String instanceKey, Map<String, Object> config,
                                          IPluginInstance parent) {
        PluginHolder holder = plugins.get(pluginId);
        if (holder == null) {
            // 定义级 LOADED 校验：未加载的定义 createInstance 明确失败
            throw new NopException(ERR_PLUGIN_DEFINITION_NOT_LOADED).param(ARG_PLUGIN_ID, pluginId);
        }
        IPlugin plugin = holder.plugin;
        if (!(plugin instanceof VfsPluginDefinition)) {
            // uber jar 轨边界裁定：plugin.json 定义无 plugin.xdef/activator 载体，
            // 实例化路径为显式 successor 项（W4/W7），明确失败（No Silent No-Op），不返回半成品实例
            throw new NopException(ERR_PLUGIN_INSTANCE_NOT_SUPPORTED)
                    .param(ARG_PLUGIN_ID, pluginId)
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
        return ((VfsPluginDefinition) plugin).createInstance(instanceKey, config, parent);
    }

    @Override
    public void destroyInstance(String pluginId, String instanceKey) {
        PluginHolder holder = plugins.get(pluginId);
        if (holder == null) {
            throw new NopException(ERR_PLUGIN_DEFINITION_NOT_LOADED).param(ARG_PLUGIN_ID, pluginId);
        }
        IPluginInstance instance = holder.plugin.getInstance(instanceKey);
        if (instance == null) {
            throw new NopException(ERR_PLUGIN_INSTANCE_NOT_FOUND)
                    .param(ARG_PLUGIN_ID, pluginId)
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
        instance.destroy();
    }

    @Override
    public List<IPlugin> getLoadedPlugins() {
        return plugins.values().stream().map(holder -> holder.plugin).collect(Collectors.toList());
    }

    /**
     * id 判别（本 plan 裁定）：{@link ArtifactCoordinates#parse} 成功 → Maven 坐标 → uber jar 轨；
     * parse 抛 IllegalArgumentException 视为非坐标 → VFS 路径轨（含 ":" 的 VFS 路径存在误判坐标的
     * 可接受残余，见 plan 记录）。
     */
    private ArtifactCoordinates tryParseCoordinates(String pluginId) {
        try {
            return ArtifactCoordinates.parse(pluginId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private IPlugin loadPluginFromVfs(String pluginId) {
        return plugins.computeIfAbsent(pluginId, id -> {
            IResource resource = VirtualFileSystem.instance().getResource(id);
            if (!resource.exists()) {
                throw new NopException(ERR_PLUGIN_DEFINITION_NOT_FOUND).param(ARG_PLUGIN_ID, id);
            }
            DynamicObject definition;
            try {
                definition = (DynamicObject) new DslModelParser(NopPluginConstants.PLUGIN_XDEF_PATH)
                        .parseFromVirtualPath(id);
            } catch (NopException e) {
                throw e.param(ARG_PLUGIN_ID, id);
            }
            VfsPluginDefinition plugin = new VfsPluginDefinition(id, definition);
            plugin.load(Collections.emptyMap());
            return new PluginHolder(plugin, null);
        }).plugin;
    }

    private IPlugin loadPluginFromJar(ArtifactCoordinates coords) {
        String id = coords.toString();
        return plugins.computeIfAbsent(id, k -> {
            List<URL> urls = resourceResolver.resolvePluginResource(coords);
            PluginClassLoader classLoader = new PluginClassLoader(urls.toArray(new URL[0]),
                    this.getClass().getClassLoader());

            IPlugin plugin = classLoader.loadPlugin();
            Map<String, Object> config = pluginConfigProvider.getPluginConfig(coords);
            try {
                if (plugin.isStateMachineAware()) {
                    plugin.load(config);
                } else {
                    plugin.start(coords.getGroupId(), coords.getArtifactId(), coords.getVersion(), config);
                }
                return new PluginHolder(plugin, classLoader);
            } catch (RuntimeException e) {
                try {
                    if (plugin.isStateMachineAware()) {
                        plugin.unload();
                    } else {
                        plugin.stop();
                    }
                } finally {
                    IoHelper.safeCloseObject(classLoader);
                }
                throw e;
            }
        }).plugin;
    }
}
