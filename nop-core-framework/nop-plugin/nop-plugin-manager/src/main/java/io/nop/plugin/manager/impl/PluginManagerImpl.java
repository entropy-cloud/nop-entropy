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
import io.nop.core.resource.deps.DefaultResourceChangeChecker;
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
import io.nop.plugin.support.AbstractPlugin;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_STATE;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_DEFINITION_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_DEFINITION_NOT_LOADED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_FORCE_UNLOAD_NOT_ALLOWED;
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
        // 锁矩阵（裁定①）：全程 reconcileLock——管理卸载与配置驱动的 reconcile 自动激活的
        // 正向/镜像交错（孤儿活容器，C2-004）随 reconcileLock → lifecycleLock 单向序结构性消除
        synchronized (reconcileLock) {
            PluginHolder holder = plugins.get(pluginId);
            if (holder != null) {
                if (holder.plugin.isStateMachineAware()) {
                    // unload 守卫（定义内）：ACTIVATED/中间态时抛明确异常（可先 deactivate 再重试 unload）
                    holder.plugin.unload();
                } else {
                    holder.plugin.stop();
                }
                // classLoader 关闭与 holder 移除保持同侧（成功路径）执行：unload/stop 失败时异常上抛，
                // 此处不执行——关闭后的 PluginClassLoader 无法加载任何类，而残留 holder 会因
                // computeIfAbsent 阻断重新加载，插件将进入不可恢复的"僵尸"状态；失败路径保留
                // classLoader 与注册表状态，保证 unload 可重试
                plugins.remove(pluginId);
                // unload 失败路径（ERR_PLUGIN_NOT_DEACTIVATED）不得清除 name 索引/订阅
                unregisterNameIndex(pluginId, nameOf(holder.plugin));
                unsubscribeGlobalConfig(pluginId);
                IoHelper.safeCloseObject(holder.classLoader);
                reconcile();
            }
        }
    }

    @Override
    public boolean activatePlugin(String pluginId) {
        // 锁矩阵（裁定①）：含 requireLoaded 校验段全程 reconcileLock（校验×卸载 TOCTOU 随
        // 序列化消解）；activate（lifecycleLock）为合法正向获取
        synchronized (reconcileLock) {
            IPlugin plugin = requireLoaded(pluginId);
            boolean result = plugin.activate();
            // 生命周期操作成功路径：activate 后自动 reconcile（依赖本插件的定义级联激活/去激活）
            reconcileAfterLifecycleChange(plugin);
            return result;
        }
    }

    @Override
    public CompletionStage<Void> deactivatePlugin(String pluginId) {
        // 锁矩阵（裁定①）：含 requireLoaded 校验段全程 reconcileLock；deactivate 持锁跨
        // futureCall 任务体全时长（任务体在调用线程执行，与 doReconcile 的 deactivateIfActive
        // syncGet 既有量级一致）
        synchronized (reconcileLock) {
            IPlugin plugin = requireLoaded(pluginId);
            return FutureHelper.futureCall(() -> {
                FutureHelper.syncGet(plugin.deactivate());
                // 生命周期操作成功路径：deactivate 后自动 reconcile（依赖本插件的定义级联去激活/恢复）
                reconcileAfterLifecycleChange(plugin);
                return null;
            });
        }
    }

    private void reconcileAfterLifecycleChange(IPlugin plugin) {
        if (plugin instanceof VfsPluginDefinition) {
            reconcile();
        }
    }

    /**
     * 状态机恢复 API（设计裁定②，C6-002）：中间态 {ACTIVATING, DEACTIVATING, FAILED, LOADED}
     * 的唯一合法出口——经双轨定义层内部恢复方法（绕 unload 守卫，在定义层各自 lifecycleLock 内
     * 原子落定 UNLOADED：残留资源逐段尽力清理、异常逐段留痕不阻断、lastActivationError 保留
     * 供诊断），并完成 manager 侧清理（holder 移除 + name 索引/订阅清理 + classLoader 关闭 +
     * 尾部 reconcile，与 {@link #unloadPlugin(String)} 成功路径同侧语义）。
     *
     * <p>契约：实现类公共方法，不上 IPluginManager 接口（跨模块公共契约面不扩张；仓内无
     * beans.xml 装配点，宿主暴露面随装配落地走 follow-up 公共面走查通道）。ACTIVATED 类型化
     * 拒绝（{@code ERR_PLUGIN_FORCE_UNLOAD_NOT_ALLOWED}）——健康活容器须先走 deactivatePlugin
     * 正常通道，不强行销毁；UNLOADED/不存在幂等返回并记日志。定位 = 卡死态恢复 hatch，
     * 非万能强杀。unload/activate 既有守卫零改动，本方法是中间态唯一不经守卫的出口。
     *
     * @param pluginId 插件 id
     */
    public void forceUnload(String pluginId) {
        synchronized (reconcileLock) {
            PluginHolder holder = plugins.get(pluginId);
            if (holder == null) {
                LOG.info("nop.plugin.force-unload-idempotent:pluginId={},reason=absent", pluginId);
                return;
            }
            PluginState priorState = holder.plugin.getState();
            if (priorState == PluginState.ACTIVATED) {
                // 类型化拒绝（No Silent No-Op）：不强行销毁健康活容器，引导走正常 deactivate 通道
                throw new NopException(ERR_PLUGIN_FORCE_UNLOAD_NOT_ALLOWED)
                        .param(ARG_PLUGIN_ID, pluginId)
                        .param(ARG_PLUGIN_STATE, priorState.name());
            }
            if (priorState == PluginState.UNLOADED) {
                LOG.info("nop.plugin.force-unload-idempotent:pluginId={},reason=already-unloaded", pluginId);
                return;
            }
            // 定义层内部恢复（Vfs 轨 package-private 同包 / AbstractPlugin 轨 public 内部契约——
            // 各自在 lifecycleLock 内原子落定 UNLOADED；LOADED 受理 = 既有 unload 语义等价）。
            // 锁内复核：前置态在 lifecycleLock 外读取，若受理时 ACTIVATING 而等锁期间在途激活已
            // 成功落 ACTIVATED，定义层返回 false——不强杀健康活容器，与前置拒绝同码类型化拒绝、
            // holder 保留（调用方走 deactivatePlugin 正常通道）
            boolean recovered;
            if (holder.plugin instanceof VfsPluginDefinition) {
                recovered = ((VfsPluginDefinition) holder.plugin).recoverToUnloaded();
            } else if (holder.plugin instanceof AbstractPlugin) {
                recovered = ((AbstractPlugin) holder.plugin).recoverToUnloaded();
            } else {
                // 非 aware 插件无六态中间态：既有 unload 语义等价（stop 兼容路径清理）
                holder.plugin.stop();
                recovered = true;
            }
            if (!recovered) {
                throw new NopException(ERR_PLUGIN_FORCE_UNLOAD_NOT_ALLOWED)
                        .param(ARG_PLUGIN_ID, pluginId)
                        .param(ARG_PLUGIN_STATE, PluginState.ACTIVATED.name());
            }
            plugins.remove(pluginId);
            unregisterNameIndex(pluginId, nameOf(holder.plugin));
            unsubscribeGlobalConfig(pluginId);
            IoHelper.safeCloseObject(holder.classLoader);
            // 诊断留痕：pluginId、前态、lastActivationError（保留值；逐段清理异常已由定义层
            // 恢复方法 LOG 留痕，不吞不抛）
            LOG.warn("nop.plugin.force-unload:pluginId={},priorState={},lastActivationError={}",
                    pluginId, priorState, lastActivationErrorOf(holder.plugin));
            // 尾部 reconcile（依赖方拓扑需重新评估；reconcileLock 可重入安全）
            reconcile();
        }
    }

    private static Throwable lastActivationErrorOf(IPlugin plugin) {
        if (plugin instanceof VfsPluginDefinition) {
            return ((VfsPluginDefinition) plugin).getLastActivationError();
        }
        if (plugin instanceof AbstractPlugin) {
            return ((AbstractPlugin) plugin).getLastActivationError();
        }
        return null;
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
     * reconcile 语义（01 §五/§三）：仅插件级 requires/if-property 评估。不动点迭代 + DFS 环检测
     * 保留；批量编排按拓扑序——激活组正拓扑序（提供者先于消费者激活）、去激活组逆拓扑序
     * （消费者先于提供者退出，01 §三跨插件不变量）；同一 pass 混合批次裁定：先处理去激活组
     * （逆拓扑序）再处理激活组（正拓扑序），经不动点迭代收敛。
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
        List<VfsPluginDefinition> topoOrder = topoSortDefs(defs);
        // 防御性迭代上限（级联收敛通常 ≤ 2 轮）：maxIter = LOADED 定义数
        int maxIter = defs.size() + 1;
        for (int iter = 0; iter < maxIter; iter++) {
            boolean changed = false;

            // 去激活组（级联闭包，逆拓扑序执行：消费者先退出）
            Set<String> deactivateGroup = computeDeactivationGroup(defs, cycleMembers);
            for (int i = topoOrder.size() - 1; i >= 0; i--) {
                VfsPluginDefinition def = topoOrder.get(i);
                if (deactivateGroup.contains(def.getPluginId())) {
                    changed |= deactivateIfActive(def);
                }
            }

            // 激活组（正拓扑序执行：提供者先激活；门控在处理时点实时评估——同 pass 内
            // 先激活的提供者即可满足消费者的 requires 腿）
            for (VfsPluginDefinition def : topoOrder) {
                PluginState st = def.getState();
                if (st == PluginState.LOADED || st == PluginState.FAILED) {
                    boolean gate = !cycleMembers.contains(def.getPluginId()) && def.isDefinitionGateSatisfied();
                    if (gate) {
                        changed |= activateIfInactive(def);
                    }
                }
            }

            if (!changed) {
                break;
            }
        }
    }

    /**
     * 去激活组计算（级联闭包，01 §三逆拓扑序执行的前置）：以"假设组内定义已去激活"的
     * 假设态重评估各 ACTIVATED 定义的门控——requires 腿按假设态（组内提供者视为未激活，
     * 其消费者随之入组，保证消费者先于提供者退出），if-property 腿读全局配置（pass 内不变）。
     * 环成员门控强制关闭（ACTIVATED 的环成员直接入组）。
     */
    private Set<String> computeDeactivationGroup(List<VfsPluginDefinition> defs, Set<String> cycleMembers) {
        Set<String> group = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (VfsPluginDefinition def : defs) {
                String id = def.getPluginId();
                if (group.contains(id) || def.getState() != PluginState.ACTIVATED) {
                    continue;
                }
                if (cycleMembers.contains(id) || !isGateSatisfiedHypothetically(def, group)) {
                    group.add(id);
                    changed = true;
                }
            }
        }
        return group;
    }

    /**
     * 假设态门控评估（去激活组级联闭包专用）：requires 腿将 {@code pendingDeactivate} 组内
     * 定义视为未激活；if-property 腿与真实评估一致（全局配置）。
     */
    private boolean isGateSatisfiedHypothetically(VfsPluginDefinition def, Set<String> pendingDeactivate) {
        for (String name : def.getRequires()) {
            if (!hasActivatedPluginByNameExcluding(name, pendingDeactivate)) {
                return false;
            }
        }
        return def.getIfPropertyName() == null || coeffectEvaluator.isGlobalConfigMatched(
                def.getIfPropertyName(), def.getIfPropertyExpected());
    }

    /**
     * 批量编排拓扑序（01 §三）：DFS 后序 = 正拓扑序（依赖提供者在前、消费者在后）。激活组按
     * 本序执行、去激活组按其逆序执行。requires 名解析为全部同名定义（合法多定义）建边；
     * 未加载名不建边（门控评估时不满足）。DFS 根与邻接按 pluginId 排序保证确定性；环成员
     * 无有效拓扑位（任意稳定位置均可——其门控强制关闭，不参与批量激活）。
     */
    private List<VfsPluginDefinition> topoSortDefs(List<VfsPluginDefinition> defs) {
        List<VfsPluginDefinition> sorted = new ArrayList<>(defs);
        sorted.sort(Comparator.comparing(VfsPluginDefinition::getPluginId));
        Map<String, VfsPluginDefinition> byId = new HashMap<>();
        for (VfsPluginDefinition def : sorted) {
            byId.put(def.getPluginId(), def);
        }
        List<VfsPluginDefinition> order = new ArrayList<>(sorted.size());
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        for (VfsPluginDefinition def : sorted) {
            dfsTopo(def, byId, visited, inStack, order);
        }
        return order;
    }

    private void dfsTopo(VfsPluginDefinition def, Map<String, VfsPluginDefinition> byId,
                         Set<String> visited, Set<String> inStack, List<VfsPluginDefinition> order) {
        String id = def.getPluginId();
        if (visited.contains(id)) {
            return;
        }
        visited.add(id);
        inStack.add(id);
        List<String> deps = new ArrayList<>(resolveDepPluginIds(id));
        Collections.sort(deps);
        for (String dep : deps) {
            VfsPluginDefinition depDef = byId.get(dep);
            if (depDef != null && !inStack.contains(dep)) {
                dfsTopo(depDef, byId, visited, inStack, order);
            }
        }
        inStack.remove(id);
        order.add(def);
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

    /**
     * C1-018 辅助：仅 VfsPluginDefinition 持有 name 索引（jar 轨不登记）。
     */
    private String nameOf(IPlugin plugin) {
        return plugin instanceof VfsPluginDefinition ? ((VfsPluginDefinition) plugin).getName() : null;
    }

    /**
     * C1-018（plan 2549 裁定 D）：签名改 `(pluginId, name)`（调用方 reload/unload/uninstall 均持有
     * definition 可传 name）；移除+判空清理在单 map `compute` 域内原子完成——原「全表遍历
     * remove→isEmpty→remove」三步与 registerNameIndex 的 computeIfAbsent 并发时产生孤儿索引集，
     * requires 门控静默失效。
     */
    private void unregisterNameIndex(String pluginId, String name) {
        if (name == null) {
            return;
        }
        namesToPluginIds.compute(name, (k, ids) -> {
            if (ids == null)
                return null;
            ids.remove(pluginId);
            return ids.isEmpty() ? null : ids;
        });
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
        // C2-005（plan 2549 裁定 D）双检①：已装载直接返回（bin 锁外）
        PluginHolder existing = plugins.get(id);
        if (existing != null) {
            return existing.plugin;
        }

        // 锁外构建（原 computeIfAbsent lambda 体搬移）：HTTP 下载 + SHA256 + 类加载 + plugin
        // start 全部不在 CHM bin 锁临界区内——消除「同 bin 串行化/长临界区」面。
        // 2506 裁定③的 try 收窄 + 失败释放语义原样保留（N2 钉零回改义务继承）
        List<URL> urls = resourceResolver.resolvePluginResource(coords);
        PluginClassLoader classLoader = null;
        IPlugin plugin = null;
        boolean mapped = false;
        try {
            classLoader = newPluginClassLoader(urls);
            plugin = classLoader.loadPlugin();
            Map<String, Object> config = pluginConfigProvider.getPluginConfig(coords);
            if (plugin.isStateMachineAware()) {
                plugin.load(config);
            } else {
                plugin.start(coords.getGroupId(), coords.getArtifactId(), coords.getVersion(), config);
            }

            // C2-005 双检②：putIfAbsent 回写（构建全程在锁外）。竞争失败方（另一线程先映射）
            // 按 2506 catch 语义回收插件（aware→unload / 非 aware→stop）后再关自建 classLoader
            // ——只关 classLoader 会丢弃已 start 的插件（R1 MJ-3）
            PluginHolder winner = plugins.putIfAbsent(id, new PluginHolder(plugin, classLoader));
            if (winner != null) {
                if (plugin.isStateMachineAware()) {
                    plugin.unload();
                } else {
                    plugin.stop();
                }
                IoHelper.safeCloseObject(classLoader);
                return winner.plugin;
            }
            mapped = true;
            return plugin;
        } catch (RuntimeException e) {
            // 构建期失败：插件级兜底（非 aware stop / aware unload 幂等）+ classLoader 释放
            // （2506 形态：不建映射、重试重建；N2「映射后失败」腿不经本路径，语义不变）
            if (plugin != null) {
                if (plugin.isStateMachineAware()) {
                    plugin.unload();
                } else {
                    plugin.stop();
                }
            }
            throw e;
        } finally {
            if (!mapped) {
                IoHelper.safeCloseObject(classLoader);
            }
        }
    }

    /**
     * PluginClassLoader 构造工厂钩子（裁定③断言缝）：构造点唯一化，方法体 = 原构造表达式
     * 原样搬移，生产零变化。测试子类（同包 src/test/java/io/nop/plugin/manager/impl/）覆写
     * 捕获实例，断言映射前失败路径已释放（{@code isClosed()}）——映射前失败不建映射，既有
     * {@code classLoaderOf} 反射探针（从 holder 读）恒 null 不可用；既有探针继续服务 N2
     * 「映射后失败」腿，两缝各司其职。钩子为 C2-005（R4-15 lambda 锁纪律重构）重构后仍成立
     * 的缝（调用点随映射函数搬移，不依赖当前 lambda 形态）。
     */
    PluginClassLoader newPluginClassLoader(List<URL> urls) {
        return new PluginClassLoader(urls.toArray(new URL[0]), this.getClass().getClassLoader());
    }

    /**
     * W6 变更检测接线（设计 §六）：遍历 VFS 轨 LOADED/激活态定义，用
     * {@code ResourceComponentManager.checkChanged(resourcePath, lastModified)}（资源真实
     * lastModified 严格比对）检测 → 有变更调 {@link #reloadPlugin}。框架核心不主动起
     * 轮询线程（避免隐式生命周期与测试耦合）；宿主应用可定时调用（docs 记录接线方式）。
     *
     * <p>检查失败显式报告（日志）不吞；检查无变更 no-op（lastModified 相等不触发 reload）。
     * 判据取载体的纯比较入口 {@link DefaultResourceChangeChecker#isLoadMismatch}：stat 型入口
     * 的「写入年龄 &lt; 宽限期保守判 CHANGED」是模型缓存层重读策略（重读廉价、实例身份由缓存门
     * 承载），插件 reload 为 deactivate/unload/load/activate 重量级迁移且定义对象身份对持有方
     * 可观测，刚写入/刚加载的 plugin.xml 在宽限窗内不得被判为变更而伪重载。
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
            if (DefaultResourceChangeChecker.isLoadMismatch(def.getLastModified(), result.getLastModified())) {
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
                unregisterNameIndex(pluginId, oldDef.getName());
                unsubscribeGlobalConfig(pluginId);
                LOG.error("nop.plugin.reload-load-fail:pluginId={}", pluginId, e);
                throw e;
            }
            // C1-019（plan 2549 裁定 D）：成功路径消除「remove→put 窗口」——plugins map 用单
            // `compute` 原子替换（无 null 窗口对无锁 loadPlugin 开放产生双活定义）。name 索引/
            // 订阅沿用原「先摘旧后登新」顺序（reconcile 门控评估依赖正确索引；先登后摘会误删
            // 同 pluginId 的新索引）。
            unregisterNameIndex(pluginId, oldDef.getName());
            unsubscribeGlobalConfig(pluginId);
            newDef.load(definitionConfig);
            registerNameIndex(newDef);
            subscribeGlobalConfig(newDef);
            plugins.compute(pluginId, (k, oldHolder) -> new PluginHolder(newDef, null));

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
            return configValueMatches(actual, expectedValue);
        }
    }

    /**
     * coeffect expectedValue 宽松比较（W5 裁定语义）：字符串与布尔/数字等价比较。规则：
     * <ul>
     *     <li>expected 为 Boolean：actual 为 Boolean 直接比较；为 String 按 Boolean.parseBoolean 比较。</li>
     *     <li>expected 为 Number：actual 为 Number 按数值比较；为 String 按数值解析比较（解析失败不匹配）。</li>
     *     <li>expected 为 String：actual 为 String 直接比较；为 Boolean/Number 按 String.valueOf 比较。</li>
     *     <li>actual 为 null 一律不匹配（无 expected 的缺省语义为 "存在且等于 true"，见 spec 解析）。</li>
     * </ul>
     */
    private static boolean configValueMatches(Object actual, Object expected) {
        if (actual == null) {
            return false;
        }
        if (expected instanceof Boolean) {
            boolean expectedBool = (Boolean) expected;
            if (actual instanceof Boolean) {
                return actual.equals(expected);
            }
            if (actual instanceof String) {
                return Boolean.parseBoolean((String) actual) == expectedBool;
            }
            return false;
        }
        if (expected instanceof Number) {
            double expectedNum = ((Number) expected).doubleValue();
            if (actual instanceof Number) {
                return ((Number) actual).doubleValue() == expectedNum;
            }
            if (actual instanceof String) {
                try {
                    return Double.parseDouble((String) actual) == expectedNum;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return false;
        }
        if (expected instanceof String) {
            if (actual instanceof String) {
                return expected.equals(actual);
            }
            if (actual instanceof Boolean) {
                return expected.equals(Boolean.toString((Boolean) actual));
            }
            if (actual instanceof Number) {
                // 数值字符串与数字等价：优先数值比较（"10" 与 10.0 等价），否则回退 toString 比较
                try {
                    return Double.parseDouble((String) expected) == ((Number) actual).doubleValue();
                } catch (NumberFormatException e) {
                    return expected.equals(actual.toString());
                }
            }
            return false;
        }
        return expected == null || expected.equals(actual);
    }

    /**
     * requires 依赖判定（定义级 ACTIVATED，单激活语义，01 §五）：依赖名（定义 @name）对应的
     * plugin 存在且处于 ACTIVATED——同名多定义沿用 ANY 语义（任一同名定义 ACTIVATED 即满足）。
     */
    private boolean hasActivatedPluginByName(String name) {
        return hasActivatedPluginByNameExcluding(name, null);
    }

    /**
     * requires 依赖判定（假设态变体，去激活组级联闭包专用）：{@code excludedIds} 中的定义视为
     * 未激活（已裁定去激活的提供者不再满足消费者的 requires 腿）。
     */
    private boolean hasActivatedPluginByNameExcluding(String name, Set<String> excludedIds) {
        Set<String> ids = namesToPluginIds.get(name);
        if (ids == null) {
            return false;
        }
        for (String id : ids) {
            if (excludedIds != null && excludedIds.contains(id)) {
                continue;
            }
            PluginHolder holder = plugins.get(id);
            if (holder != null && holder.plugin.getState() == PluginState.ACTIVATED) {
                return true;
            }
        }
        return false;
    }
}
