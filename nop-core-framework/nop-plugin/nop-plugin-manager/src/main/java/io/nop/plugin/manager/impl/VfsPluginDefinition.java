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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_MULTIPLE_INSTANCES;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_INSTANCE_KEY;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_INSTANCE_KEYS;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_SPEC_ATTR;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCES_NOT_EMPTY;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_EXISTS;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INVALID_COEFFECT_SPEC;

/**
 * VFS 轨（本地 *.plugin.xml）的定义持有类——manager 侧实现 {@link IPlugin}，
 * 持有经 plugin.xdef 解析出的静态定义（{@link DynamicObject}）与定义级状态
 * （{@link PluginState}：UNLOADED → LOADED，见设计文档 01-architecture-baseline.md §三）。
 *
 * <p>实例 registry 的唯一持有者 = 本类（W5 的 {@code IPluginContext}（由 PluginManagerImpl 实现）
 * 以同一 registry 为数据源，不许另起第二份 registry）。{@link #createInstance} 创建实例并立即激活
 * （注册进 registry；定义级 coeffect 不满足时 no-op 返回 null）；{@link #destroyInstance} 销毁并移除；
 * unload 守卫（有实例禁止 unload）。
 *
 * <p>钉死行为：VFS 轨无 Maven 坐标（groupId/artifactId/version 为 null）；start/stop 已收敛为
 * §7.1 语义（start = load + createInstance(默认 key)；stop = destroyInstance + unload）；
 * P2-C 裁决：定义级 {@link #updateConfig} 更新定义默认配置——ACTIVATED 实例热应用（经委托 provider
 * 触发变更通知），DEACTIVATED 实例缓存、下次 activate 应用（updateConfig 后经 {@link #onConfigChanged}
 * 回调自动触发 manager reconcile）。定义级 invokeCommand（§7.1，W4 落地）：
 * 实例数=1 经该实例路由 / >1 抛 ERR_PLUGIN_MULTIPLE_INSTANCES / =0 抛 INACTIVE。
 *
 * <p>coeffect spec（W5）：{@code requires}（依赖 plugin id 集合）与 {@code if-property}
 * （格式 propName|expectedValue，缺省 expectedValue 视为 true）在构造时（load 解析）提取为
 * 类型化字段并校验；门控评估经 {@link #coeffectEvaluator} 回引（manager 注入，查其他定义实例
 * 状态 + 读全局配置），评估器未注入时保守放行并记录警告日志（非 manager 构造的防御场景）。
 */
public class VfsPluginDefinition implements IPlugin {
    static final Logger LOG = LoggerFactory.getLogger(VfsPluginDefinition.class);

    private final String pluginId;
    private final DynamicObject definition;
    private final BeansModel beansModel;
    private final String activatorName;

    private final Set<String> requires;
    private final String ifPropertyName;
    private final Object ifPropertyExpected;

    private final Map<String, IPluginInstance> instances = new ConcurrentHashMap<>();
    private PluginState state = PluginState.UNLOADED;
    private Timestamp lastChangeTime;
    private Timestamp loadTime;

    private volatile Map<String, Object> definitionConfig = new LinkedHashMap<>();

    private volatile ICoeffectEvaluator coeffectEvaluator;
    private volatile Runnable onConfigChanged;

    public VfsPluginDefinition(String pluginId, DynamicObject definition) {
        this.pluginId = pluginId;
        this.definition = definition;
        this.beansModel = (BeansModel) definition.prop_get("beans");
        this.activatorName = (String) definition.prop_get("activator");
        this.requires = parseRequires(pluginId, definition);
        IfPropertySpec ifSpec = parseIfProperty(pluginId, definition);
        this.ifPropertyName = ifSpec.name;
        this.ifPropertyExpected = ifSpec.expected;
    }

    public String getPluginId() {
        return pluginId;
    }

    /**
     * 定义声明名（plugin.xdef 必填 {@code name="!string"}；防御性兜底：无 name 时以 id 匹配，不可达）。
     */
    public String getName() {
        String name = (String) definition.prop_get("name");
        return name != null ? name : pluginId;
    }

    /**
     * requires 依赖集（csv-set → Set&lt;String&gt;；空集合 = 无依赖）。
     */
    public Set<String> getRequires() {
        return requires;
    }

    /**
     * 定义级 if-property 键（格式 propName|expectedValue 的 propName；未声明为 null）。
     */
    public String getIfPropertyName() {
        return ifPropertyName;
    }

    /**
     * 定义级 if-property 期望值（缺省 expectedValue 视为 Boolean.TRUE）。
     */
    public Object getIfPropertyExpected() {
        return ifPropertyExpected;
    }

    /**
     * W5 数据流缝：manager 在 load 时注入评估器回引（查其他定义实例状态 + 读全局配置）。
     */
    public void setCoeffectEvaluator(ICoeffectEvaluator coeffectEvaluator) {
        this.coeffectEvaluator = coeffectEvaluator;
    }

    /**
     * 定义级 updateConfig 后的回调（P2-C 热应用路径；manager 注入 {@code this::reconcile}）。
     */
    public void setOnConfigChanged(Runnable onConfigChanged) {
        this.onConfigChanged = onConfigChanged;
    }

    /**
     * 定义级 coeffect 门控评估（createInstance / start 使用）：requires 满足 且 定义级 if-property
     * 满足（读全局配置）。评估器缺省未注入时（非 manager 构造的防御场景）保守放行并记录警告日志。
     */
    public boolean isDefinitionGateSatisfied() {
        ICoeffectEvaluator evaluator = this.coeffectEvaluator;
        if (evaluator == null) {
            if (!requires.isEmpty() || ifPropertyName != null) {
                LOG.warn("nop.plugin.coeffect-evaluator-not-injected:pluginId={},gate-bypassed", pluginId);
            }
            return true;
        }
        if (!requires.isEmpty() && !evaluator.isRequiresSatisfied(requires)) {
            return false;
        }
        if (ifPropertyName != null && !evaluator.isGlobalConfigMatched(ifPropertyName, ifPropertyExpected)) {
            return false;
        }
        return true;
    }

    private static Set<String> parseRequires(String pluginId, DynamicObject definition) {
        Object value = definition.prop_get("requires");
        if (value == null) {
            return Collections.emptySet();
        }
        if (value instanceof Set) {
            Set<String> set = new LinkedHashSet<>();
            for (Object item : (Set<?>) value) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    set.add(String.valueOf(item).trim());
                }
            }
            return Collections.unmodifiableSet(set);
        }
        // 防御性兜底（正常由 xdef csv-set 类型解析为 Set，不可达）：字符串按逗号拆分
        String spec = String.valueOf(value).trim();
        if (spec.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String item : spec.split(",")) {
            if (!item.trim().isEmpty()) {
                set.add(item.trim());
            }
        }
        return Collections.unmodifiableSet(set);
    }

    private static IfPropertySpec parseIfProperty(String pluginId, DynamicObject definition) {
        Object value = definition.prop_get("ifProperty");
        if (value == null) {
            return new IfPropertySpec(null, null);
        }
        String spec = String.valueOf(value);
        String name;
        String expected;
        int pos = spec.indexOf('|');
        if (pos < 0) {
            name = spec.trim();
            expected = null; // 缺省 expectedValue 视为 true
        } else {
            name = spec.substring(0, pos).trim();
            expected = spec.substring(pos + 1).trim();
        }
        if (name.isEmpty()) {
            throw new NopException(ERR_PLUGIN_INVALID_COEFFECT_SPEC)
                    .param(ARG_PLUGIN_ID, pluginId)
                    .param(ARG_SPEC_ATTR, "if-property");
        }
        return new IfPropertySpec(name, expected == null || expected.isEmpty() ? Boolean.TRUE : expected);
    }

    private static class IfPropertySpec {
        final String name;
        final Object expected;

        IfPropertySpec(String name, Object expected) {
            this.name = name;
            this.expected = expected;
        }
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
        IPluginInstance instance = createInstance(PluginManagerConstants.DEFAULT_INSTANCE_KEY, config, null);
        // W5 门控兼容路径：定义级 coeffect 不满足时 start 不创建实例并记录日志
        // （与 createInstance 返回 null 语义一致，不抛异常）
        if (instance == null) {
            LOG.warn("nop.plugin.start-gated:pluginId={} (coeffect 定义级条件不满足，不创建实例)", pluginId);
        }
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
        // W5 自动触发 (c)：定义级 updateConfig 后经 manager 注入的回调自动 reconcile
        // （实例级条件的唯一变化源——实例配置在 createInstance 后无独立变更 API）
        Runnable callback = onConfigChanged;
        if (callback != null) {
            callback.run();
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
     *
     * <p>W5 定义级 coeffect 门控（位置钉死：checkLoaded → 重复 key → 门控 → 创建）：
     * 定义级 spec（requires + 定义级 if-property）不满足 → <b>no-op 返回 null</b>（设计 §五）；
     * 重复 key 检查先于门控——门控关闭但实例已存在（先开后关场景）仍抛
     * {@code ERR_PLUGIN_INSTANCE_EXISTS}，不静默返回 null。
     */
    public IPluginInstance createInstance(String instanceKey, Map<String, Object> config, IPluginInstance parent) {
        checkLoaded();
        if (instances.containsKey(instanceKey)) {
            throw new NopException(ERR_PLUGIN_INSTANCE_EXISTS)
                    .param(ARG_PLUGIN_ID, pluginId)
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
        if (!isDefinitionGateSatisfied()) {
            return null;
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
