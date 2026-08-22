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
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.deps.IResourceChangeChecker;
import io.nop.core.resource.deps.ResourceChangeCheckResult;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginContext;
import io.nop.plugin.api.NopPluginConstants;
import io.nop.plugin.api.PluginState;
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
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_DEFINITION_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_DEFINITION_NOT_LOADED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_RELOAD_NOT_SUPPORTED;

/**
 * 双轨来源（设计文档 01-architecture-baseline.md §二）的统一编排：
 * <ul>
 *     <li><b>uber jar 轨</b>：id 为 Maven 坐标（{@link ArtifactCoordinates#parse} 成功），
 *     保留 resolver → {@link PluginClassLoader} → plugin.json → 实例化链路（类隔离不变）。
 *     jar 轨契约：无 plugin.xdef/VFS 定义载体 → 门控恒为空集（无条件激活）、
 *     activator 未声明则跳过激活回调（子容器启动即完成激活）。</li>
 *     <li><b>VFS 轨</b>：id 为本地 VFS 路径，经 plugin.xdef（{@link DslModelParser}）解析
 *     *.plugin.xml 为静态定义，由 {@link VfsPluginDefinition}（定义持有类）承载单层六态状态机。</li>
 * </ul>
 *
 * <p>状态机感知路由（§7.1 兼容机制）：aware → 单层状态机（loadPlugin 只到 LOADED，
 * 不调用 start；reconcile 按门控自动激活）；非 aware → 兼容路径（loadPlugin 执行旧 start
 * 语义，失败时 stop 清理、entry 不写入 map）。
 *
 * <p>本类实现 {@link IPluginContext}（plugin registry + coeffect reconcile 的唯一宿主，
 * 以 {@code plugins} map 为单一数据源，不另起第二份 registry）。reconcile 触发点 =
 * 显式调用 + 生命周期成功路径（load/unload/activate/deactivate）+ 全局配置订阅
 * （定义级 if-property 键）+ 定义级 updateConfig 回调。
 */
public class PluginManagerImpl implements IPluginManager, IPluginContext {
    static final Logger LOG = LoggerFactory.getLogger(PluginManagerImpl.class);

    private final Map<String, PluginHolder> plugins = new ConcurrentHashMap<>();

    /**
     * name → pluginIds 索引（requires 依赖身份按定义 @name 解析；同名多个定义合法）。
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
     * 全局配置 provider（自动触发接线）：定义级 if-property 求值 + 变更订阅的来源。
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
            // 生命周期操作成功路径：load 后自动 reconcile（门控满足的定义自动激活）
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
                    // unload 守卫（定义内）：ACTIVATED/中间态时抛明确异常（可先 deactivate 再重试 unload）
                    holder.plugin.unload();
                } else {
                    holder.plugin.stop();
                }
                plugins.remove(pluginId);
                // unload 失败路径（ERR_PLUGIN_NOT_DEACTIVATED）不得清除 name 索引/订阅
                unregisterNameIndex(pluginId);
                unsubscribeGlobalConfig(pluginId);
            } finally {
                IoHelper.safeCloseObject(holder.classLoader);
            }
            reconcile();
        }
    }

    @Override
    public boolean activatePlugin(String pluginId) {
        IPlugin plugin = requireLoaded(pluginId);
        boolean result = plugin.activate();
        // 生命周期操作成功路径：activate 后自动 reconcile（依赖本插件的定义级联激活/去激活）
        reconcileAfterLifecycleChange(plugin);
        return result;
    }

    @Override
    public CompletionStage<Void> deactivatePlugin(String pluginId) {
        IPlugin plugin = requireLoaded(pluginId);
        return FutureHelper.futureCall(() -> {
            FutureHelper.syncGet(plugin.deactivate());
            // 生命周期操作成功路径：deactivate 后自动 reconcile（依赖本插件的定义级联去激活/恢复）
            reconcileAfterLifecycleChange(plugin);
            return null;
        });
    }

    private void reconcileAfterLifecycleChange(IPlugin plugin) {
        if (plugin instanceof VfsPluginDefinition) {
            reconcile();
        }
    }

    @Override
    public IPlugin getPlugin(String pluginId) {
        PluginHolder holder = plugins.get(pluginId);
        return holder == null ? null : holder.plugin;
    }

    @Override
    public List<IPlugin> getLoadedPlugins() {
        return plugins.values().stream().map(holder -> holder.plugin).collect(Collectors.toList());
    }

    @Override
    public void reconcilePlugins() {
        reconcile();
    }

    private IPlugin requireLoaded(String pluginId) {
        IPlugin plugin = getPlugin(pluginId);
        if (plugin == null) {
            throw new NopException(ERR_PLUGIN_DEFINITION_NOT_LOADED).param(ARG_PLUGIN_ID, pluginId);
        }
        return plugin;
    }

    /**
     * IPluginContext 实现（registry + coeffect reconcile，单一数据源 = {@code plugins} map）。
     */
    @Override
    public Collection<IPlugin> allPlugins() {
        return plugins.values().stream().map(holder -> holder.plugin).collect(Collectors.toList());
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

    /**
     * reconcile 过渡语义（R1）：仅定义级 requires/if-property 评估——对每个 VFS 轨定义，
     * 门控满足且未激活（LOADED/FAILED）→ activate；门控不满足且 ACTIVATED → deactivate。
     * 不动点迭代 + 环检测保留；跨插件去激活拓扑序增强归 R2。
     *
     * <p>自动激活失败不抛出（置 FAILED + 记录错误，下轮重试）；失败计数达阈值后暂停该定义的
     * 自动激活（仅显式 activatePlugin 可恢复）。jar 轨（非 VfsPluginDefinition）不参与。
     */
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
                PluginState st = def.getState();
                boolean gate = !cycleMembers.contains(def.getPluginId()) && def.isDefinitionGateSatisfied();
                if (gate && (st == PluginState.LOADED || st == PluginState.FAILED)) {
                    changed |= activateIfInactive(def);
                } else if (!gate && st == PluginState.ACTIVATED) {
                    changed |= deactivateIfActive(def);
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    /**
     * 自动激活（reconcile 专用）：失败不抛出（定义已置 FAILED + 记录错误），
     * 失败计数达阈值后暂停自动激活（仅显式 activatePlugin 可恢复）。
     */
    private boolean activateIfInactive(VfsPluginDefinition def) {
        if (def.getState() == PluginState.ACTIVATED) {
            return false;
        }
        if (def.isAutoActivationPaused()) {
            return false;
        }
        try {
            return def.activate();
        } catch (RuntimeException | Error e) {
            LOG.error("nop.plugin.reconcile-activate-fail:pluginId={}", def.getPluginId(), e);
            return false;
        }
    }

    private boolean deactivateIfActive(VfsPluginDefinition def) {
        if (def.getState() != PluginState.ACTIVATED) {
            return false;
        }
        try {
            FutureHelper.syncGet(def.deactivate());
            return true;
        } catch (RuntimeException | Error e) {
            LOG.error("nop.plugin.reconcile-deactivate-fail:pluginId={}", def.getPluginId(), e);
            return false;
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
     * id 判别（本 plan 裁定 + Windows 修复）：{@link ArtifactCoordinates#parse} 成功 → Maven 坐标 →
     * uber jar 轨；parse 抛 IllegalArgumentException 视为非坐标 → VFS 路径轨。Windows 盘符绝对路径
     * （file:C:\path）含两个冒号会被 parse 误判为坐标，Maven 坐标不允许出现路径分隔符，故含 '/' 或
     * '\' 的 id 一律归入 VFS 路径轨。
     */
    private ArtifactCoordinates tryParseCoordinates(String pluginId) {
        if (pluginId.indexOf('/') >= 0 || pluginId.indexOf('\\') >= 0) {
            return null;
        }
        try {
            return ArtifactCoordinates.parse(pluginId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private IPlugin loadPluginFromVfs(String pluginId) {
        return plugins.computeIfAbsent(pluginId, id -> {
            VfsPluginDefinition plugin = loadVfsDefinition(id);
            plugin.load(Collections.emptyMap());
            registerNameIndex(plugin);
            subscribeGlobalConfig(plugin);
            return new PluginHolder(plugin, null);
        }).plugin;
    }

    /**
     * 解析 plugin.xml 为定义对象（load/reload 共用）：DslModelParser 管线 + 评估器回引
     * + updateConfig 回调 + 资源真实 lastModified 记录（变更检测比对源，非 getLastChangeTime
     * 时钟语义——DefaultResourceChangeChecker 是 lastModified 严格比对）。
     */
    private VfsPluginDefinition loadVfsDefinition(String pluginId) {
        IResource resource = VirtualFileSystem.instance().getResource(pluginId);
        if (!resource.exists()) {
            throw new NopException(ERR_PLUGIN_DEFINITION_NOT_FOUND).param(ARG_PLUGIN_ID, pluginId);
        }
        DynamicObject definition;
        try {
            definition = (DynamicObject) new DslModelParser(NopPluginConstants.PLUGIN_XDEF_PATH)
                    .parseFromVirtualPath(pluginId);
        } catch (NopException e) {
            throw e.param(ARG_PLUGIN_ID, pluginId);
        }
        VfsPluginDefinition plugin = new VfsPluginDefinition(pluginId, definition);
        // 数据流缝：注入评估器回引（查其他定义激活状态 + 读全局配置）与 updateConfig 回调
        plugin.setCoeffectEvaluator(coeffectEvaluator);
        plugin.setOnConfigChanged(this::reconcile);
        plugin.setLastModified(resource.lastModified());
        return plugin;
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
     * W6 变更检测接线（设计 §六）：遍历 VFS 轨 LOADED/激活态定义，用
     * {@code ResourceComponentManager.checkChanged(resourcePath, lastModified)}（资源真实
     * lastModified 严格比对）检测 → 有变更调 {@link #reloadPlugin}。框架核心不主动起
     * 轮询线程（避免隐式生命周期与测试耦合）；宿主应用可定时调用（docs 记录接线方式）。
     *
     * <p>检查失败显式报告（日志）不吞；检查无变更 no-op（lastModified 相等不触发 reload）。
     */
    public void checkChangedAndReload() {
        List<VfsPluginDefinition> defs = collectVfsDefinitions();
        for (VfsPluginDefinition def : defs) {
            PluginState st = def.getState();
            if (st == PluginState.UNLOADED) {
                continue;
            }
            ResourceChangeCheckResult result;
            try {
                result = ((IResourceChangeChecker) ResourceComponentManager.instance())
                        .checkChanged(def.getPluginId(), def.getLastModified());
            } catch (RuntimeException e) {
                LOG.error("nop.plugin.check-changed-fail:pluginId={}", def.getPluginId(), e);
                continue;
            }
            if (result.isChanged()) {
                LOG.info("nop.plugin.resource-changed:pluginId={}", def.getPluginId());
                reloadPlugin(def.getPluginId());
            }
        }
    }

    /**
     * HMR 热重载（§六，单激活过渡语义）：若激活态先 deactivate（回退 effect + 容器 stop）→
     * unload（丢弃旧定义）→ load（重解析 plugin.xml，重放定义级 updateConfig 累积值）→
     * reconcile（重新门控：门控满足自动激活）。无实例快照重建（无实例可重建——唯一重放的
     * 是定义级配置域累积值）。
     *
     * <p>失败路径（No Silent No-Op）：deactivate/unload 段失败 → 异常上抛，定义状态可观测；
     * load 段失败 → 定义从 map 移除（可重新 loadPlugin 恢复），不残留不可重载的陈旧定义。
     */
    @Override
    public void reloadPlugin(String pluginId) {
        synchronized (reconcileLock) {
            PluginHolder holder = plugins.get(pluginId);
            if (holder == null) {
                throw new NopException(ERR_PLUGIN_DEFINITION_NOT_LOADED).param(ARG_PLUGIN_ID, pluginId);
            }
            if (!(holder.plugin instanceof VfsPluginDefinition)) {
                // jar 轨（uber jar 不可编辑，设计 §六 HMR 面向本地/开发场景）显式失败
                throw new NopException(ERR_PLUGIN_RELOAD_NOT_SUPPORTED)
                        .param(ARG_PLUGIN_ID, pluginId);
            }
            VfsPluginDefinition oldDef = (VfsPluginDefinition) holder.plugin;
            if (oldDef.getState() == PluginState.UNLOADED) {
                throw new NopException(ERR_PLUGIN_DEFINITION_NOT_LOADED).param(ARG_PLUGIN_ID, pluginId);
            }

            // 定义级配置域快照（unload 会清空 definitionConfig，须先采集；含 updateConfig 累积值）
            Map<String, Object> definitionConfig = oldDef.getDefinitionConfig();

            // 1. 若激活态先 deactivate（回退 effect；失败 → 异常上抛，定义保持可观测状态）
            if (oldDef.getState() == PluginState.ACTIVATED) {
                FutureHelper.syncGet(oldDef.deactivate());
            }

            // 2. unload（已未激活；失败 → 异常上抛）
            oldDef.unload();

            // 3. load（重解析 plugin.xml → 新定义对象，重放定义级配置域累积值；失败 → 定义
            //    从 map 移除（可重新 loadPlugin 恢复），不残留不可重载的陈旧 UNLOADED 定义）
            VfsPluginDefinition newDef;
            try {
                newDef = loadVfsDefinition(pluginId);
            } catch (RuntimeException e) {
                plugins.remove(pluginId);
                unregisterNameIndex(pluginId);
                unsubscribeGlobalConfig(pluginId);
                LOG.error("nop.plugin.reload-load-fail:pluginId={}", pluginId, e);
                throw e;
            }
            plugins.remove(pluginId);
            unregisterNameIndex(pluginId);
            unsubscribeGlobalConfig(pluginId);
            newDef.load(definitionConfig);
            registerNameIndex(newDef);
            subscribeGlobalConfig(newDef);
            plugins.put(pluginId, new PluginHolder(newDef, null));

            // 4. reconcile（重新门控激活；reconcileLock 可重入）
            reconcile();
        }
    }

    /**
     * 数据流缝（回引实现）：查其他定义激活状态 + 读全局配置。
     */
    private class CoeffectEvaluatorImpl implements ICoeffectEvaluator {
        @Override
        public boolean isRequiresSatisfied(Set<String> requires) {
            for (String dep : requires) {
                if (!hasActivatedPluginByName(dep)) {
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
    }

    /**
     * requires 依赖判定（定义级 ACTIVATED，单激活语义）：依赖名（定义 @name）对应的 plugin
     * 存在且处于 ACTIVATED——原"至少一个实例 ACTIVATED"的定义级判定改造（多实例机制已移除）。
     */
    private boolean hasActivatedPluginByName(String name) {
        Set<String> ids = namesToPluginIds.get(name);
        if (ids == null) {
            return false;
        }
        for (String id : ids) {
            PluginHolder holder = plugins.get(id);
            if (holder != null && holder.plugin.getState() == PluginState.ACTIVATED) {
                return true;
            }
        }
        return false;
    }
}
