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
import io.nop.plugin.api.IPluginInstance;
import io.nop.plugin.api.NopPluginConstants;
import io.nop.plugin.api.PluginState;
import io.nop.xlang.xdsl.DslModelParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.plugin.api.NopPluginConstants.BEAN_NOP_PLUGIN_COMMAND_PREFIX;
import static io.nop.plugin.api.PluginApiErrors.ARG_PLUGIN_ID;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_DEFINITION_NOT_FOUND;
import static io.nop.plugin.api.PluginApiErrors.ERR_PLUGIN_INACTIVE;

/**
 * 插件基类：状态机感知（{@link #isStateMachineAware()} 默认 true）双路径实现。
 *
 * <p><b>aware 路径</b>（默认）：{@link #load(Map)} 从 VFS 约定路径（{@link #getPluginDefinitionPath()}，
 * 默认 {@link NopPluginConstants#PLUGIN_DEFINITION_FILE}）经 plugin.xdef 解析静态定义并持有，
 * 状态到 {@link PluginState#LOADED}——<b>不创建子容器</b>（子容器创建移入 W3 createInstance）；
 * {@link #unload()} 丢弃定义回 UNLOADED；{@code start/stop} 按设计 §7.1 语义收敛
 * （start = load + createInstance(默认 key)，stop = destroyInstance + unload）——createInstance
 * 对 uber jar 轨（本类的实例化路径）为显式 successor 项（W4/W7），start 明确失败；
 * 无 ACTIVATED 实例时 invokeCommand 抛 INACTIVE（不回退宿主容器）。
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
     * aware 路径静态定义（load 后非 null）；未 load / 已 unload 为 null。
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

    @Override
    public void load(Map<String, Object> config) {
        String defPath = getPluginDefinitionPath();
        IResource defResource = VirtualFileSystem.instance().getResource(defPath);
        if (!defResource.exists()) {
            throw new NopException(ERR_PLUGIN_DEFINITION_NOT_FOUND).param(ARG_PLUGIN_ID, defPath);
        }
        try {
            this.definition = (DynamicObject) new DslModelParser(NopPluginConstants.PLUGIN_XDEF_PATH)
                    .parseFromVirtualPath(defPath);
        } catch (NopException e) {
            throw e.param(ARG_PLUGIN_ID, defPath);
        }
        this.state = PluginState.LOADED;
        this.loadTime = CoreMetrics.currentTimestamp();
    }

    @Override
    public void unload() {
        this.definition = null;
        this.state = PluginState.UNLOADED;
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
            // §7.1 收敛：start = load + createInstance(默认 key)。load 落地；
            // createInstance 对 uber jar 轨（plugin.json 定义，无 plugin.xdef/activator 载体）
            // 为显式 successor 项（W4/W7 评估）——明确失败（No Silent No-Op），
            // VFS 轨的默认 key 实例化已由 W3 定义持有类（VfsPluginDefinition）落地。
            load(config);
            throw new UnsupportedOperationException(
                    "jar-track (plugin.json) instance creation is a successor item (W4/W7); "
                            + "start = load + createInstance(default key) is fully landed for the VFS track");
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
            // §7.1 收敛：stop = destroyInstance + unload；jar 轨无实例（createInstance 为
            // successor 项），destroyInstance 空操作，unload 落地
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
        this.loadTime = CoreMetrics.currentTimestamp();

        if (!CoreInitialization.isInitialized()) {
            autoInit = true;
            CoreInitialization.initialize();
        }

        IResource beansResource = VirtualFileSystem.instance().getResource(NopPluginConstants.PLUGIN_BEANS_FILE);
        if (beansResource.exists()) {
            beanContainer = new AppBeanContainerLoader().loadFromResource(getPluginId().toString(), beansResource, BeanContainer.instance());
        } else {
            LOG.info("nop.plugin.no-plugin-beans:pluginId={}", getPluginId());
        }
    }

    public ArtifactCoordinates getPluginId() {
        return new ArtifactCoordinates(getPluginGroupId(), getPluginArtifactId(), getPluginVersion());
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
            List<IPluginInstance> instances = getInstances();
            if (instances.isEmpty()) {
                throw new NopException(ERR_PLUGIN_INACTIVE).param(ARG_PLUGIN_ID, getPluginDefinitionPath());
            }
            throw new UnsupportedOperationException(
                    "not yet implemented: per-instance command routing (W4)");
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
