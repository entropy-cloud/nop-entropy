package io.nop.plugin.core.support;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.core.initialize.CoreInitialization;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.plugin.core.PluginCoreConstants.BEAN_NOP_PLUGIN_COMMAND_PREFIX;

public abstract class AbstractPlugin extends LifeCycleSupport implements IPlugin {
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
    protected void doStop() {
        if (beanContainer != null)
            beanContainer.stop();

        if (autoInit)
            CoreInitialization.destroy();
    }

    @Override
    protected void doStart() {
        this.loadTime = CoreMetrics.currentTimestamp();

        if (!CoreInitialization.isInitialized()) {
            autoInit = true;
            CoreInitialization.initialize();
        }
    }

    @Override
    public CompletionStage<Map<String, Object>> invokeCommand(String command, Map<String, Object> args, IPluginCancelToken cancelToken) {
        String beanName = BEAN_NOP_PLUGIN_COMMAND_PREFIX + command;
        IPluginCommand commandBean = (IPluginCommand) BeanContainer.tryGetBean(beanName);
        if (commandBean == null) {
            commandBean = (IPluginCommand) BeanContainer.instance().getBean(BEAN_NOP_PLUGIN_COMMAND_PREFIX + "default");
        }
        return commandBean.invokeCommand(command, args, cancelToken);
    }
}