package io.nop.ioc.support;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;

public class BeanContainerHelper {
    public static IBeanContainerImplementor buildContainer(String name, String path) {
        AppBeanContainerLoader loader = new AppBeanContainerLoader();
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return loader.loadFromResource(name, resource, BeanContainer.instance());
    }
}
