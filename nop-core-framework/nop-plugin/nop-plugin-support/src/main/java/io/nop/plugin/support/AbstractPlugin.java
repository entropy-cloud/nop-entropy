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
 *     <li>start/stop 收敛为 §7.1 目标语义（start = load + activate、stop = deactivate + unload）。</li>
 *     <li>invokeCommand 定义级路由：命令 bean 分发于本插件激活容器（未接通路径显式抛异常，
 *     不静默返回）。</li>
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

    private IBeanContainer beanContainer;
    private Timestamp lastChangeTime;
    private Timestamp loadTime = CoreMetrics.currentTimestamp();
    private boolean autoInit;

    private DynamicObject definition;
    private PluginState state = PluginState.UNLOADED;
    private Throwable lastActivationError;

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
     * 最近一次激活失败原因（激活失败后记录；成功后清空；FAILED 态可读）。
     */
    public Throwable getLastActivationError() {
        return lastActivationError;
    }

    @Override
    public void load(Map<String, Object> config) {
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

    @Override
    public void unload() {
        // unload 守卫（01 §三不变量）：ACTIVATED/中间态禁止 unload（须先 deactivate）；
        // FAILED 态错误清理已完成，允许 unload
        if (isStateMachineAware()) {
            if (state == PluginState.ACTIVATED || state == PluginState.ACTIVATING
                    || state == PluginState.DEACTIVATING) {
                throw new NopException(ERR_PLUGIN_NOT_DEACTIVATED)
                        .param(ARG_PLUGIN_ID, describePluginId())
                        .param(ARG_PLUGIN_STATE, state.name());
            }
        }
        this.definition = null;
        this.state = PluginState.UNLOADED;
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
                rollbackActivation();
                this.state = PluginState.FAILED;
                this.lastActivationError = e;
                throw e;
            }
        }
    }

    /**
     * 容器构建（复用旧 doStart 路径逻辑）：CoreInitialization 按需初始化 + plugin.beans.xml
     * 经 {@link AppBeanContainerLoader} 装配（parent = 宿主容器）。容器 id 容忍坐标未设置
     * （activate 可先于 start 调用，groupId/artifactId 为 null）。
     *
     * @param startAwarePath true = aware activate 路径：build 后显式 start（加载器只 build 不
     *                       start；jar 轨契约"子容器启动即 ACTIVATED"）；false = 兼容路径
     *                       （doStart）：保持改造前行为（build 不 start）
     */
    private void buildBeanContainer(boolean startAwarePath) {
        this.loadTime = CoreMetrics.currentTimestamp();

        if (!CoreInitialization.isInitialized()) {
            autoInit = true;
            CoreInitialization.initialize();
        }

        IResource beansResource = VirtualFileSystem.instance().getResource(NopPluginConstants.PLUGIN_BEANS_FILE);
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

    private void rollbackActivation() {
        IBeanContainer c = beanContainer;
        beanContainer = null;
        if (c != null) {
            c.stop();
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
            beanContainer = null;
            if (c != null) {
                c.stop();
            }
            if (autoInit) {
                autoInit = false;
                CoreInitialization.destroy();
            }
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
            // §7.1 收敛：start = load + activate（jar 轨门控空集 → activate 恒为 true）
            this.pluginGroupId = pluginGroupId;
            this.pluginArtifactId = pluginArtifactId;
            this.pluginVersion = pluginVersion;
            load(config);
            activate();
            return;
        }

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
            // §7.1 收敛：stop = deactivate + unload
            FutureHelper.syncGet(deactivate());
            unload();
            return;
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

    protected IPluginCommand getCommandBean(String beanName, boolean ignoreUnknown) {
        if (beanContainer != null) {
            if (beanContainer.containsBean(beanName))
                return (IPluginCommand) beanContainer.getBean(beanName);
        }
        if (ignoreUnknown)
            return (IPluginCommand) BeanContainer.tryGetBean(beanName);
        return (IPluginCommand) BeanContainer.instance().getBean(beanName);
    }

    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                    String fieldSelection,
                                                                    IPluginCancelToken cancelToken) {
        if (isStateMachineAware()) {
            // 定义级路由：命令 bean 分发于本插件激活容器；未激活显式抛 INACTIVE（不静默返回）
            if (getState() != PluginState.ACTIVATED) {
                throw new NopException(ERR_PLUGIN_INACTIVE)
                        .param(ARG_PLUGIN_ID, getPluginDefinitionPath());
            }
            String beanName = BEAN_NOP_PLUGIN_COMMAND_PREFIX + command;
            IPluginCommand commandBean = getCommandBean(beanName, true);
            if (commandBean == null) {
                commandBean = getCommandBean(BEAN_NOP_PLUGIN_COMMAND_PREFIX + "default", false);
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
