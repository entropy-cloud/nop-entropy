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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.plugin.api.NopPluginConstants.BEAN_NOP_PLUGIN_COMMAND_PREFIX;
import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_ACTIVATOR;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_BEAN_TYPE;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_INSTANCE_KEY;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVATION_FAILED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVATOR_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INVALID_ACTIVATOR;
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

    private final Object lifecycleLock = new Object();

    private volatile InstanceState state = InstanceState.DEACTIVATED;
    private volatile PluginScopeImpl scope;
    private volatile IBeanContainer container;
    private volatile InstanceConfigProvider configProvider;
    private volatile Map<String, Object> mergedView;
    private volatile Throwable lastActivationError;

    public PluginInstanceImpl(VfsPluginDefinition definition, String instanceKey, Map<String, Object> config,
                              IPluginInstance parent) {
        this.definition = definition;
        this.instanceKey = instanceKey;
        this.instanceConfig = config == null ? Collections.emptyMap() : new LinkedHashMap<>(config);
        this.parent = parent;
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
            doDeactivate();
            return null;
        });
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
        try {
            doDeactivate();
        } finally {
            definition.removeInstance(instanceKey);
        }
    }

    /**
     * 定义级 updateConfig 传播（P2-C）：ACTIVATED 热应用（刷新合并视图 + 经委托 provider 触发变更通知）；
     * DEACTIVATED 缓存（下次 activate 重新合并，本方法不动）。
     */
    void onDefinitionConfigChanged() {
        synchronized (lifecycleLock) {
            if (state != InstanceState.ACTIVATED) {
                return;
            }
            Map<String, Object> merged = newMergedView();
            mergedView = merged;
            InstanceConfigProvider provider = configProvider;
            if (provider != null) {
                provider.updateMergedView(merged);
            }
        }
    }

    private Map<String, Object> newMergedView() {
        Map<String, Object> merged = new LinkedHashMap<>(definition.getDefinitionConfig());
        merged.putAll(instanceConfig);
        return merged;
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
        // 禁止 buildNewInstance（不传播自定义 provider，会静默回落到全局 AppConfig）
        BeanContainerBuilder builder = new BeanContainerBuilder(classLoader, null);
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
