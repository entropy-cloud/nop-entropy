/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;

public class ModuleNamespaceHandler implements IResourceNamespaceHandler {
    public static final ModuleNamespaceHandler INSTANCE = new ModuleNamespaceHandler();

    @Override
    public String getNamespace() {
        return ResourceConstants.RESOURCE_NS_MODULE;
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        String path = ResourceHelper.removeNamespace(vPath, getNamespace());
        ResourceHelper.checkNormalVirtualPath(path);

        return ModuleManager.instance().getModuleResource(true, path);
    }
}