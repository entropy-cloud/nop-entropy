/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.module.ModuleManager;
import io.nop.core.module.ModuleModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.UnknownResource;

import static io.nop.core.CoreErrors.ARG_OTHER_PATH;
import static io.nop.core.CoreErrors.ARG_PATH;
import static io.nop.core.CoreErrors.ARG_STD_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_MODULE_PATH_RESOLVE_TO_MULTI_FILE;

public class ModuleNamespaceHandler implements IResourceNamespaceHandler {
    public static final ModuleNamespaceHandler INSTANCE = new ModuleNamespaceHandler();

    @Override
    public String getNamespace() {
        return ResourceConstants.MODULE_NS;
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        String path = ResourceHelper.removeNamespace(vPath, getNamespace());
        ResourceHelper.checkNormalVirtualPath(path);

        IResource resource = null;
        for (ModuleModel module : ModuleManager.instance().getEnabledModules()) {
            String fullPath = "/" + module.getModuleId() + path;
            IResource prev = resource;
            resource = locator.getResource(fullPath, true);
            if (resource != null) {
                if (AppConfig.isDebugMode()) {
                    if (prev != null)
                        throw new NopException(ERR_RESOURCE_MODULE_PATH_RESOLVE_TO_MULTI_FILE)
                                .param(ARG_STD_PATH, vPath).param(ARG_PATH, resource.getPath())
                                .param(ARG_OTHER_PATH, resource.getPath());
                    continue;
                }
                return resource;
            }
        }

        if (resource != null)
            return resource;

        return new UnknownResource(vPath);
    }
}