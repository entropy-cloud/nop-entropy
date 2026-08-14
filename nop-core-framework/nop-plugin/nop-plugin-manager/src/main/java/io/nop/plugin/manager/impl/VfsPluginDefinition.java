package io.nop.plugin.manager.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.model.object.DynamicObject;
import io.nop.ioc.model.BeansModel;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.PluginState;
import io.nop.plugin.manager.PluginManagerConstants;
import io.nop.plugin.manager.PluginManagerErrors;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_MULTIPLE_INSTANCES;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_INSTANCE_KEY;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_INSTANCE_KEYS;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCES_NOT_EMPTY;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_EXISTS;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_NOT_FOUND;

/**
 * VFS 轨（本地 *.plugin.xml）的定义持有类——manager 侧实现 {@link IPlugin}，
 * 持有经 plugin.xdef 解析出的静态定义（{@link DynamicObject}）与定义级状态
 * （{@link PluginState}：UNLOADED → LOADED，见设计文档 01-architecture-baseline.md §三）。
 *
 * <p>实例 registry 的唯一持有者 = 本类（W5 的 {@code IPluginContextImpl} 以同一 registry 为
 * 数据源，不许另起第二份 registry）。{@link #createInstance} 创建实例并立即激活（注册进 registry）；
 * {@link #destroyInstance} 销毁并移除；unload 守卫（有实例禁止 unload）。
 *
 * <p>钉死行为：VFS 轨无 Maven 坐标（groupId/artifactId/version 为 null）；start/stop 已收敛为
 * §7.1 语义（start = load + createInstance(默认 key)；stop = destroyInstance + unload）；
 * P2-C 裁决：定义级 {@link #updateConfig} 更新定义默认配置——ACTIVATED 实例热应用（经委托 provider
 * 触发变更通知），DEACTIVATED 实例缓存、下次 activate 应用。定义级 invokeCommand（§7.1，W4 落地）：
 * 实例数=1 经该实例路由 / >1 抛 ERR_PLUGIN_MULTIPLE_INSTANCES / =0 抛 INACTIVE。
 */
public class VfsPluginDefinition implements IPlugin {
    private final String pluginId;
    private final DynamicObject definition;
    private final BeansModel beansModel;
    private final String activatorName;

    private final Map<String, IPluginInstance> instances = new ConcurrentHashMap<>();
    private PluginState state = PluginState.UNLOADED;
    private Timestamp lastChangeTime;
    private Timestamp loadTime;

    private volatile Map<String, Object> definitionConfig = new LinkedHashMap<>();

    public VfsPluginDefinition(String pluginId, DynamicObject definition) {
        this.pluginId = pluginId;
        this.definition = definition;
        this.beansModel = (BeansModel) definition.prop_get("beans");
        this.activatorName = (String) definition.prop_get("activator");
    }

    public String getPluginId() {
        return pluginId;
    }

    public DynamicObject getDefinition() {
        return definition;
    }

    /**
     * 定义默认配置（load(config) 传入，updateConfig 合并更新；实例合并视图 = 定义默认 + 实例配置）。
     */
    public Map<String, Object> getDefinitionConfig() {
        return definitionConfig;
    }

    /**
     * 定义声明的 bean 模型（plugin.xdef 的 beans 子元素；无 beans 时为 null）。
     */
    public BeansModel getBeansModel() {
        return beansModel;
    }

    /**
     * 定义声明的 activator bean id（plugin.xdef 的 activator 属性；未声明为 null）。
     */
    public String getActivatorName() {
        return activatorName;
    }

    @Override
    public boolean isStateMachineAware() {
        return true;
    }

    @Override
    public PluginState getState() {
        return state;
    }

    @Override
    public void load(Map<String, Object> config) {
        this.definitionConfig = config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
        this.state = PluginState.LOADED;
        this.loadTime = CoreMetrics.currentTimestamp();
        this.lastChangeTime = this.loadTime;
    }

    @Override
    public void unload() {
        if (!instances.isEmpty()) {
            throw new NopException(ERR_PLUGIN_INSTANCES_NOT_EMPTY)
                    .param(ARG_PLUGIN_ID, pluginId)
                    .param(ARG_INSTANCE_KEYS, new ArrayList<>(instances.keySet()));
        }
        this.state = PluginState.UNLOADED;
        this.definitionConfig = new LinkedHashMap<>();
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
        return lastChangeTime;
    }

    @Override
    public Timestamp getLoadTime() {
        return loadTime;
    }

    @Override
    public IPluginInstance getInstance(String instanceKey) {
        return instances.get(instanceKey);
    }

    @Override
    public List<IPluginInstance> getInstances() {
        return new ArrayList<>(instances.values());
    }

    @Override
    public void start(String pluginGroupId, String pluginArtifactId, String pluginVersion,
                      Map<String, Object> config) {
        checkLoaded();
        createInstance(PluginManagerConstants.DEFAULT_INSTANCE_KEY, config, null);
    }

    @Override
    public void stop() {
        if (instances.containsKey(PluginManagerConstants.DEFAULT_INSTANCE_KEY)) {
            destroyInstance(PluginManagerConstants.DEFAULT_INSTANCE_KEY);
        }
        unload();
    }

    @Override
    public void updateConfig(Map<String, Object> config) {
        // P2-C 裁决（本 plan Closure 记录）：定义默认配置合并更新（实例配置覆盖定义默认）。
        // ACTIVATED 实例热应用（合并视图刷新 + 委托 provider 触发变更通知）；
        // DEACTIVATED 实例缓存配置、下次 activate 应用（本方法不动其合并视图）。
        Map<String, Object> merged = new LinkedHashMap<>(definitionConfig);
        if (config != null) {
            merged.putAll(config);
        }
        this.definitionConfig = merged;
        for (IPluginInstance instance : instances.values()) {
            if (instance instanceof PluginInstanceImpl) {
                ((PluginInstanceImpl) instance).onDefinitionConfigChanged();
            }
        }
    }

    /**
     * 定义级 invokeCommand（§7.1 兼容规则，W4 落地）：实例数=1 → 经该实例路由
     * （路由到实例子容器命令 bean，实例级检查决定 DEACTIVATED 抛 INACTIVE）；
     * 实例数>1 → 抛 {@code ERR_PLUGIN_MULTIPLE_INSTANCES}（无论实例激活状态，
     * 须经 {@link IPluginInstance#invokeCommand} 显式指定实例）；
     * 实例数=0（LOADED 无实例）→ 抛 INACTIVE。
     */
    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                   String fieldSelection,
                                                                   IPluginCancelToken cancelToken) {
        List<IPluginInstance> instances = getInstances();
        if (instances.size() > 1) {
            throw new NopException(ERR_PLUGIN_MULTIPLE_INSTANCES).param(ARG_PLUGIN_ID, pluginId)
                    .param(ARG_INSTANCE_KEYS, instanceKeys(instances));
        }
        if (instances.isEmpty()) {
            throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, pluginId);
        }
        return instances.get(0).invokeCommandAsync(command, args, fieldSelection, cancelToken);
    }

    @Override
    public Map<String, Object> invokeCommand(String command, Map<String, Object> args,
                                             String fieldSelection,
                                             IPluginCancelToken cancelToken) {
        List<IPluginInstance> instances = getInstances();
        if (instances.size() > 1) {
            throw new NopException(ERR_PLUGIN_MULTIPLE_INSTANCES).param(ARG_PLUGIN_ID, pluginId)
                    .param(ARG_INSTANCE_KEYS, instanceKeys(instances));
        }
        if (instances.isEmpty()) {
            throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, pluginId);
        }
        return instances.get(0).invokeCommand(command, args, fieldSelection, cancelToken);
    }

    private static List<String> instanceKeys(List<IPluginInstance> instances) {
        List<String> keys = new ArrayList<>(instances.size());
        for (IPluginInstance instance : instances) {
            keys.add(instance.getInstanceKey());
        }
        return keys;
    }

    /**
     * 为已 LOADED 的定义派生一个激活实例（§7.3）：实例 registry 注册 → 立即激活（子容器 +
     * activator + effect）。同 key 重复创建抛明确异常；激活失败时实例回退 DEACTIVATED
     * 并记录错误（错误带实例 key 参数），实例仍在 registry 中（可 destroy / 重试 activate）。
     */
    public IPluginInstance createInstance(String instanceKey, Map<String, Object> config, IPluginInstance parent) {
        checkLoaded();
        if (instances.containsKey(instanceKey)) {
            throw new NopException(ERR_PLUGIN_INSTANCE_EXISTS)
                    .param(ARG_PLUGIN_ID, pluginId)
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
        PluginInstanceImpl instance = new PluginInstanceImpl(this, instanceKey, config, parent);
        if (instances.putIfAbsent(instanceKey, instance) != null) {
            throw new NopException(ERR_PLUGIN_INSTANCE_EXISTS)
                    .param(ARG_PLUGIN_ID, pluginId)
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
        try {
            // 立即激活（同步等待）：activate 的异常在此重新抛出（错误带实例 key 参数）
            FutureHelper.syncGet(instance.activate());
        } catch (RuntimeException e) {
            // 激活失败：实例保留在 registry（DEACTIVATED + 错误已记录），不残留半激活实例
            throw e;
        }
        return instance;
    }

    public void destroyInstance(String instanceKey) {
        IPluginInstance instance = instances.get(instanceKey);
        if (instance == null) {
            throw new NopException(ERR_PLUGIN_INSTANCE_NOT_FOUND)
                    .param(ARG_PLUGIN_ID, pluginId)
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
        instance.destroy();
    }

    private void checkLoaded() {
        if (state != PluginState.LOADED) {
            throw new NopException(PluginManagerErrors.ERR_PLUGIN_DEFINITION_NOT_LOADED)
                    .param(ARG_PLUGIN_ID, pluginId);
        }
    }

    /**
     * 定义级 registry 的变更入口（destroy 路径使用）。
     */
    public void addInstance(IPluginInstance instance) {
        instances.put(instance.getInstanceKey(), instance);
    }

    public void removeInstance(String instanceKey) {
        instances.remove(instanceKey);
    }
}
