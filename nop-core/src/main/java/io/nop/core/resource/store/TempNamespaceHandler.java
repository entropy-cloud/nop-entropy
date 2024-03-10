/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;

import java.io.File;

import static io.nop.core.CoreConfigs.CFG_RESOURCE_TEMP_ROOT_DIR;

public class TempNamespaceHandler implements IResourceNamespaceHandler {
    public static final TempNamespaceHandler INSTANCE = new TempNamespaceHandler();

    @Override
    public String getNamespace() {
        return ResourceConstants.TEMP_NS;
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        String path = ResourceHelper.removeNamespace(vPath, getNamespace());
        ResourceHelper.checkNormalVirtualPath(path);

        String rootDir = CFG_RESOURCE_TEMP_ROOT_DIR.get();
        if (StringHelper.isEmpty(rootDir)) {
            String workDir = System.getProperty("java.io.tmpdir");
            File file = new File(workDir, "_temp/" + AppConfig.appName());
            return new FileResource(vPath, new File(file, path));
        } else {
            return new FileResource(vPath, new File(rootDir, path));
        }
    }
}