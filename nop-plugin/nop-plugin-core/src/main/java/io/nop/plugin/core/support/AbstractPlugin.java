package io.nop.plugin.core.support;

import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.plugin.api.IPlugin;

import java.sql.Timestamp;

public abstract class AbstractPlugin extends LifeCycleSupport implements IPlugin {
    private IBeanContainer beanContainer;
    private Timestamp lastChangeTime;
    private Timestamp loadTime = CoreMetrics.currentTimestamp();

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
    }

    protected IResource getBeansResource() {
        ArtifactCoordinates pluginId = null; //getPluginId();
        String path = "classpath:_vfs/plugins/" + getPluginGroupId() + "/" + getPluginArtifactId() + "/" + getPluginVersion() + "/plugin.beans.xml";
        IResource resource = new ClassPathResource(path);
        // "plugin:abc/bb/3.3.0/data;
        return null;
    }

    @Override
    protected void doStart() {

    }
}
