package io.nop.plugin.support;

import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.ioc.loader.AppBeanContainerLoader;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;
import io.nop.plugin.api.NopPluginConstants;
import io.nop.plugin.api.PluginState;
import io.nop.xlang.xdsl.DslModelParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.plugin.api.NopPluginConstants.BEAN_NOP_PLUGIN_COMMAND_PREFIX;
import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_STATE;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_NOT_DEACTIVATED;

/**
 * 插件基类（uber jar 轨持有类）：状态机感知（{@link #isStateMachineAware()} 默认 true）双路径实现，
 * 承载单层六态状态机（01-architecture-baseline.md §7.1）。
 *
 * <p><b>aware 路径</b>（默认）——jar 轨契约（01 §二裁决）：
 * <ul>
 *     <li>{@link #load(Map)} 容忍 xdef 缺失：约定路径（{@link #getPluginDefinitionPath()}）无
 *     {@code *.plugin.xml} 时以空定义加载成功；有载体时仍解析持有，但仅作元数据
 *     （不驱动 jar 轨门控/activator）。</li>
 *     <li>{@link #activate()} 门控恒为空集——<b>无条件激活</b>（requires/if-property 不评估）；
 *     激活回调统一跳过（无 activator 概念），容器构建复用旧 doStart 路径逻辑
 *     （plugin.beans.xml 经 {@link AppBeanContainerLoader}、parent = 宿主容器）——
 *     子容器启动即 ACTIVATED。</li>
 *     <li>{@link #deactivate()} 子容器 stop；{@link #unload()} 守卫：ACTIVATED/中间态抛明确异常。</li>
 *     <li>start/stop 收敛为 §7.1 目标语义（start = load + activate、stop = deactivate + unload），
 *     状态边界两轨统一规格：UNLOADED 才补 load 步（已 LOADED/FAILED 不重复 load 直接 activate）；
 *     已 ACTIVATED 时 start 幂等 no-op（activate 幂等，不重建容器）；UNLOADED 态 stop 幂等 no-op。</li>
 *     <li>invokeCommand 定义级路由：命令 bean 分发于本插件激活容器（未接通路径显式抛异常，
 *     不静默返回）；回退链与 VfsPluginDefinition 一致（插件容器 → 宿主回退 → default bean 兜底），
 *     不分叉。</li>
 * </ul>
 *
 * <p><b>兼容路径</b>（子类 override {@code isStateMachineAware()} 返回 false）：保留旧 start/stop
 * 语义（{@code AppConfig.assignConfigValue} 全局写入 + doStart 子容器创建），行为与改造前等价。
 */
public abstract class AbstractPlugin extends LifeCycleSupport implements IPlugin {
    static final Logger LOG = LoggerFactory.getLogger(AbstractPlugin.class);

    private String pluginGroupId;
    private String pluginArtifactId;
    private String pluginVersion;

    /**
     * 状态机字段 volatile（裁定① C1-011 双轨防护对齐，与 VfsPluginDefinition 轨既有风格一致）：
     * state/beanContainer/definition/lastActivationError 由生命周期线程与消费方线程并发读写，
     * volatile 保证复合读双检（取引用 → 复核 state）的可见性前提。坐标/时间戳字段维持现状
     * （启动期写读，条件安全口径）。
     */
    private volatile IBeanContainer beanContainer;
    private Timestamp lastChangeTime;
    private Timestamp loadTime = CoreMetrics.currentTimestamp();
    private boolean autoInit;

    private volatile DynamicObject definition;
    private volatile PluginState state = PluginState.UNLOADED;
    private volatile Throwable lastActivationError;

    private final Object lifecycleLock = new Object();

    public IBeanContainer getBeanContainer() {
        return beanContainer;
    }

    public void setBeanContainer(IBeanContainer beanContainer) {
        this.beanContainer = beanContainer;
    }

    @Override
    public boolean isStateMachineAware() {
        return true;
    }

    @Override
    public PluginState getState() {
        if (!isStateMachineAware())
            return PluginState.LOADED;
        return state;
    }

    /**
     * aware 路径静态定义（load 后非 null；xdef 载体缺失时空定义加载成功为 null）；
     * 未 load / 已 unload 为 null。有载体时仅作元数据持有，不驱动门控/activator。
     */
    public DynamicObject getPluginDefinition() {
        return definition;
    }

    /**
     * aware 路径定义文件路径（VFS 约定路径，默认 {@link NopPluginConstants#PLUGIN_DEFINITION_FILE}）。
     */
    protected String getPluginDefinitionPath() {
        return NopPluginConstants.PLUGIN_DEFINITION_FILE;
    }

    /**
     * 插件子容器 bean 装配文件路径（VFS 约定路径，默认
     * {@link NopPluginConstants#PLUGIN_BEANS_FILE}；aware 与兼容路径共用）。
     * 路径无文件时 aware 激活成功但无子容器（命令经回退链解析）。
     */
    protected String getPluginBeansPath() {
        return NopPluginConstants.PLUGIN_BEANS_FILE;
    }

    /**
     * 最近一次激活失败原因（激活失败后记录；成功后清空；FAILED 态可读）。
     */
    public Throwable getLastActivationError() {
        return lastActivationError;
    }

    @Override
    public void load(Map<String, Object> config) {
        // 锁矩阵（裁定①双轨对齐）：迁移写（definition/state/loadTime）不可分割
        synchronized (lifecycleLock) {
            // jar 轨契约：容忍 xdef 缺失——无载体时空定义加载成功；有载体时解析持有（仅元数据）
            String defPath = getPluginDefinitionPath();
            IResource defResource = VirtualFileSystem.instance().getResource(defPath);
            if (defResource.exists()) {
                try {
                    this.definition = (DynamicObject) new DslModelParser(NopPluginConstants.PLUGIN_XDEF_PATH)
                            .parseFromVirtualPath(defPath);
                } catch (NopException e) {
                    throw e.param(ARG_PLUGIN_ID, defPath);
                }
            } else {
                this.definition = null;
            }
            this.state = PluginState.LOADED;
            this.loadTime = CoreMetrics.currentTimestamp();
        }
    }

    @Override
    public void unload() {
        synchronized (lifecycleLock) {
            // unload 守卫（01 §三不变量）：ACTIVATED/中间态禁止 unload（须先 deactivate）；
            // FAILED 态错误清理已完成，允许 unload。守卫读与迁移写同锁（裁定①/C2-004 双轨对齐）
            if (isStateMachineAware()) {
                if (state == PluginState.ACTIVATED || state == PluginState.ACTIVATING
                        || state == PluginState.DEACTIVATING) {
                    throw new NopException(ERR_PLUGIN_NOT_DEACTIVATED)
                            .param(ARG_PLUGIN_ID, describePluginId())
                            .param(ARG_PLUGIN_STATE, state.name());
                }
                // FAILED 态残留字段幂等尽力清理（裁定②）：防「丢弃引用漏停容器」
                // （成功路径字段已空，零行为变化）
                if (state == PluginState.FAILED) {
                    bestEffortCleanupResidual();
                    beanContainer = null;
                }
            }
            this.definition = null;
            this.state = PluginState.UNLOADED;
        }
    }

    @Override
    public boolean activate() {
        if (!isStateMachineAware()) {
            // 非 aware 插件不进入新状态机：显式失败（No Silent No-Op），不静默返回
            throw new NopException(ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED);
        }
        synchronized (lifecycleLock) {
            if (state == PluginState.ACTIVATED) {
                // 幂等：并发重复 activate 不重跑
                return true;
            }
            if (state == PluginState.ACTIVATING || state == PluginState.DEACTIVATING) {
                throw new NopException(ERR_PLUGIN_NOT_DEACTIVATED)
                        .param(ARG_PLUGIN_ID, describePluginId())
                        .param(ARG_PLUGIN_STATE, state.name());
            }
            if (state != PluginState.LOADED && state != PluginState.FAILED) {
                throw new NopException(ERR_PLUGIN_NOT_DEACTIVATED)
                        .param(ARG_PLUGIN_ID, describePluginId())
                        .param(ARG_PLUGIN_STATE, state.name());
            }
            // jar 轨门控恒为空集：无条件激活（requires/if-property 不评估）；激活回调统一跳过
            this.state = PluginState.ACTIVATING;
            try {
                buildBeanContainer(true);
                this.state = PluginState.ACTIVATED;
                this.lastActivationError = null;
                return true;
            } catch (RuntimeException | Error e) {
                LOG.error("nop.plugin.activate-fail:pluginId={}", describePluginId(), e);
                // 回滚尽力清理（裁定②）：清理异常降级为留痕，不阻断下方 FAILED 落定
                rollbackActivation();
                this.state = PluginState.FAILED;
                this.lastActivationError = e;
                throw e;
            }
        }
    }

    /**
     * 容器构建（复用旧 doStart 路径逻辑）：CoreInitialization 按需初始化 + plugin.beans.xml
     * （{@link #getPluginBeansPath()}）经 {@link AppBeanContainerLoader} 装配
     * （parent = 宿主容器）。容器 id 容忍坐标未设置（activate 可先于 start 调用，
     * groupId/artifactId 为 null）。
     *
     * @param startAwarePath true = aware activate 路径：build 后显式 start（加载器只 build 不
     *                       start；jar 轨契约"子容器启动即 ACTIVATED"）；false = 兼容路径
     *                       （doStart）：保持改造前行为（build 不 start）
     */
    protected void buildBeanContainer(boolean startAwarePath) {
        this.loadTime = CoreMetrics.currentTimestamp();

        if (!CoreInitialization.isInitialized()) {
            autoInit = true;
            CoreInitialization.initialize();
        }

        IResource beansResource = VirtualFileSystem.instance().getResource(getPluginBeansPath());
        if (beansResource.exists()) {
            String containerId = pluginGroupId != null ? getPluginId().toString() : getClass().getName();
            IBeanContainer container = new AppBeanContainerLoader().loadFromResource(containerId, beansResource,
                    BeanContainer.instance());
            if (startAwarePath) {
                container.start();
            }
            beanContainer = container;
        } else {
            LOG.info("nop.plugin.no-plugin-beans:pluginId={}", describePluginId());
        }
    }

    /**
     * 激活失败回滚（裁定②失败终态表第 4 行）：尽力清理，清理异常降级为留痕（LOG.error），
     * 不阻断 activate catch 内的 FAILED 落定。清理成功 → beanContainer 置空；失败 → 保留
     * 原引用（FAILED 态残留 = 待恢复入口 forceUnload 处置的显式信号）。
     */
    private void rollbackActivation() {
        IBeanContainer c = beanContainer;
        boolean cleanupFailed = false;
        if (c != null) {
            try {
                c.stop();
            } catch (RuntimeException | Error e) {
                cleanupFailed = true;
                LOG.error("nop.plugin.rollback-container-stop-fail:pluginId={}", describePluginId(), e);
            }
        }
        if (!cleanupFailed) {
            beanContainer = null;
        }
    }

    @Override
    public CompletionStage<Void> deactivate() {
        if (!isStateMachineAware()) {
            // 非 aware 插件不进入新状态机：显式失败（No Silent No-Op）
            throw new NopException(ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED);
        }
        return FutureHelper.futureCall(() -> {
            doDeactivate();
            return null;
        });
    }

    private void doDeactivate() {
        synchronized (lifecycleLock) {
            if (state != PluginState.ACTIVATED) {
                // 幂等：非激活态 no-op
                return;
            }
            this.state = PluginState.DEACTIVATING;
            IBeanContainer c = beanContainer;
            // 清理逐段尽力、失败聚合（裁定②失败终态表）：失败路径保留原引用，成功路径清理后置空
            Throwable failure = null;
            try {
                if (c != null) {
                    c.stop();
                }
            } catch (RuntimeException | Error e) {
                failure = e;
                LOG.error("nop.plugin.deactivate-container-stop-fail:pluginId={}", describePluginId(), e);
            }
            // autoInit 的 CoreInitialization.destroy() 纳入独立尽力段（裁定②）：stop 抛错不跳过
            // destroy（全局初始化/拆除配对不因插件清理失败而破坏）
            if (autoInit) {
                autoInit = false;
                try {
                    CoreInitialization.destroy();
                } catch (RuntimeException | Error e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                    LOG.error("nop.plugin.deactivate-core-destroy-fail:pluginId={}", describePluginId(), e);
                }
            }
            if (failure != null) {
                // 失败落定 FAILED（临界区出口前落定，不滞留中间态）；lastActivationError 记因
                this.state = PluginState.FAILED;
                this.lastActivationError = failure;
                if (failure instanceof RuntimeException) {
                    throw (RuntimeException) failure;
                }
                throw (Error) failure;
            }
            beanContainer = null;
            this.state = PluginState.LOADED;
        }
    }

    @Override
    public String getPluginGroupId() {
        return pluginGroupId;
    }

    @Override
    public String getPluginArtifactId() {
        return pluginArtifactId;
    }

    @Override
    public String getPluginVersion() {
        return pluginVersion;
    }

    @Override
    public Timestamp getLastChangeTime() {
        return lastChangeTime;
    }

    public void setLastChangeTime(Timestamp lastChangeTime) {
        this.lastChangeTime = lastChangeTime;
    }

    @Override
    public Timestamp getLoadTime() {
        return loadTime;
    }

    public void setLoadTime(Timestamp loadTime) {
        this.loadTime = loadTime;
    }

    @Override
    public void start(String pluginGroupId, String pluginArtifactId, String pluginVersion,
                      Map<String, Object> config) {
        if (isStateMachineAware()) {
            // §7.1 收敛 + 状态边界（两轨统一规格）：start = load + activate（jar 轨门控空集 →
            // activate 恒为 true）。仅 UNLOADED 补 load 步；已 LOADED/FAILED 不重复 load 直接
            // activate；已 ACTIVATED 时 activate 幂等 no-op（不重建容器）。
            // 锁矩阵（裁定①双轨对齐）：aware 段全程 lifecycleLock（load/activate 锁内重入）
            synchronized (lifecycleLock) {
                this.pluginGroupId = pluginGroupId;
                this.pluginArtifactId = pluginArtifactId;
                this.pluginVersion = pluginVersion;
                if (state == PluginState.UNLOADED) {
                    load(config);
                }
                activate();
                return;
            }
        }

        // 兼容路径（非 aware 轨无六态迁移，锁迁移面显式豁免）
        this.pluginGroupId = pluginGroupId;
        this.pluginArtifactId = pluginArtifactId;
        this.pluginVersion = pluginVersion;

        IConfigProvider configProvider = AppConfig.getConfigProvider();
        if (config != null) {
            config.forEach(configProvider::assignConfigValue);
        }
        start();
    }

    @Override
    public void stop() {
        if (isStateMachineAware()) {
            // §7.1 收敛：stop = deactivate + unload（两轨统一边界：UNLOADED 态幂等 no-op——
            // deactivate 非激活态 no-op、unload 对 UNLOADED 幂等）。
            // 锁矩阵（裁定①）：stop aware 段 lifecycleLock（deactivate/unload 锁内重入；
            // futureCall 任务体在调用线程执行，无跨线程持锁）
            synchronized (lifecycleLock) {
                FutureHelper.syncGet(deactivate());
                unload();
                return;
            }
        }
        super.stop();
    }

    @Override
    protected void doStop() {
        try {
            if (beanContainer != null)
                beanContainer.stop();
        } finally {
            if (autoInit)
                CoreInitialization.destroy();
        }
    }

    @Override
    protected void doStart() {
        // 兼容路径（非 aware）：保持改造前行为（build 不 start）
        buildBeanContainer(false);
    }

    public ArtifactCoordinates getPluginId() {
        return new ArtifactCoordinates(getPluginGroupId(), getPluginArtifactId(), getPluginVersion());
    }

    /**
     * 日志/错误参数用的 id 描述（容忍坐标未设置——activate 可先于 start 调用）。
     */
    private String describePluginId() {
        return pluginGroupId != null ? getPluginId().toString() : getClass().getName();
    }

    /**
     * FAILED 态残留资源幂等尽力清理（须持 lifecycleLock 调用）：容器 stop 逐段尽力，
     * 异常留痕不阻断（不吞不抛，状态迁移由调用方继续）。
     */
    private void bestEffortCleanupResidual() {
        IBeanContainer c = beanContainer;
        if (c != null) {
            try {
                c.stop();
            } catch (RuntimeException | Error e) {
                LOG.error("nop.plugin.residual-container-stop-fail:pluginId={}", describePluginId(), e);
            }
        }
    }

    /**
     * 平台内部恢复契约（裁定②）：{@code PluginManagerImpl.forceUnload} 专用——
     * <b>非插件开发者 API</b>。不经 {@link #unload()} 守卫（守卫拒绝 ACTIVATING/DEACTIVATING
     * 且不接受残留字段），在 lifecycleLock 内原子完成：残留容器尽力 stop（异常留痕不阻断）→
     * 字段置空 → 落 UNLOADED。lastActivationError 保留供诊断。public 可见性为跨包调用所需
     * （nop-plugin-support 为 impl 模块，不构成公共 API 面）。
     *
     * <p>锁内复核：manager 侧前置态判定在 lifecycleLock 外读取，若受理时为 ACTIVATING 而等锁
     * 期间在途激活已成功落 ACTIVATED，则无可恢复对象——不强杀健康活容器，返回 false 交 manager
     * 作类型化拒绝（holder 保留）；其余状态恢复并返回 true。
     *
     * @return 是否已落定 UNLOADED；false 表示锁内复核为 ACTIVATED、未做任何迁移
     */
    public boolean recoverToUnloaded() {
        synchronized (lifecycleLock) {
            if (state == PluginState.ACTIVATED) {
                return false;
            }
            bestEffortCleanupResidual();
            this.beanContainer = null;
            this.definition = null;
            this.state = PluginState.UNLOADED;
            return true;
        }
    }

    /**
     * 命令 bean 解析回退链（与 VfsPluginDefinition 同一链，不分叉）：本插件容器优先 →
     * 宿主容器回退（ignoreUnknown=true 时 miss 返回 null）→ default bean 兜底
     * （ignoreUnknown=false 时 miss 经 getBean 显式抛异常，不静默返回）。
     */
    protected IPluginCommand getCommandBean(String beanName, boolean ignoreUnknown) {
        return getCommandBean(this.beanContainer, beanName, ignoreUnknown);
    }

    /**
     * 回退链实现（容器引用参数化）：aware 命令路由的复合读双检（3.1.4 站点 7 附加面）要求
     * 同一容器引用贯穿路由（取引用 → 复核 state → 引用使用），不二次取字段。
     */
    private IPluginCommand getCommandBean(IBeanContainer container, String beanName, boolean ignoreUnknown) {
        if (container != null) {
            if (container.containsBean(beanName))
                return (IPluginCommand) container.getBean(beanName);
        }
        if (ignoreUnknown) {
            Object bean = BeanContainer.tryGetBean(beanName);
            return bean instanceof IPluginCommand ? (IPluginCommand) bean : null;
        }
        return (IPluginCommand) BeanContainer.instance().getBean(beanName);
    }

    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                    String fieldSelection,
                                                                    IPluginCancelToken cancelToken) {
        if (isStateMachineAware()) {
            // 定义级路由：命令 bean 分发于本插件激活容器；未激活显式抛 INACTIVE（不静默返回）。
            // 复合读双检（裁定① 3.1.4 站点 7 附加面）：取容器引用 → 复核 state → 同一引用贯穿
            // 路由，deactivate 翻转窗统一快速失败 INACTIVE。容器为 null（ACTIVATED 但无 beans 文件）
            // 是合法形态，沿既有宿主回退链解析，不判 INACTIVE
            IBeanContainer container = beanContainer;
            if (getState() != PluginState.ACTIVATED) {
                throw new NopException(ERR_PLUGIN_INACTIVE)
                        .param(ARG_PLUGIN_ID, getPluginDefinitionPath());
            }
            String beanName = BEAN_NOP_PLUGIN_COMMAND_PREFIX + command;
            IPluginCommand commandBean = getCommandBean(container, beanName, true);
            if (commandBean == null) {
                commandBean = getCommandBean(container, BEAN_NOP_PLUGIN_COMMAND_PREFIX + "default", false);
            }
            return commandBean.invokeCommandAsync(command, args, fieldSelection, cancelToken);
        }

        String beanName = BEAN_NOP_PLUGIN_COMMAND_PREFIX + command;
        IPluginCommand commandBean = getCommandBean(beanName, true);
        if (commandBean == null) {
            commandBean = getCommandBean(BEAN_NOP_PLUGIN_COMMAND_PREFIX + "default", false);
        }
        return commandBean.invokeCommandAsync(command, args, fieldSelection, cancelToken);
    }

    @Override
    public Map<String, Object> invokeCommand(String command, Map<String, Object> args,
                                              String fieldSelection,
                                              IPluginCancelToken cancelToken) {
        return FutureHelper.syncGet(invokeCommandAsync(command, args, fieldSelection, cancelToken));
    }
}
