package io.nop.wf.dao.store;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.wf.dao.NopWfDaoConstants;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class WfResourceNamespaceHandler implements IResourceNamespaceHandler {

    @PostConstruct
    public void init() {
        VirtualFileSystem.instance().registerNamespaceHandler(this);
    }

    @PreDestroy
    public void destroy() {
        VirtualFileSystem.instance().unregisterNamespaceHandler(this);
    }

    @Override
    public String getNamespace() {
        return NopWfDaoConstants.NAMESPACE_WF;
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        String path = ResourceHelper.removeNamespace(vPath, getNamespace());
        ResourceHelper.checkNormalVirtualPath(path);


        return null;
    }
}