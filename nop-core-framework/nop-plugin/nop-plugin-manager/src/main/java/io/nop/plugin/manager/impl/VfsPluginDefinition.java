package io.nop.plugin.manager.impl;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.util.ClassHelper;
import io.nop.core.model.object.DynamicObject;
import io.nop.ioc.impl.BeanContainerImpl;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.ioc.model.BeansModel;
import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;
import io.nop.plugin.api.IPluginScope;
import io.nop.plugin.api.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static io.nop.plugin.api.NopPluginConstants.BEAN_NOP_PLUGIN_COMMAND_PREFIX;
import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_STATE;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_NOT_DEACTIVATED;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_ACTIVATOR;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_BEAN_TYPE;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_SPEC_ATTR;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVATION_FAILED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_ACTIVATOR_NOT_FOUND;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_DEFINITION_NOT_LOADED;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INVALID_ACTIVATOR;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INVALID_COEFFECT_SPEC;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE;

/**
 * VFS 轨（本地 *.plugin.xml）的定义持有类——manager 侧实现 {@link IPlugin}，承载单层六态状态机
 * （UNLOADED → LOADED ⇄ ACTIVATED，含 ACTIVATING/DEACTIVATING/FAILED；一个定义至多一个激活，
 * 见设计文档 01-architecture-baseline.md §三/§7.1）：加载只产出静态定义，激活才实例化子容器
 * 并执行 activator。
 *
 * <p>生命周期：
 * <ul>
 *   <li>{@link #activate()} = 门控评估（{@link #isDefinitionGateSatisfied()}，不满足 no-op 返回
 *   false）→ LOADED→ACTIVATING → 子容器构建（{@link BeanContainerBuilder} 标准管线 → 转
 *   {@code BeanContainerImpl} → <b>先 setConfigProvider（定义级配置域视图）再 start()</b>，禁用
 *   {@code buildNewInstance}——其不传播自定义 provider）→ activator（{@code activate(scope, config)}
 *   双参数传递，返回值非 null 自动注册为 effect）→ ACTIVATED。失败置 FAILED（回滚本次激活创建的
 *   容器与 scope，不残留半激活状态）并记录错误（可重试激活）。</li>
 *   <li>{@link #deactivate()} = ACTIVATED→DEACTIVATING → <b>先 {@code scope.close()}（LIFO 回退
 *   全部 effect）再子容器 stop</b> → LOADED（定义保留）。幂等。</li>
 *   <li>{@link #unload()} 守卫：ACTIVATED/中间态（ACTIVATING/DEACTIVATING）抛明确异常
 *   （{@code ERR_PLUGIN_NOT_DEACTIVATED}）；FAILED 已清理完毕可 unload。</li>
 *   <li>in-flight 单飞：同一 plugin 的 activate/deactivate 经 {@code lifecycleLock} 串行化；
 *   并发重复 activate 幂等（ACTIVATED 直接返回 true，不重跑 activator）。</li>
 * </ul>
 *
 * <p>钉死行为：VFS 轨无 Maven 坐标（groupId/artifactId/version 为 null）；start/stop 收敛为
 * §7.1 目标语义（start = load + activate、stop = deactivate + unload）；定义级
 * {@link #updateConfig} 合并更新定义配置域——ACTIVATED 热应用（配置域视图刷新 + 经委托 provider
 * 触发变更通知），LOADED/FAILED 缓存、下次 activate 应用（updateConfig 后经 {@link #onConfigChanged}
 * 回调自动触发 manager reconcile）；invokeCommand 定义级路由（命令 bean 分发于本插件激活容器）。
 *
 * <p>coeffect spec：{@code requires}（依赖定义 @name 集合）与 {@code if-property}
 * （格式 propName|expectedValue，缺省 expectedValue 视为 true）在构造时（load 解析）提取为
 * 类型化字段并校验；门控评估经 {@link #coeffectEvaluator} 回引（manager 注入，查其他定义激活
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

    private PluginState state = PluginState.UNLOADED;
    private Timestamp lastChangeTime;
    private Timestamp loadTime;

    /**
     * 资源真实 lastModified（变更检测比对源，load/reload 时由 manager 记录）——
     * 不得复用 {@link #getLastChangeTime} 时钟语义（DefaultResourceChangeChecker 是
     * lastModified 严格比对，时钟值必然误报变更）。
     */
    private volatile long lastModified;

    private volatile Map<String, Object> definitionConfig = new LinkedHashMap<>();

    private volatile ICoeffectEvaluator coeffectEvaluator;
    private volatile Runnable onConfigChanged;

    private final Object lifecycleLock = new Object();

    /**
     * 连续激活失败阈值（reconcile 自动激活）：达到后暂停自动激活（仅显式 activate() 可恢复），
     * 避免无限重试。机制自 PluginInstanceImpl 迁移到定义级，语义不变。
     */
    static final int AUTO_ACTIVATION_FAILURE_THRESHOLD = 5;

    private volatile PluginScopeImpl scope;
    private volatile IBeanContainer container;
    private volatile InstanceConfigProvider configProvider;
    private volatile Throwable lastActivationError;
    private volatile int activationFailures;
    private volatile boolean autoActivationPaused;

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
     * 数据流缝：manager 在 load 时注入评估器回引（查其他定义激活状态 + 读全局配置）。
     */
    public void setCoeffectEvaluator(ICoeffectEvaluator coeffectEvaluator) {
        this.coeffectEvaluator = coeffectEvaluator;
    }

    /**
     * 定义级 updateConfig 后的回调（热应用路径；manager 注入 {@code this::reconcile}）。
     */
    public void setOnConfigChanged(Runnable onConfigChanged) {
        this.onConfigChanged = onConfigChanged;
    }

    /**
     * 资源真实 lastModified（manager 在 load/reload 解析时记录；变更检测比对源）。
     */
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public long getLastModified() {
        return lastModified;
    }

    /**
     * 定义级 coeffect 门控评估（activate / start 使用）：requires 满足 且 定义级 if-property
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
     * 定义级配置域（load(config) 传入，updateConfig 累积合并更新；激活时送达子容器与 activator）。
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

    /**
     * ACTIVATED 态返回激活 scope（{@link IPluginScope}，quiescence 断言 / 代理解析用）；
     * 否则返回 null。
     */
    public IPluginScope getScope() {
        return state == PluginState.ACTIVATED ? scope : null;
    }

    /**
     * 最近一次激活失败原因（激活失败后记录；成功后清空；FAILED 态可读，错误可观测）。
     */
    public Throwable getLastActivationError() {
        return lastActivationError;
    }

    /**
     * 自动激活是否被失败阈值暂停（reconcile 读取）：连续激活失败 ≥ 阈值 → 暂停
     * reconcile 的自动激活尝试；仅显式 {@link #activate()} 成功可恢复（清计数 + 解除暂停）。
     */
    public boolean isAutoActivationPaused() {
        return autoActivationPaused;
    }

    private void onActivationFailed() {
        if (++activationFailures >= AUTO_ACTIVATION_FAILURE_THRESHOLD && !autoActivationPaused) {
            autoActivationPaused = true;
            LOG.warn("nop.plugin.auto-activation-paused:pluginId={},failures={}", pluginId, activationFailures);
        }
    }

    private void onActivationSucceeded() {
        activationFailures = 0;
        autoActivationPaused = false;
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
        // unload 守卫（01 §三不变量）：ACTIVATED/中间态禁止 unload（须先 deactivate）；
        // FAILED 态错误清理已完成（无容器/无 effect），允许 unload
        if (state == PluginState.ACTIVATED || state == PluginState.ACTIVATING
                || state == PluginState.DEACTIVATING) {
            throw new NopException(ERR_PLUGIN_NOT_DEACTIVATED)
                    .param(ARG_PLUGIN_ID, pluginId)
                    .param(ARG_PLUGIN_STATE, state.name());
        }
        this.state = PluginState.UNLOADED;
        this.definitionConfig = new LinkedHashMap<>();
    }

    @Override
    public boolean activate() {
        synchronized (lifecycleLock) {
            if (state == PluginState.ACTIVATED) {
                // in-flight 单飞：并发重复 activate 幂等，不重跑 activator
                return true;
            }
            if (state == PluginState.ACTIVATING || state == PluginState.DEACTIVATING) {
                // 同步锁下不可达（中间态仅在本次调用内演进）；防御性显式失败，不静默返回
                throw new NopException(ERR_PLUGIN_NOT_DEACTIVATED)
                        .param(ARG_PLUGIN_ID, pluginId)
                        .param(ARG_PLUGIN_STATE, state.name());
            }
            if (state != PluginState.LOADED && state != PluginState.FAILED) {
                throw new NopException(ERR_PLUGIN_DEFINITION_NOT_LOADED)
                        .param(ARG_PLUGIN_ID, pluginId)
                        .param(ARG_PLUGIN_STATE, state.name());
            }
            // 门控不满足 no-op false（不抛异常，§五）；FAILED 视同可重试
            if (!isDefinitionGateSatisfied()) {
                return false;
            }
            this.state = PluginState.ACTIVATING;
            try {
                activateInternal();
                this.state = PluginState.ACTIVATED;
                this.lastActivationError = null;
                onActivationSucceeded();
                return true;
            } catch (RuntimeException | Error e) {
                LOG.error("nop.plugin.activate-fail:pluginId={}", pluginId, e);
                // 回滚：停止本次激活创建的子容器（scope 随之回退）——不残留半激活状态
                rollbackActivation();
                this.state = PluginState.FAILED;
                this.lastActivationError = e;
                onActivationFailed();
                if (e instanceof NopException) {
                    ((NopException) e).param(ARG_PLUGIN_ID, pluginId);
                    throw e;
                }
                throw new NopException(ERR_PLUGIN_ACTIVATION_FAILED)
                        .param(ARG_PLUGIN_ID, pluginId)
                        .cause(e);
            }
        }
    }

    @Override
    public CompletionStage<Void> deactivate() {
        return FutureHelper.futureCall(() -> {
            doDeactivate();
            return null;
        });
    }

    private void doDeactivate() {
        synchronized (lifecycleLock) {
            if (state != PluginState.ACTIVATED) {
                // 幂等：非激活态（LOADED/FAILED/UNLOADED）no-op
                return;
            }
            this.state = PluginState.DEACTIVATING;
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
            this.state = PluginState.LOADED;
        }
    }

    private void activateInternal() {
        Map<String, Object> config = definitionConfig;

        // 定义级配置送达子容器的通道：委托包装 provider（实例机制删除后语义等价迁移——
        // 定义级配置域视图优先、未命中回退全局配置）；先 setConfigProvider 再 start()，
        // 禁止 buildNewInstance（不传播自定义 provider，会静默回落到全局 AppConfig）
        InstanceConfigProvider provider = new InstanceConfigProvider(AppConfig.getConfigProvider());
        provider.setMergedView(config);

        BeansModel beansModel = this.beansModel;
        IClassLoader classLoader = ClassHelper.getSafeClassLoader();
        BeanContainerBuilder builder = new BeanContainerBuilder(classLoader, null);
        if (beansModel != null) {
            builder.addBeansModel(beansModel);
        }
        IBeanContainer container = builder.build(pluginId);
        ((BeanContainerImpl) container).setConfigProvider(provider);
        container.start();

        PluginScopeImpl scope = new PluginScopeImpl(container);
        // 先落字段：激活失败时 rollbackActivation 能回退本批资源
        this.scope = scope;
        this.container = container;
        this.configProvider = provider;

        String activatorName = this.activatorName;
        if (activatorName != null) {
            if (!container.containsBean(activatorName)) {
                throw new NopException(ERR_PLUGIN_ACTIVATOR_NOT_FOUND)
                        .param(ARG_PLUGIN_ID, pluginId)
                        .param(ARG_ACTIVATOR, activatorName);
            }
            Object activatorBean = container.getBean(activatorName);
            if (!(activatorBean instanceof IPluginActivator)) {
                throw new NopException(ERR_PLUGIN_INVALID_ACTIVATOR)
                        .param(ARG_PLUGIN_ID, pluginId)
                        .param(ARG_ACTIVATOR, activatorName);
            }
            // 双参数传递（scope + 定义级配置域视图 config），禁止字段注入 scope
            Disposable returned = ((IPluginActivator) activatorBean).activate(scope, config);
            // 返回值非 null 自动注册为本次激活的 effect（等价 scope.effect()）
            if (returned != null) {
                scope.effect(returned);
            }
        }
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
    public void start(String pluginGroupId, String pluginArtifactId, String pluginVersion,
                      Map<String, Object> config) {
        // §7.1 收敛：start = load + activate。已 LOADED 时 config 并入定义级配置域
        // （LOADED 缓存 / ACTIVATED 热应用，updateConfig 语义）；门控不满足 no-op 返回 false，
        // 记录日志不抛异常
        if (state == PluginState.UNLOADED) {
            load(config);
        } else if (config != null && !config.isEmpty()) {
            updateConfig(config);
        }
        if (!activate()) {
            LOG.warn("nop.plugin.start-gated:pluginId={} (coeffect 定义级条件不满足，未激活)", pluginId);
        }
    }

    @Override
    public void stop() {
        // §7.1 收敛：stop = deactivate + unload
        FutureHelper.syncGet(deactivate());
        unload();
    }

    @Override
    public void updateConfig(Map<String, Object> config) {
        // 定义级配置域累积合并（load 传入的初始 config + updateConfig 累积值）
        Map<String, Object> merged = new LinkedHashMap<>(definitionConfig);
        if (config != null) {
            merged.putAll(config);
        }
        this.definitionConfig = merged;
        if (state == PluginState.ACTIVATED) {
            // 热应用：定义级配置域视图刷新 + 经委托 provider 触发变更通知（bean 属性真实重绑定）
            InstanceConfigProvider provider = configProvider;
            if (provider != null) {
                provider.updateMergedView(merged);
            }
        }
        // LOADED/FAILED 缓存：下次 activate 应用（activateInternal 读最新 definitionConfig）
        // 自动触发：定义级 updateConfig 后经 manager 注入的回调自动 reconcile
        Runnable callback = onConfigChanged;
        if (callback != null) {
            callback.run();
        }
    }

    /**
     * 定义级命令路由（§7.1）：命令 bean 分发于本插件激活容器（单容器）；未激活抛 INACTIVE。
     */
    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                    String fieldSelection,
                                                                    IPluginCancelToken cancelToken) {
        IBeanContainer c = requireActiveContainer();
        String beanName = BEAN_NOP_PLUGIN_COMMAND_PREFIX + command;
        IPluginCommand commandBean = getCommandBean(c, beanName, true);
        if (commandBean == null) {
            commandBean = getCommandBean(c, BEAN_NOP_PLUGIN_COMMAND_PREFIX + "default", false);
        }
        return commandBean.invokeCommandAsync(command, args, fieldSelection, cancelToken);
    }

    @Override
    public Map<String, Object> invokeCommand(String command, Map<String, Object> args,
                                              String fieldSelection,
                                              IPluginCancelToken cancelToken) {
        return FutureHelper.syncGet(invokeCommandAsync(command, args, fieldSelection, cancelToken));
    }

    private IPluginCommand getCommandBean(IBeanContainer c, String beanName, boolean ignoreUnknown) {
        if (c.containsBean(beanName)) {
            return (IPluginCommand) c.getBean(beanName);
        }
        if (ignoreUnknown) {
            Object bean = io.nop.api.core.ioc.BeanContainer.tryGetBean(beanName);
            return bean instanceof IPluginCommand ? (IPluginCommand) bean : null;
        }
        return (IPluginCommand) io.nop.api.core.ioc.BeanContainer.instance().getBean(beanName);
    }

    @Override
    public <T> T getService(Class<T> serviceType) {
        PluginScopeImpl s = requireActiveScope();
        checkProxyable(serviceType);
        // 包装时立即解析一次：多候选/无候选错误在 getService 调用点抛出（不延迟到首次代理调用）
        s.getService(serviceType);
        return ServiceProxy.newInstance(this, serviceType);
    }

    @Override
    public <T> Collection<T> getServices(Class<T> serviceType) {
        PluginScopeImpl s = requireActiveScope();
        checkProxyable(serviceType);
        // 集合代理以 getBeansOfType 的 bean id 为候选键（重激活后按 id 重新解析）
        Map<String, T> beans = s.getServiceBeans(serviceType);
        List<T> proxies = new ArrayList<>(beans.size());
        for (String beanId : beans.keySet()) {
            proxies.add(ServiceProxy.newInstance(this, serviceType, beanId));
        }
        return proxies;
    }

    private PluginScopeImpl requireActiveScope() {
        PluginScopeImpl s = scope;
        if (state != PluginState.ACTIVATED || s == null) {
            throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, pluginId);
        }
        return s;
    }

    private IBeanContainer requireActiveContainer() {
        IBeanContainer c = container;
        if (state != PluginState.ACTIVATED || c == null) {
            throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, pluginId);
        }
        return c;
    }

    /**
     * 生命周期代理仅支持接口类型（具体类无法生成代理）；具体类传入明确抛错，
     * 禁止静默返回裸引用/裸集合（W4 Phase 1 裁定保留）。
     */
    private void checkProxyable(Class<?> serviceType) {
        if (!serviceType.isInterface()) {
            throw new NopException(ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE)
                    .param(ARG_BEAN_TYPE, serviceType.getName())
                    .param(ARG_PLUGIN_ID, pluginId);
        }
    }
}
