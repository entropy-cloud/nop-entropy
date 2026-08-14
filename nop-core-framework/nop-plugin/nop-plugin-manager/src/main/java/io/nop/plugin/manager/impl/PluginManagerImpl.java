package io.nop.plugin.manager.impl;

import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.IoHelper;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginContext;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.InstanceState;
import io.nop.plugin.api.NopPluginConstants;
import io.nop.plugin.manager.IPluginConfigProvider;
import io.nop.plugin.manager.IPluginManager;
import io.nop.plugin.manager.classloader.PluginClassLoader;
import io.nop.plugin.manager.resolver.IPluginResourceResolver;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>W5：本类实现 {@link IPluginContext}（实例 registry + coeffect reconcile 的唯一宿主，
 * 以 {@code plugins} map 与各定义 registry 为单一数据源，不另起第二份 registry）。
 * reconcile 触发点 = 显式调用 + 生命周期成功路径（load/unload/create/destroy）+
 * 全局配置订阅（定义级 if-property 键）+ 定义级 updateConfig 回调。
 */
public class PluginManagerImpl implements IPluginManager, IPluginContext {
    static final Logger LOG = LoggerFactory.getLogger(PluginManagerImpl.class);

    private final Map<String, PluginHolder> plugins = new ConcurrentHashMap<>();

    /**
     * name → pluginIds 索引（W5 requires 依赖身份按定义 @name 解析；同名多个定义合法）。
     */
    private final Map<String, Set<String>> namesToPluginIds = new ConcurrentHashMap<>();

    /**
     * 定义级 if-property 键的全局配置订阅（pluginId → cleanup；SimpleConfigProvider 返回 null）。
     */
    private final Map<String, Runnable> configSubscriptions = new ConcurrentHashMap<>();

    private IPluginResourceResolver resourceResolver;
    private IPluginConfigProvider pluginConfigProvider;
    private IConfigProvider globalConfigProvider;

    private final Object reconcileLock = new Object();
    private boolean reconcileInFlight;
    private boolean reconcileDirty;
    private volatile Set<String> unresolvedPluginIds = Collections.emptySet();

    private final ICoeffectEvaluator coeffectEvaluator = new CoeffectEvaluatorImpl();

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

    /**
     * 全局配置 provider（W5 自动触发接线）：定义级 if-property 求值 + 变更订阅的来源。
     * 名称区别于 {@link #setPluginConfigProvider}（jar 轨配置）；缺省
     * {@code AppConfig.getConfigProvider()}。生产语义 = 配置源轮询驱动的变更事件；
     * 测试可注入可控 provider 验证订阅接线。
     */
    @Inject
    public void setGlobalConfigProvider(IConfigProvider globalConfigProvider) {
        this.globalConfigProvider = globalConfigProvider;
    }

    private IConfigProvider getGlobalConfigProvider() {
        IConfigProvider provider = globalConfigProvider;
        return provider != null ? provider : AppConfig.getConfigProvider();
    }

    @Override
    public IPlugin loadPlugin(String pluginId) {
        ArtifactCoordinates coords = tryParseCoordinates(pluginId);
        IPlugin plugin;
        if (coords != null)
            plugin = loadPluginFromJar(coords);
        else
            plugin = loadPluginFromVfs(pluginId);
        if (plugin instanceof VfsPluginDefinition) {
            // 生命周期操作成功路径：load 后自动 reconcile（状态收敛；不自动创建实例）
            reconcile();
        }
        return plugin;
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
                // unload 失败路径（ERR_PLUGIN_INSTANCES_NOT_EMPTY）不得清除 name 索引/订阅
                unregisterNameIndex(pluginId);
                unsubscribeGlobalConfig(pluginId);
            } finally {
                IoHelper.safeCloseObject(holder.classLoader);
            }
            reconcile();
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
        IPluginInstance instance = ((VfsPluginDefinition) plugin).createInstance(instanceKey, config, parent);
        // 生命周期操作成功路径：create 后自动 reconcile（级联激活依赖本实例的下游定义实例）
        reconcile();
        return instance;
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
        // 生命周期操作成功路径：destroy 后自动 reconcile（级联去激活依赖本实例的下游定义实例）
        reconcile();
    }

    @Override
    public List<IPlugin> getLoadedPlugins() {
        return plugins.values().stream().map(holder -> holder.plugin).collect(Collectors.toList());
    }

    @Override
    public void reconcileInstances() {
        reconcile();
    }

    /**
     * IPluginContext 实现（registry + coeffect reconcile，单一数据源 = {@code plugins} map）。
     */
    @Override
    public Collection<IPluginInstance> allInstances() {
        List<IPluginInstance> all = new ArrayList<>();
        for (PluginHolder holder : plugins.values()) {
            all.addAll(holder.plugin.getInstances());
        }
        return all;
    }

    @Override
    public void reconcile() {
        synchronized (reconcileLock) {
            if (reconcileInFlight) {
                // 重入（如配置订阅回调在 reconcile 执行中被触发）：置 dirty，本轮结束后再跑一轮（不丢通知）
                reconcileDirty = true;
                return;
            }
            reconcileInFlight = true;
            try {
                do {
                    reconcileDirty = false;
                    doReconcile();
                } while (reconcileDirty);
            } finally {
                reconcileInFlight = false;
            }
        }
    }

    /**
     * 最近一次 reconcile 的静态环检测报告（非接口方法，不进 IPluginManager/IPluginContext——
     * 保持接口最小；测试经具体类断言报告内容）。
     */
    public Set<String> getUnresolvedPluginIds() {
        return unresolvedPluginIds;
    }

    private void doReconcile() {
        List<VfsPluginDefinition> defs = collectVfsDefinitions();
        Set<String> cycleMembers = detectCycles(defs);
        this.unresolvedPluginIds = cycleMembers;
        if (!cycleMembers.isEmpty()) {
            LOG.warn("nop.plugin.reconcile-unresolved:cycles={}", cycleMembers);
        }
        // 防御性迭代上限（级联收敛通常 ≤ 2 轮）：maxIter = LOADED 定义数
        int maxIter = defs.size() + 1;
        for (int iter = 0; iter < maxIter; iter++) {
            boolean changed = false;
            for (VfsPluginDefinition def : defs) {
                boolean gate = !cycleMembers.contains(def.getPluginId()) && def.isDefinitionGateSatisfied();
                for (IPluginInstance instance : def.getInstances()) {
                    if (!gate) {
                        changed |= deactivateIfActive(def, instance);
                        continue;
                    }
                    if (evaluateInstanceCondition(def, instance)) {
                        changed |= activateIfInactive(def, instance);
                    } else {
                        changed |= deactivateIfActive(def, instance);
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    private List<VfsPluginDefinition> collectVfsDefinitions() {
        List<VfsPluginDefinition> defs = new ArrayList<>();
        for (PluginHolder holder : plugins.values()) {
            if (holder.plugin instanceof VfsPluginDefinition) {
                defs.add((VfsPluginDefinition) holder.plugin);
            }
        }
        return defs;
    }

    /**
     * 实例级条件 = 实例合并视图优先、未命中回退全局配置（与 InstanceConfigProvider 合并视图语义一致）。
     */
    private boolean evaluateInstanceCondition(VfsPluginDefinition def, IPluginInstance instance) {
        String propName = def.getIfPropertyName();
        if (propName == null) {
            return true;
        }
        return coeffectEvaluator.isInstanceConfigMatched(instance, propName, def.getIfPropertyExpected());
    }

    /**
     * 自动激活（reconcile 专用）：失败不抛出（已由实例回退 DEACTIVATED + 记录错误），
     * 失败计数达阈值后暂停自动激活（仅显式 instance.activate() 可恢复）。
     */
    private boolean activateIfInactive(VfsPluginDefinition def, IPluginInstance instance) {
        if (instance.getState() == InstanceState.ACTIVATED) {
            return false;
        }
        if (instance instanceof PluginInstanceImpl && ((PluginInstanceImpl) instance).isAutoActivationPaused()) {
            return false;
        }
        try {
            FutureHelper.syncGet(instance.activate());
            return true;
        } catch (RuntimeException | Error e) {
            LOG.error("nop.plugin.reconcile-activate-fail:pluginId={},instanceKey={}",
                    def.getPluginId(), instance.getInstanceKey(), e);
            return false;
        }
    }

    private boolean deactivateIfActive(VfsPluginDefinition def, IPluginInstance instance) {
        if (instance.getState() != InstanceState.ACTIVATED) {
            return false;
        }
        try {
            FutureHelper.syncGet(instance.deactivate());
            return true;
        } catch (RuntimeException | Error e) {
            LOG.error("nop.plugin.reconcile-deactivate-fail:pluginId={},instanceKey={}",
                    def.getPluginId(), instance.getInstanceKey(), e);
            return false;
        }
    }

    /**
     * 静态环检测（DFS 三色标记）：环成员强制门控关闭（reconcile 视其定义级条件不满足），
     * 报告环成员 pluginId 集合。
     */
    private Set<String> detectCycles(List<VfsPluginDefinition> defs) {
        Map<String, Integer> color = new HashMap<>();
        Set<String> cycleMembers = new HashSet<>();
        for (VfsPluginDefinition def : defs) {
            if (color.getOrDefault(def.getPluginId(), 0) == 0) {
                dfsCycle(def.getPluginId(), color, cycleMembers, new ArrayList<>());
            }
        }
        return cycleMembers;
    }

    private void dfsCycle(String pluginId, Map<String, Integer> color, Set<String> cycleMembers,
                          List<String> path) {
        color.put(pluginId, 1);
        path.add(pluginId);
        for (String dep : resolveDepPluginIds(pluginId)) {
            int c = color.getOrDefault(dep, 0);
            if (c == 0) {
                dfsCycle(dep, color, cycleMembers, path);
            } else if (c == 1) {
                // 回边：路径中从 dep 到当前节点的段均为环成员
                int idx = path.indexOf(dep);
                if (idx >= 0) {
                    cycleMembers.addAll(path.subList(idx, path.size()));
                }
            }
        }
        path.remove(path.size() - 1);
        color.put(pluginId, 2);
    }

    private List<String> resolveDepPluginIds(String pluginId) {
        PluginHolder holder = plugins.get(pluginId);
        if (holder == null || !(holder.plugin instanceof VfsPluginDefinition)) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (String name : ((VfsPluginDefinition) holder.plugin).getRequires()) {
            Set<String> found = namesToPluginIds.get(name);
            if (found != null) {
                ids.addAll(found);
            }
        }
        return ids;
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
            // W5 数据流缝：注入评估器回引（查其他定义实例状态 + 读全局配置）与 updateConfig 回调
            plugin.setCoeffectEvaluator(coeffectEvaluator);
            plugin.setOnConfigChanged(this::reconcile);
            plugin.load(Collections.emptyMap());
            registerNameIndex(plugin);
            subscribeGlobalConfig(plugin);
            return new PluginHolder(plugin, null);
        }).plugin;
    }

    private void registerNameIndex(VfsPluginDefinition plugin) {
        String name = plugin.getName();
        if (name == null) {
            return;
        }
        namesToPluginIds.computeIfAbsent(name, k -> ConcurrentHashMap.newKeySet()).add(plugin.getPluginId());
    }

    private void unregisterNameIndex(String pluginId) {
        for (Map.Entry<String, Set<String>> entry : namesToPluginIds.entrySet()) {
            if (entry.getValue().remove(pluginId) && entry.getValue().isEmpty()) {
                namesToPluginIds.remove(entry.getKey());
            }
        }
    }

    /**
     * 全局配置订阅（定义级 if-property 键）：变更回调自动触发 reconcile。
     * 重复键订阅无害（reconcile 幂等可重入）；SimpleConfigProvider 的 subscribeChange
     * 返回 null（生产语义 = DefaultConfigProvider.applyChange 驱动的变更事件）。
     */
    private void subscribeGlobalConfig(VfsPluginDefinition plugin) {
        String propName = plugin.getIfPropertyName();
        if (propName == null) {
            return;
        }
        Runnable cleanup = getGlobalConfigProvider().subscribeChange(propName,
                (provider, oldValues) -> reconcile());
        if (cleanup != null) {
            configSubscriptions.put(plugin.getPluginId(), cleanup);
        }
    }

    private void unsubscribeGlobalConfig(String pluginId) {
        Runnable cleanup = configSubscriptions.remove(pluginId);
        if (cleanup != null) {
            cleanup.run();
        }
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

    /**
     * W5 数据流缝（回引实现）：查其他定义实例状态 + 读全局配置。
     */
    private class CoeffectEvaluatorImpl implements ICoeffectEvaluator {
        @Override
        public boolean isRequiresSatisfied(Set<String> requires) {
            for (String dep : requires) {
                if (!hasActivatedInstanceByName(dep)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean isGlobalConfigMatched(String propName, Object expectedValue) {
            Object actual = getGlobalConfigProvider().getConfigValue(propName, null);
            return CoeffectConfigHelper.matches(actual, expectedValue);
        }

        @Override
        public boolean isInstanceConfigMatched(IPluginInstance instance, String propName, Object expectedValue) {
            // 实例级求值数据源 = 实时合并视图（定义默认 + 实例配置；DEACTIVATED 实例的
            // getConfig() 为缓存快照，不用于求值——否则 updateConfig 往返无法驱动重激活）
            Map<String, Object> view = instance instanceof PluginInstanceImpl
                    ? ((PluginInstanceImpl) instance).getCoeffectConfigView()
                    : instance.getConfig();
            if (view.containsKey(propName)) {
                return CoeffectConfigHelper.matches(view.get(propName), expectedValue);
            }
            return isGlobalConfigMatched(propName, expectedValue);
        }
    }

    private boolean hasActivatedInstanceByName(String name) {
        Set<String> ids = namesToPluginIds.get(name);
        if (ids == null) {
            return false;
        }
        for (String id : ids) {
            PluginHolder holder = plugins.get(id);
            if (holder != null) {
                for (IPluginInstance instance : holder.plugin.getInstances()) {
                    if (instance.getState() == InstanceState.ACTIVATED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
