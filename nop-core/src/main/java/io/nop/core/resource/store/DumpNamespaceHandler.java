/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.util.MavenDirHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;

import java.io.File;

import static io.nop.core.CoreConfigs.CFG_RESOURCE_DUMP_ROOT_DIR;
import static io.nop.core.resource.ResourceConstants.PLACEHOLDER_PROJECT_PATH;

public class DumpNamespaceHandler implements IResourceNamespaceHandler {
    public static final DumpNamespaceHandler INSTANCE = new DumpNamespaceHandler();

    @Override
    public String getNamespace() {
        return ResourceConstants.RESOURCE_NS_DUMP;
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        String path = ResourceHelper.removeNamespace(vPath, getNamespace());
        ResourceHelper.checkNormalVirtualPath(path);

        String rootDir = CFG_RESOURCE_DUMP_ROOT_DIR.get();
        if (StringHelper.isEmpty(rootDir)) {
            File workDir = MavenDirHelper.guessProjectDir();
            File file = new File(workDir, "_dump/" + AppConfig.appName());
            return new FileResource(vPath, new File(file, path));
        } else {
            if (rootDir.indexOf(PLACEHOLDER_PROJECT_PATH) >= 0) {
                File workDir = MavenDirHelper.guessProjectDir();
                rootDir = StringHelper.replace(rootDir, PLACEHOLDER_PROJECT_PATH, workDir.getAbsolutePath());
            }
            return new FileResource(vPath, new File(rootDir, path));
        }
    }
}
