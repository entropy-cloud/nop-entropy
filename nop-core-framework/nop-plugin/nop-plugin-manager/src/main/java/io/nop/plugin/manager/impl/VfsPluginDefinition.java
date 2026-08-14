package io.nop.plugin.manager.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.model.object.DynamicObject;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.PluginState;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;

/**
 * VFS 轨（本地 *.plugin.xml）的定义持有类——manager 侧实现 {@link IPlugin}，
 * 持有经 plugin.xdef 解析出的静态定义（{@link DynamicObject}）与定义级状态
 * （{@link PluginState}：UNLOADED → LOADED，见设计文档 01-architecture-baseline.md §三）。
 *
 * <p>实例级 registry 骨架：{@link #getInstances()} / {@link #getInstance(String)} 返回
 * 真实空列表/空（W3 createInstance 落地后填充，本类保证"非空壳"语义成立——registry 是
 * 真实数据结构而非占位返回）。
 *
 * <p>钉死行为：VFS 轨无 Maven 坐标（groupId/artifactId/version 为 null）；
 * {@link #start}/{@link #stop} 按 §7.1 语义（start = load + createInstance）在 createInstance
 * 未落地（W3）前显式失败；LOADED 且 0 实例时 invokeCommand 抛 INACTIVE（§7.1，W4 完善路由）。
 */
public class VfsPluginDefinition implements IPlugin {
    private final String pluginId;
    private final DynamicObject definition;

    private final Map<String, IPluginInstance> instances = new ConcurrentHashMap<>();
    private PluginState state = PluginState.UNLOADED;
    private Timestamp lastChangeTime;
    private Timestamp loadTime;

    public VfsPluginDefinition(String pluginId, DynamicObject definition) {
        this.pluginId = pluginId;
        this.definition = definition;
    }

    public String getPluginId() {
        return pluginId;
    }

    public DynamicObject getDefinition() {
        return definition;
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
        this.state = PluginState.LOADED;
        this.loadTime = CoreMetrics.currentTimestamp();
        this.lastChangeTime = this.loadTime;
    }

    @Override
    public void unload() {
        this.state = PluginState.UNLOADED;
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
        throw new UnsupportedOperationException(
                "start is not supported for state-machine-aware plugin before createInstance is implemented (W3); use load(config) instead");
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException(
                "stop is not supported for state-machine-aware plugin before destroyInstance is implemented (W3); use unload() instead");
    }

    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                   String fieldSelection,
                                                                   IPluginCancelToken cancelToken) {
        throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, pluginId);
    }

    @Override
    public Map<String, Object> invokeCommand(String command, Map<String, Object> args,
                                             String fieldSelection,
                                             IPluginCancelToken cancelToken) {
        throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, pluginId);
    }

    /**
     * 定义级 registry 的变更入口（W3 createInstance/destroyInstance 使用）。
     */
    public void addInstance(IPluginInstance instance) {
        instances.put(instance.getInstanceKey(), instance);
    }

    public void removeInstance(String instanceKey) {
        instances.remove(instanceKey);
    }
}
