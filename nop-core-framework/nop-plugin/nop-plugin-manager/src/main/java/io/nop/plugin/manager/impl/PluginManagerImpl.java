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
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.InstanceState;
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
import java.util.stream.Collectors;

import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_DEFINITION_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_INSTANCE_KEY;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_DEFINITION_NOT_LOADED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INSTANCE_NOT_SUPPORTED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_RELOAD_NOT_SUPPORTED;

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

    /**
     * W6 parent 层级：全局父子映射（instance → children 集合）——跨定义 parent 链
     * （IPluginManager.createInstance 的 parent 可为任意定义实例）必须可观测：
     * 定义持有类级索引看不见其他定义的实例（会静默降级级联）。createInstance(parent)
     * 时登记、destroy 时移除（经 PluginInstanceImpl 的 onDestroyed 回调同步清理）。
     */
    private final Map<IPluginInstance, Set<IPluginInstance>> parentToChildren = new ConcurrentHashMap<>();

    /**
     * P2-A HMR 配置快照（reloadPlugin 持有）：reload 流程成功结束后失效（下次 reload 重新采集）；
     * destroy/unload/load 段失败时保留（调用方可重试 reload）；重建段失败时未处理快照项转 pending。
     */
    private volatile ReloadSnapshot lastReloadSnapshot;

    /**
     * 门控未满足（W5 语义 createInstance 返回 null）暂缓重建的实例快照项（P2-A 快照恢复语义）：
     * reconcile 触发点重试重建；父实例仍 pending / 已不存在时子项跟随 pending / 显式错误丢弃。
     */
    private final List<SnapshotInstance> pendingRebuilds = new ArrayList<>();

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
        if (instance == null) {
            // 门控 null（W5 语义 no-op）：无状态变化，不触发 reconcile——
            // 否则 pending 重试路径（reconcile → retryPendingRebuilds → createInstance → reconcile）
            // 会形成无限循环（每次重试都置 dirty）
            return null;
        }
        if (instance instanceof PluginInstanceImpl) {
            // W6 父子登记（manager 级全局映射 + 实例级 children 已在构造时登记）：
            // onDestroyed 回调保证直接 instance.destroy() 也同步清理全局映射
            PluginInstanceImpl impl = (PluginInstanceImpl) instance;
            impl.setOnDestroyed(() -> unregisterParentChild(instance));
            if (parent != null) {
                parentToChildren.computeIfAbsent(parent, k -> ConcurrentHashMap.newKeySet()).add(instance);
            }
        }
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
        // W6 级联 destroy：沿全局映射递归收集全部后代（含跨定义），先子后父（子容器
        // parent 链随父销毁而失效，必须子先销毁）。destroySelf 不递归——闭包已含全部后代。
        List<IPluginInstance> closure = new ArrayList<>();
        Set<IPluginInstance> visited = new HashSet<>();
        collectChildrenFirst(instance, closure, visited);
        for (IPluginInstance inst : closure) {
            if (inst instanceof PluginInstanceImpl) {
                ((PluginInstanceImpl) inst).destroySelf();
            } else {
                inst.destroy();
            }
        }
        // 生命周期操作成功路径：destroy 后自动 reconcile（级联去激活依赖本实例的下游定义实例）
        reconcile();
    }

    /**
     * W6 级联闭包收集（先子后父，post-order）：沿全局父子映射递归；visited 防御
     * 手工构造的环引用。实例级 children 与全局映射同步维护（内容一致），此处以
     * 全局映射为权威（跨定义可观测性）。
     */
    private void collectChildrenFirst(IPluginInstance instance, List<IPluginInstance> order,
                                      Set<IPluginInstance> visited) {
        if (!visited.add(instance)) {
            return;
        }
        Set<IPluginInstance> children = parentToChildren.get(instance);
        if (children != null) {
            for (IPluginInstance child : children) {
                collectChildrenFirst(child, order, visited);
            }
        }
        order.add(instance);
    }

    /**
     * W6 全局映射清理：实例销毁时从父实例的 children 集移除 + 移除自身作为父的键
     * （其子实例已随级联销毁完毕）。经 PluginInstanceImpl.onDestroyed 回调调用——
     * 直接 instance.destroy() 路径也保证同步清理。
     */
    private void unregisterParentChild(IPluginInstance instance) {
        IPluginInstance p = instance.getParent();
        if (p != null) {
            Set<IPluginInstance> siblings = parentToChildren.get(p);
            if (siblings != null) {
                siblings.remove(instance);
                if (siblings.isEmpty()) {
                    parentToChildren.remove(p);
                }
            }
        }
        parentToChildren.remove(instance);
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
     * 显式变更检查入口（W6 变更检测接线，设计 §六）：遍历 VFS 轨 LOADED 定义，用
     * {@code ResourceComponentManager.checkChanged(resourcePath, lastModified)}（资源真实
     * lastModified 严格比对）检测 → 有变更调 {@link #reloadPlugin}。框架核心不主动起
     * 轮询线程（避免隐式生命周期与测试耦合）；宿主应用可定时调用（W7 docs 记录接线方式）。
     *
     * <p>检查失败显式报告（日志）不吞；检查无变更 no-op（lastModified 相等不触发 reload）。
     */
    public void checkChangedAndReload() {
        List<VfsPluginDefinition> defs = collectVfsDefinitions();
        for (VfsPluginDefinition def : defs) {
            if (def.getState() != PluginState.LOADED) {
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
     * W6 HMR 热重载（P2-A 快照语义，编排串行化于 reconcileLock——与 reconcile 互斥，
     * synchronized 可重入，末尾显式 reconcile 不冲突）：快照采集 → destroy 全部
     * （先子后父）→ unload → load（重解析）→ 按快照重建（父先子后）→ reconcile。
     *
     * <p>失败路径（No Silent No-Op）：destroy/unload 段失败 → 定义保留 LOADED（部分实例
     * 可能已销毁，状态可观测），快照保留可重试；load 段失败 → 定义 UNLOADED，快照保留；
     * 重建段失败 → 已建实例保留（可观测），未处理快照项转 pending（reconcile 重试）。
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
            }            VfsPluginDefinition oldDef = (VfsPluginDefinition) holder.plugin;
            if (oldDef.getState() != PluginState.LOADED) {
                throw new NopException(ERR_PLUGIN_DEFINITION_NOT_LOADED).param(ARG_PLUGIN_ID, pluginId);
            }

            // 1. 快照采集（P2-A：定义级 definitionConfig + 级联闭包实例列表——闭包 = 本定义
            //    全部实例 + 跨定义后代，先子后父序）；新 reload 取代旧 pending 状态
            ReloadSnapshot snapshot = collectSnapshot(oldDef);
            lastReloadSnapshot = snapshot;
            synchronized (pendingRebuilds) {
                pendingRebuilds.clear();
            }

            // 2. destroy 全部（先子后父；失败 → 定义保留 LOADED、快照保留，可重试 reload）
            for (SnapshotInstance si : snapshot.destroyOrder) {
                IPluginInstance inst = getInstance(si.pluginId, si.instanceKey);
                if (inst != null) {
                    destroyOne(inst);
                }
            }

            // 3. unload（实例已清空；失败 → 快照保留）
            oldDef.unload();

            // 4. load（重解析 plugin.xml → 新定义对象；失败 → 定义 UNLOADED、快照保留、
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
            // P2-A (a) 定义级快照：取自 step 1 采集的快照（unload 已清空旧定义的 definitionConfig，
            // 不能在 load 后读旧定义对象）
            newDef.load(snapshot.definitionConfig);
            registerNameIndex(newDef);
            subscribeGlobalConfig(newDef);
            plugins.put(pluginId, new PluginHolder(newDef, null));

            // 5. 按快照重建（父先子后）：跨定义后代仅当其定义仍 LOADED 时重建（否则显式错误
            //    记录，不静默丢失）；门控 null（W5 语义）→ pending（reconcile 触发点重试）
            Map<String, IPluginInstance> rebuilt = new HashMap<>();
            List<SnapshotInstance> pending = new ArrayList<>();
            try {
                for (SnapshotInstance si : snapshot.rebuildOrder) {
                    RebuildResult result = rebuildOne(si, snapshot, rebuilt);
                    switch (result.kind) {
                        case CREATED:
                            rebuilt.put(pair(si), result.instance);
                            break;
                        case GATED:
                        case PARENT_PENDING:
                            pending.add(si);
                            break;
                        case PARENT_MISSING:
                            LOG.error(
                                    "nop.plugin.reload-parent-missing:pluginId={},instanceKey={},parent={}#{}",
                                    si.pluginId, si.instanceKey, si.parentPluginId, si.parentInstanceKey);
                            break;
                        case DEFINITION_UNAVAILABLE:
                            LOG.error(
                                    "nop.plugin.reload-definition-unavailable:pluginId={},instanceKey={}（定义未 LOADED，跨定义后代不重建——显式错误，不静默丢失）",
                                    si.pluginId, si.instanceKey);
                            break;
                    }
                }
            } catch (RuntimeException e) {
                // 重建段失败：已建实例保留（可观测），剩余快照项转 pending（reconcile 重试）
                synchronized (pendingRebuilds) {
                    pendingRebuilds.addAll(pending);
                }
                throw e;
            }
            synchronized (pendingRebuilds) {
                pendingRebuilds.addAll(pending);
            }

            // 6. reconcile（状态收敛 + pending 重试；reconcileLock 可重入）
            reconcile();
        }
    }

    /**
     * 最近一次 reload 的 P2-A 快照（失败保留可重试的可观测入口；非接口方法，
     * 保持 IPluginManager 最小——测试经具体类断言）。
     */
    public ReloadSnapshot getLastReloadSnapshot() {
        return lastReloadSnapshot;
    }

    /**
     * 待重建的 pending 快照项数（W6 快照恢复语义可观测入口；非接口方法，测试经具体类断言）。
     */
    public int getPendingRebuildCount() {
        synchronized (pendingRebuilds) {
            return pendingRebuilds.size();
        }
    }

    private void destroyOne(IPluginInstance inst) {
        if (inst instanceof PluginInstanceImpl) {
            ((PluginInstanceImpl) inst).destroySelf();
        } else {
            inst.destroy();
        }
    }

    /**
     * P2-A 快照采集：级联闭包（先子后父序）+ 定义级 definitionConfig + 闭包成员
     * (pluginId, instanceKey) 对集合（父引用解析用）。闭包成员必须为本框架实例
     * （快照需读取原始实例配置，非 IPluginInstance 契约）——否则显式失败。
     */
    private ReloadSnapshot collectSnapshot(VfsPluginDefinition def) {
        List<IPluginInstance> closure = new ArrayList<>();
        Set<IPluginInstance> visited = new HashSet<>();
        for (IPluginInstance inst : def.getInstances()) {
            collectChildrenFirst(inst, closure, visited);
        }
        List<SnapshotInstance> destroyOrder = new ArrayList<>(closure.size());
        Set<String> pairs = new HashSet<>();
        for (IPluginInstance inst : closure) {
            if (!(inst instanceof PluginInstanceImpl)) {
                throw new NopException(ERR_PLUGIN_INSTANCE_NOT_SUPPORTED)
                        .param(ARG_PLUGIN_ID, def.getPluginId())
                        .param(ARG_INSTANCE_KEY, inst.getInstanceKey());
            }
            PluginInstanceImpl impl = (PluginInstanceImpl) inst;
            destroyOrder.add(new SnapshotInstance(impl));
            pairs.add(pair(impl));
        }
        return new ReloadSnapshot(def.getDefinitionConfig(), destroyOrder, pairs);
    }

    /**
     * 按快照重建单个实例：父引用解析 = 已重建实例（父先子后序，父必先建）→ 快照闭包内
     * 但未重建（父被门控）→ PARENT_PENDING（子跟随 pending）→ 闭包外 → 现注册表
     * （未被 destroy 的跨闭包父）→ 不存在 → PARENT_MISSING（显式错误）。
     */
    private RebuildResult rebuildOne(SnapshotInstance si, ReloadSnapshot snapshot,
                                     Map<String, IPluginInstance> rebuilt) {
        PluginHolder holder = plugins.get(si.pluginId);
        if (holder == null || !(holder.plugin instanceof VfsPluginDefinition)
                || ((VfsPluginDefinition) holder.plugin).getState() != PluginState.LOADED) {
            return RebuildResult.definitionUnavailable();
        }
        IPluginInstance parent = null;
        if (si.parentPluginId != null) {
            parent = rebuilt.get(pair(si.parentPluginId, si.parentInstanceKey));
            if (parent == null) {
                if (snapshot.pairs.contains(pair(si.parentPluginId, si.parentInstanceKey))) {
                    return RebuildResult.parentPending();
                }
                parent = getInstance(si.parentPluginId, si.parentInstanceKey);
                if (parent == null) {
                    return RebuildResult.parentMissing();
                }
            }
        }
        IPluginInstance instance = createInstance(si.pluginId, si.instanceKey, si.config, parent);
        if (instance == null) {
            return RebuildResult.gated();
        }
        return RebuildResult.created(instance);
    }

    /**
     * pending 重试（reconcile 触发点，快照恢复语义）：按父先序重放 pending 项——门控打开
     * （createInstance 返回实例）→ 移除；仍门控 → 保持；父仍 pending → 跟随保持；父已不存在
     * （destroy/unload 后不可重建）→ 显式错误记录并丢弃。
     */
    private void retryPendingRebuilds() {
        List<SnapshotInstance> retry;
        synchronized (pendingRebuilds) {
            if (pendingRebuilds.isEmpty()) {
                return;
            }
            retry = new ArrayList<>(pendingRebuilds);
        }
        for (SnapshotInstance si : retry) {
            if (si.parentPluginId != null) {
                IPluginInstance parent = getInstance(si.parentPluginId, si.parentInstanceKey);
                if (parent == null) {
                    synchronized (pendingRebuilds) {
                        if (containsPendingPair(si.parentPluginId, si.parentInstanceKey)) {
                            continue; // 父仍 pending（门控未开）→ 子跟随等待
                        }
                        LOG.error("nop.plugin.reload-pending-drop:pluginId={},instanceKey={},parent={}#{}（父实例已不存在，快照项丢弃）",
                                si.pluginId, si.instanceKey, si.parentPluginId, si.parentInstanceKey);
                        pendingRebuilds.remove(si);
                        continue;
                    }
                }
            }
            PluginHolder holder = plugins.get(si.pluginId);
            if (holder == null || !(holder.plugin instanceof VfsPluginDefinition)
                    || ((VfsPluginDefinition) holder.plugin).getState() != PluginState.LOADED) {
                synchronized (pendingRebuilds) {
                    LOG.error("nop.plugin.reload-pending-drop:pluginId={},instanceKey={}（定义未 LOADED，快照项丢弃）",
                            si.pluginId, si.instanceKey);
                    pendingRebuilds.remove(si);
                }
                continue;
            }
            IPluginInstance instance = createInstance(si.pluginId, si.instanceKey, si.config,
                    si.parentPluginId == null ? null : getInstance(si.parentPluginId, si.parentInstanceKey));
            if (instance != null) {
                synchronized (pendingRebuilds) {
                    pendingRebuilds.remove(si);
                }
            }
        }
    }

    private boolean containsPendingPair(String pluginId, String instanceKey) {
        for (SnapshotInstance si : pendingRebuilds) {
            if (si.pluginId.equals(pluginId) && si.instanceKey.equals(instanceKey)) {
                return true;
            }
        }
        return false;
    }

    private static String pair(PluginInstanceImpl instance) {
        return pair(instance.getPluginId(), instance.getInstanceKey());
    }

    private static String pair(String pluginId, String instanceKey) {
        return pluginId + "#" + instanceKey;
    }

    private static String pair(SnapshotInstance si) {
        return pair(si.pluginId, si.instanceKey);
    }

    /**
     * P2-A 快照：定义级 definitionConfig + 级联闭包实例列表（destroyOrder 先子后父、
     * rebuildOrder 父先子后）+ 闭包成员 (pluginId, instanceKey) 对集合。
     */
    static class ReloadSnapshot {
        final Map<String, Object> definitionConfig;
        final List<SnapshotInstance> destroyOrder;
        final List<SnapshotInstance> rebuildOrder;
        final Set<String> pairs;

        ReloadSnapshot(Map<String, Object> definitionConfig, List<SnapshotInstance> destroyOrder,
                       Set<String> pairs) {
            this.definitionConfig = definitionConfig;
            this.destroyOrder = destroyOrder;
            List<SnapshotInstance> reversed = new ArrayList<>(destroyOrder);
            Collections.reverse(reversed);
            this.rebuildOrder = reversed;
            this.pairs = pairs;
        }
    }

    /**
     * 快照实例项：instanceKey + 原始实例配置（createInstance 传入——重建按原始配置回放，
     * 非合并视图，避免双层合并）+ parent 以 (pluginId, instanceKey) 对记录（destroy 后
     * 父对象失效，重建时解析到新实例对象）。
     */
    static class SnapshotInstance {
        final String pluginId;
        final String instanceKey;
        final Map<String, Object> config;
        final String parentPluginId;
        final String parentInstanceKey;

        SnapshotInstance(PluginInstanceImpl instance) {
            this.pluginId = instance.getPluginId();
            this.instanceKey = instance.getInstanceKey();
            this.config = instance.getRawInstanceConfig();
            IPluginInstance parent = instance.getParent();
            if (parent instanceof PluginInstanceImpl) {
                this.parentPluginId = ((PluginInstanceImpl) parent).getPluginId();
                this.parentInstanceKey = parent.getInstanceKey();
            } else {
                this.parentPluginId = null;
                this.parentInstanceKey = null;
            }
        }
    }

    private static class RebuildResult {
        enum Kind {
            CREATED, GATED, PARENT_PENDING, PARENT_MISSING, DEFINITION_UNAVAILABLE
        }

        final Kind kind;
        final IPluginInstance instance;

        private RebuildResult(Kind kind, IPluginInstance instance) {
            this.kind = kind;
            this.instance = instance;
        }

        static RebuildResult created(IPluginInstance instance) {
            return new RebuildResult(Kind.CREATED, instance);
        }

        static RebuildResult gated() {
            return new RebuildResult(Kind.GATED, null);
        }

        static RebuildResult parentPending() {
            return new RebuildResult(Kind.PARENT_PENDING, null);
        }

        static RebuildResult parentMissing() {
            return new RebuildResult(Kind.PARENT_MISSING, null);
        }

        static RebuildResult definitionUnavailable() {
            return new RebuildResult(Kind.DEFINITION_UNAVAILABLE, null);
        }
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
                    // W6 pending 重试（快照恢复语义）：门控打开后经 reconcile 触发点重建；
                    // 重建的 createInstance 经 manager 入口会触发 reconcile（dirty），循环消费
                    retryPendingRebuilds();
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
                        // P2-D reconcile 路径级联式：先级联去激活后代（先子后父），再去激活本实例
                        changed |= deactivateCascadeIfActive(def, instance);
                        continue;
                    }
                    // 父链健康（P2-D 跨 plan 契约）：祖先 DEACTIVATED → 子实例不激活
                    // （父链抑制激活，与显式守卫共同消除"父停即子停、reconcile 又激活"冲突）
                    if (evaluateInstanceCondition(def, instance) && isParentChainHealthy(instance)) {
                        changed |= activateIfInactive(def, instance);
                    } else {
                        changed |= deactivateCascadeIfActive(def, instance);
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    /**
     * P2-D reconcile 路径级联去激活（W6，设计 §三(b) 级联语义）：reconcile 决定去激活某父实例时
     * <b>先递归去激活其 ACTIVATED 后代（保留实例对象，可重新激活），再去激活本实例</b>——
     * 否则子实例容器 parent 指向已停容器，服务回退调用抛 ERR_IOC_CONTAINER_NOT_STARTED 硬失败
     * （"服务沿链回退"契约破裂）。子先父后亦保证父实例的显式守卫
     * （ERR_PLUGIN_ACTIVE_CHILDREN_EXIST）在 reconcile 路径自然放行（不冲突）。
     */
    private boolean deactivateCascadeIfActive(VfsPluginDefinition def, IPluginInstance instance) {
        boolean changed = false;
        if (instance instanceof PluginInstanceImpl) {
            for (PluginInstanceImpl child : ((PluginInstanceImpl) instance).getChildren()) {
                if (child.getState() == InstanceState.ACTIVATED) {
                    changed |= deactivateCascadeIfActive(def, child);
                }
            }
        }
        return changed | deactivateIfActive(def, instance);
    }

    /**
     * 父链健康检查（P2-D reconcile 路径）：沿 getParent() 链回溯，任一祖先非 ACTIVATED
     * 即不健康（父 DEACTIVATED 时子实例不激活——父链抑制激活，防止 reconcile 反复尝试
     * 激活子实例造成冲突）。
     */
    private boolean isParentChainHealthy(IPluginInstance instance) {
        return !(instance instanceof PluginInstanceImpl)
                || ((PluginInstanceImpl) instance).isParentChainHealthy();
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
     * 解析 plugin.xml 为定义对象（load/reload 共用）：DslModelParser 管线 + W5 评估器回引
     * + updateConfig 回调 + W6 资源真实 lastModified 记录（变更检测比对源，非 getLastChangeTime
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
        // W5 数据流缝：注入评估器回引（查其他定义实例状态 + 读全局配置）与 updateConfig 回调
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
