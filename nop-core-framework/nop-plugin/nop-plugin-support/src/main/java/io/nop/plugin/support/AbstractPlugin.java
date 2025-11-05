package io.nop.plugin.support;

import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.ioc.loader.AppBeanContainerLoader;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;
import io.nop.plugin.api.NopPluginConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.plugin.api.NopPluginConstants.BEAN_NOP_PLUGIN_COMMAND_PREFIX;

public abstract class AbstractPlugin extends LifeCycleSupport implements IPlugin {
    static final Logger LOG = LoggerFactory.getLogger(AbstractPlugin.class);

    private String pluginGroupId;
    private String pluginArtifactId;
    private String pluginVersion;

    private IBeanContainer beanContainer;
    private Timestamp lastChangeTime;
    private Timestamp loadTime = CoreMetrics.currentTimestamp();
    private boolean autoInit;

    public IBeanContainer getBeanContainer() {
        return beanContainer;
    }

    public void setBeanContainer(IBeanContainer beanContainer) {
        this.beanContainer = beanContainer;
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