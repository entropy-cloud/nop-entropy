package io.nop.core.resource.store;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;

import java.io.File;

import static io.nop.core.CoreConfigs.CFG_RESOURCE_DATA_ROOT_DIR;

public class DataNamespaceHandler implements IResourceNamespaceHandler {
    public static final TempNamespaceHandler INSTANCE = new TempNamespaceHandler();

    @Override
    public String getNamespace() {
        return ResourceConstants.RESOURCE_NS_DATA;
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        String path = ResourceHelper.removeNamespace(vPath, getNamespace());
        ResourceHelper.checkNormalVirtualPath(path);

        String rootDir = CFG_RESOURCE_DATA_ROOT_DIR.get();
        return new FileResource(vPath, new File(rootDir, path));
    }
}