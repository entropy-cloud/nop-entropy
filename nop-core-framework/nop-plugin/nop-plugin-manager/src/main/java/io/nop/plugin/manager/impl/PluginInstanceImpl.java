package io.nop.plugin.manager.impl;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.util.ClassHelper;
import io.nop.ioc.impl.BeanContainerImpl;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.ioc.model.BeansModel;
import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.IPluginScope;
import io.nop.plugin.api.InstanceState;
import io.nop.plugin.manager.PluginManagerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.nop.plugin.api.NopPluginConstants.BEAN_NOP_PLUGIN_COMMAND_PREFIX;
import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_ACTIVATOR;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_BEAN_TYPE;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_INSTANCE_KEY;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_INSTANCE_KEYS;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVATION_FAILED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVE_CHILDREN_EXIST;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVATOR_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INVALID_ACTIVATOR;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_PARENT_NOT_ACTIVATED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE;

/**
 * 插件实例（实例级状态机，设计文档 01-architecture-baseline.md §7.2）——固定实例对象：
 * deactivate 只销毁内部子容器并回退 effect（实例对象保留，可重新 activate）；destroy 从定义移除。
 *
 * <p>生命周期：
 * <ul>
 *   <li>activate = 实例化子容器（{@link BeanContainerBuilder} 标准管线 → 转
 *   {@code BeanContainerImpl} → 先 {@code setConfigProvider} 再 {@code start()}，禁用
 *   {@code buildNewInstance}——其不传播自定义 provider）+ 定位 activator bean +
 *   {@code activator.activate(scope, config)}（scope+合并视图 config 参数传递，禁止字段注入）+
 *   返回值非 null 自动注册为 effect。</li>
 *   <li>deactivate = <b>先 {@code scope.close()}（LIFO 回退全部 effect）再子容器 stop</b>。</li>
 *   <li>per-instance in-flight 单飞：同一实例的 activate/deactivate 经 {@code lifecycleLock}
 *   串行化；并发重复 activate 幂等（in-flight 期间等待，完成后见 ACTIVATED 直接返回，不重跑）。</li>
 * </ul>
 *
 * <p>激活失败：实例回退 DEACTIVATED 并记录错误（不残留半激活实例），错误带实例 key 参数。
 * 每次 activate 使用全新 scope 实例（close 后 effect() 抛异常，旧 scope 不可复用）。
 */
public class PluginInstanceImpl implements IPluginInstance {
    static final Logger LOG = LoggerFactory.getLogger(PluginInstanceImpl.class);

    private final VfsPluginDefinition definition;
    private final String instanceKey;
    private final Map<String, Object> instanceConfig;
    private final IPluginInstance parent;

    /**
     * 子实例集合（W6 parent 层级）——实例级 children 引用：父热应用传播（
     * {@link #onDefinitionConfigChanged} 递归）与 P2-D 显式 deactivate 守卫需要实例级访问；
     * 与 manager 级全局父子映射（PluginManagerImpl）同步维护（createInstance 时登记、
     * destroy 时移除）。跨定义 parent 链由全局映射保证可观测（定义持有类级索引看不见
     * 其他定义的实例）。
     */
    private final List<PluginInstanceImpl> children = new CopyOnWriteArrayList<>();

    /**
     * destroy 通知回调（manager 注入：从全局父子映射移除本实例；直接调用
     * {@link #destroy()} 时保证 manager 级映射同步清理）。
     */
    private volatile Runnable onDestroyed;

    private final Object lifecycleLock = new Object();

    /**
     * 连续激活失败阈值（W5 reconcile）：达到后暂停自动激活（仅显式 activate() 可恢复），避免无限重试。
     */
    static final int AUTO_ACTIVATION_FAILURE_THRESHOLD = 5;

    private volatile InstanceState state = InstanceState.DEACTIVATED;
    private volatile PluginScopeImpl scope;
    private volatile IBeanContainer container;
    private volatile InstanceConfigProvider configProvider;
    private volatile Map<String, Object> mergedView;
    private volatile Throwable lastActivationError;
    private volatile int activationFailures;
    private volatile boolean autoActivationPaused;

    public PluginInstanceImpl(VfsPluginDefinition definition, String instanceKey, Map<String, Object> config,
                              IPluginInstance parent) {
        this.definition = definition;
        this.instanceKey = instanceKey;
        this.instanceConfig = config == null ? Collections.emptyMap() : new LinkedHashMap<>(config);
        this.parent = parent;
        // 子实例登记进父实例的 children（实例级引用；manager 级全局映射由 PluginManagerImpl 登记）
        if (parent instanceof PluginInstanceImpl) {
            ((PluginInstanceImpl) parent).registerChild(this);
        }
        this.mergedView = newMergedView();
    }

    public String getPluginId() {
        return definition.getPluginId();
    }

    @Override
    public String getInstanceKey() {
        return instanceKey;
    }

    @Override
    public InstanceState getState() {
        return state;
    }

    /**
     * 最近一次激活失败原因（激活失败后记录；成功后清空）。
     */
    public Throwable getLastActivationError() {
        return lastActivationError;
    }

    /**
     * 自动激活是否被失败阈值暂停（W5 reconcile 读取）：连续激活失败 ≥ 阈值 → 暂停
     * reconcile 的自动激活尝试；仅显式 {@link #activate()} 成功可恢复（清计数 + 解除暂停）。
     */
    public boolean isAutoActivationPaused() {
        return autoActivationPaused;
    }

    private void onActivationFailed() {
        if (++activationFailures >= AUTO_ACTIVATION_FAILURE_THRESHOLD && !autoActivationPaused) {
            autoActivationPaused = true;
            LOG.warn("nop.plugin.auto-activation-paused:pluginId={},instanceKey={},failures={}",
                    getPluginId(), instanceKey, activationFailures);
        }
    }

    private void onActivationSucceeded() {
        activationFailures = 0;
        autoActivationPaused = false;
    }

    @Override
    public CompletionStage<Void> activate() {
        return FutureHelper.futureCall(() -> {
            doActivate();
            return null;
        });
    }

    @Override
    public CompletionStage<Void> deactivate() {
        return FutureHelper.futureCall(() -> {
            doDeactivateWithGuard();
            return null;
        });
    }

    /**
     * P2-D 显式 deactivate 守卫（W6）：父实例存在 ACTIVATED 子实例时抛明确异常
     * （带子实例 key 参数；与 unload 守卫同构——先处理子再处理父）。destroy 仍级联
     * （{@link #destroy()} 先子后父，不经此守卫）。
     */
    private void doDeactivateWithGuard() {
        synchronized (lifecycleLock) {
            if (state != InstanceState.ACTIVATED) {
                return;
            }
            List<String> activeChildren = new ArrayList<>();
            for (PluginInstanceImpl child : children) {
                if (child.getState() == InstanceState.ACTIVATED) {
                    activeChildren.add(child.getInstanceKey());
                }
            }
            if (!activeChildren.isEmpty()) {
                throw new NopException(ERR_PLUGIN_ACTIVE_CHILDREN_EXIST)
                        .param(ARG_PLUGIN_ID, getPluginId())
                        .param(ARG_INSTANCE_KEY, instanceKey)
                        .param(ARG_INSTANCE_KEYS, activeChildren);
            }
            doDeactivate();
        }
    }

    @Override
    public IPluginScope getScope() {
        return state == InstanceState.ACTIVATED ? scope : null;
    }

    @Override
    public <T> T getService(Class<T> serviceType) {
        PluginScopeImpl s = scope;
        if (state != InstanceState.ACTIVATED || s == null) {
            throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, getPluginId())
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
        checkProxyable(serviceType);
        // 包装时立即解析一次：多候选/无候选错误在 getService 调用点抛出（不延迟到首次代理调用）
        s.getService(serviceType);
        return ServiceProxy.newInstance(this, serviceType);
    }

    @Override
    public <T> Collection<T> getServices(Class<T> serviceType) {
        PluginScopeImpl s = scope;
        if (state != InstanceState.ACTIVATED || s == null) {
            throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, getPluginId())
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
        checkProxyable(serviceType);
        // 集合代理以 getBeansOfType 的 bean id 为候选键（重激活后按 id 重新解析）
        Map<String, T> beans = s.getServiceBeans(serviceType);
        List<T> proxies = new ArrayList<>(beans.size());
        for (String beanId : beans.keySet()) {
            proxies.add(ServiceProxy.newInstance(this, serviceType, beanId));
        }
        return proxies;
    }

    /**
     * 生命周期代理仅支持接口类型（具体类无法生成代理）；具体类传入明确抛错，
     * 禁止静默返回裸引用/裸集合（W4 Phase 1 裁定）。
     */
    private void checkProxyable(Class<?> serviceType) {
        if (!serviceType.isInterface()) {
            throw new NopException(ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE)
                    .param(ARG_BEAN_TYPE, serviceType.getName())
                    .param(ARG_PLUGIN_ID, getPluginId())
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
    }

    @Override
    public IPluginInstance getParent() {
        return parent;
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> view = mergedView;
        return view == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(view));
    }

    /**
     * 原始实例配置（createInstance 传入，未合并）——W6 reload 快照重建按原始配置回放
     * （合并视图快照会与定义级 definitionConfig 快照双层合并）。
     */
    Map<String, Object> getRawInstanceConfig() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(instanceConfig));
    }

    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                   String fieldSelection,
                                                                   IPluginCancelToken cancelToken) {
        if (state != InstanceState.ACTIVATED) {
            throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, getPluginId())
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
        String beanName = BEAN_NOP_PLUGIN_COMMAND_PREFIX + command;
        IPluginCommand commandBean = getCommandBean(beanName, true);
        if (commandBean == null) {
            commandBean = getCommandBean(BEAN_NOP_PLUGIN_COMMAND_PREFIX + "default", false);
        }
        return commandBean.invokeCommandAsync(command, args, fieldSelection, cancelToken);
    }

    @Override
    public Map<String, Object> invokeCommand(String command, Map<String, Object> args, String fieldSelection,
                                             IPluginCancelToken cancelToken) {
        return FutureHelper.syncGet(invokeCommandAsync(command, args, fieldSelection, cancelToken));
    }

    @Override
    public void destroy() {
        // 级联 destroy（设计 §三(b)，W6）：先子后父，递归销毁全部后代；子实例先回退自身
        // effect 再销毁（子容器 parent 链随父销毁而失效，不得残留悬空子实例）
        List<PluginInstanceImpl> snapshot = new ArrayList<>(children);
        children.clear();
        for (PluginInstanceImpl child : snapshot) {
            child.destroy();
        }
        destroySelf();
    }

    /**
     * 单实例销毁（不递归级联）——manager 级级联路径（沿全局映射收集的闭包）复用；
     * 直接 {@link #destroy()} 的级联路径内部亦收敛到此。
     */
    void destroySelf() {
        try {
            doDeactivate();
        } finally {
            definition.removeInstance(instanceKey);
            if (parent instanceof PluginInstanceImpl) {
                ((PluginInstanceImpl) parent).unregisterChild(this);
            }
            Runnable callback = onDestroyed;
            if (callback != null) {
                callback.run();
            }
        }
    }

    void setOnDestroyed(Runnable onDestroyed) {
        this.onDestroyed = onDestroyed;
    }

    void registerChild(PluginInstanceImpl child) {
        children.add(child);
    }

    void unregisterChild(PluginInstanceImpl child) {
        children.remove(child);
    }

    /**
     * 子实例集合（P2-D 守卫 / 父热应用传播 / reconcile 级联 deactivate 使用）。
     */
    List<PluginInstanceImpl> getChildren() {
        return Collections.unmodifiableList(new ArrayList<>(children));
    }

    /**
     * 是否存在 ACTIVATED 子实例（P2-D 显式 deactivate 守卫的可观测判定）。
     */
    boolean hasActiveChildren() {
        for (PluginInstanceImpl child : children) {
            if (child.getState() == InstanceState.ACTIVATED) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前 ACTIVATED 子容器（W6 子容器 parent 链接线使用；父 DEACTIVATED 时为 null）。
     */
    IBeanContainer getContainer() {
        return container;
    }

    /**
     * 父链健康检查（W6 reconcile 路径）：沿 getParent() 链回溯，任一祖先非 ACTIVATED
     * 即不健康（visited 集合防御手工构造的环引用）。
     */
    boolean isParentChainHealthy() {
        Set<IPluginInstance> visited = new HashSet<>();
        for (IPluginInstance p = parent; p != null; p = p.getParent()) {
            if (!visited.add(p)) {
                return false;
            }
            if (p.getState() != InstanceState.ACTIVATED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 定义级 updateConfig 传播（P2-C + W6 父热应用传播）：ACTIVATED 热应用（刷新合并视图 +
     * 经委托 provider 触发变更通知）并<b>递归传播到全部后代</b>（父合并视图变化 → 子重算
     * 合并视图 + 触发各自 provider 变更，子覆盖父语义保持）；DEACTIVATED 缓存
     * （下次 activate 重新合并，本方法不动——P2-C 缓存契约保持，含后代）。
     */
    void onDefinitionConfigChanged() {
        synchronized (lifecycleLock) {
            if (state != InstanceState.ACTIVATED) {
                return;
            }
            recomputeMergedView();
            for (PluginInstanceImpl child : children) {
                child.onDefinitionConfigChanged();
            }
        }
    }

    private void recomputeMergedView() {
        Map<String, Object> merged = newMergedView();
        mergedView = merged;
        InstanceConfigProvider provider = configProvider;
        if (provider != null) {
            provider.updateMergedView(merged);
        }
    }

    /**
     * 配置层叠（W6，设计 §三(c)）：父合并视图（父配置 + 父定义默认 + 祖父……递归）+ 定义默认
     * + 子实例配置（子覆盖父）。父配置热应用经 {@link #onDefinitionConfigChanged} 递归传播——
     * 本方法实时读取父合并视图，保证传播路径单点（父先重算、子后读取，无陈旧快照）。
     */
    private Map<String, Object> newMergedView() {
        Map<String, Object> merged = new LinkedHashMap<>();
        IPluginInstance p = parent;
        if (p != null) {
            merged.putAll(p.getConfig());
        }
        merged.putAll(definition.getDefinitionConfig());
        merged.putAll(instanceConfig);
        return merged;
    }

    /**
     * 实例级 coeffect 求值数据源（W5）：定义默认 + 实例配置的<b>实时</b>合并视图。
     * {@link #getConfig()} 为缓存快照（P2-C：DEACTIVATED 缓存配置、下次 activate 应用）——
     * 缓存语义保持；求值用实时视图使 updateConfig → 自动 reconcile 的 activate/deactivate
     * 往返可驱动（快照在 DEACTIVATED 期间不刷新，会永久卡在旧值）。
     */
    Map<String, Object> getCoeffectConfigView() {
        return newMergedView();
    }

    private void doActivate() {
        synchronized (lifecycleLock) {
            if (state == InstanceState.ACTIVATED) {
                // in-flight 单飞：并发重复 activate 幂等，不重跑 activator
                return;
            }
            try {
                activateInternal();
            } catch (RuntimeException | Error e) {
                LOG.error("nop.plugin.instance-activate-fail:pluginId={},instanceKey={}", getPluginId(),
                        instanceKey, e);
                // 回滚：停止本次激活创建的子容器（scope 随之回退）——不残留半激活实例
                rollbackActivation();
                lastActivationError = e;
                onActivationFailed();
                if (e instanceof NopException) {
                    ((NopException) e).param(ARG_PLUGIN_ID, getPluginId()).param(ARG_INSTANCE_KEY, instanceKey);
                    throw e;
                }
                throw new NopException(ERR_PLUGIN_ACTIVATION_FAILED)
                        .param(ARG_PLUGIN_ID, getPluginId())
                        .param(ARG_INSTANCE_KEY, instanceKey)
                        .cause(e);
            }
        }
    }

    private void activateInternal() {
        Map<String, Object> merged = newMergedView();
        mergedView = merged;

        InstanceConfigProvider provider = new InstanceConfigProvider(AppConfig.getConfigProvider());
        provider.setMergedView(merged);

        BeansModel beansModel = definition.getBeansModel();
        IClassLoader classLoader = ClassHelper.getSafeClassLoader();
        // 容器接线钉死：标准 BeanContainerBuilder 管线（AppBeanContainerLoader 同源）→
        // 转 BeanContainerImpl → 先 setConfigProvider(实例 provider) 再 start()；
        // 禁止 buildNewInstance（不传播自定义 provider，会静默回落到全局 AppConfig）。
        // W6 子容器 parent 链接线：parent = 父实例的 ACTIVATED 容器（服务查找沿链回退）；
        // 父在检查后、接线前被 deactivate（容器置 null）→ 显式失败，不静默降级为顶层容器。
        // 顶层实例 parentContainer 保持 null（"→ 宿主"语义不扩展，设计 01 §三(a) 注解）。
        IBeanContainer parentContainer = resolveParentContainer();
        BeanContainerBuilder builder = new BeanContainerBuilder(classLoader, parentContainer);
        if (beansModel != null) {
            builder.addBeansModel(beansModel);
        }
        IBeanContainer container = builder.build(definition.getPluginId() + "#" + instanceKey);
        ((BeanContainerImpl) container).setConfigProvider(provider);
        container.start();

        PluginScopeImpl scope = new PluginScopeImpl(container);
        // 先落字段：激活失败时 rollbackActivation 能回退本批资源
        this.scope = scope;
        this.container = container;
        this.configProvider = provider;

        String activatorName = definition.getActivatorName();
        if (activatorName != null) {
            if (!container.containsBean(activatorName)) {
                throw new NopException(ERR_PLUGIN_ACTIVATOR_NOT_FOUND)
                        .param(ARG_PLUGIN_ID, getPluginId())
                        .param(ARG_INSTANCE_KEY, instanceKey)
                        .param(ARG_ACTIVATOR, activatorName);
            }
            Object activatorBean = container.getBean(activatorName);
            if (!(activatorBean instanceof IPluginActivator)) {
                throw new NopException(ERR_PLUGIN_INVALID_ACTIVATOR)
                        .param(ARG_PLUGIN_ID, getPluginId())
                        .param(ARG_INSTANCE_KEY, instanceKey)
                        .param(ARG_ACTIVATOR, activatorName);
            }
            // 双参数传递（scope + 合并视图 config），禁止字段注入 scope
            Disposable returned = ((IPluginActivator) activatorBean).activate(scope, merged);
            // 返回值非 null 自动注册为该实例的 effect（等价 scope.effect()）
            if (returned != null) {
                scope.effect(returned);
            }
        }

        this.state = InstanceState.ACTIVATED;
        this.lastActivationError = null;
        onActivationSucceeded();
    }

    /**
     * 解析父实例的 ACTIVATED 容器（W6 子容器 parent 链接线）：父非本框架实现或容器
     * 已随 deactivate 置 null → 显式失败（禁止挂到已停容器，服务沿链回退会硬失败）。
     */
    private IBeanContainer resolveParentContainer() {
        IPluginInstance p = parent;
        if (p == null) {
            return null;
        }
        if (!(p instanceof PluginInstanceImpl)) {
            throw new NopException(ERR_PLUGIN_PARENT_NOT_ACTIVATED)
                    .param(ARG_PLUGIN_ID, getPluginId())
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
        IBeanContainer parentContainer = ((PluginInstanceImpl) p).getContainer();
        if (parentContainer == null) {
            throw new NopException(ERR_PLUGIN_PARENT_NOT_ACTIVATED)
                    .param(ARG_PLUGIN_ID, getPluginId())
                    .param(ARG_INSTANCE_KEY, instanceKey);
        }
        return parentContainer;
    }

    private void rollbackActivation() {
        PluginScopeImpl s = scope;
        IBeanContainer c = container;
        scope = null;
        container = null;
        configProvider = null;
        try {
            if (s != null) {
                s.close();
            }
        } finally {
            if (c != null) {
                c.stop();
            }
        }
        state = InstanceState.DEACTIVATED;
    }

    private void doDeactivate() {
        synchronized (lifecycleLock) {
            if (state != InstanceState.ACTIVATED) {
                return;
            }
            PluginScopeImpl s = scope;
            IBeanContainer c = container;
            scope = null;
            container = null;
            configProvider = null;
            try {
                // deactivate 顺序：先 scope.close()（LIFO 回退全部注册 effect）再子容器 stop
                if (s != null) {
                    s.close();
                }
            } finally {
                if (c != null) {
                    c.stop();
                }
            }
            state = InstanceState.DEACTIVATED;
        }
    }

    private IPluginCommand getCommandBean(String beanName, boolean ignoreUnknown) {
        IBeanContainer c = container;
        if (c != null && c.containsBean(beanName)) {
            return (IPluginCommand) c.getBean(beanName);
        }
        if (ignoreUnknown) {
            Object bean = io.nop.api.core.ioc.BeanContainer.tryGetBean(beanName);
            return bean instanceof IPluginCommand ? (IPluginCommand) bean : null;
        }
        return (IPluginCommand) io.nop.api.core.ioc.BeanContainer.instance().getBean(beanName);
    }
}
