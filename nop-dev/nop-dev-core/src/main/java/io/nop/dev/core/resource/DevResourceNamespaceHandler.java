/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dev.core.resource;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import io.nop.dev.core.DevCoreConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.File;

import static io.nop.dev.core.DevCoreConfigs.CFG_DEV_ROOT_PATH;

public class DevResourceNamespaceHandler implements IResourceNamespaceHandler {
    public static DevResourceNamespaceHandler INSTANCE = new DevResourceNamespaceHandler();

    @PostConstruct
    public void register() {
        VirtualFileSystem.instance().registerNamespaceHandler(INSTANCE);
    }

    @PreDestroy
    public void unregister() {
        VirtualFileSystem.instance().unregisterNamespaceHandler(INSTANCE);
    }

    @Override
    public String getNamespace() {
        return DevCoreConstants.RESOURCE_NS_DEV;
    }

    @Override
    public IResource getResource(String path, IResourceStore locator) {
        Guard.checkArgument(StringHelper.startsWithNamespace(path, DevCoreConstants.RESOURCE_NS_DEV), "path must startsWith dev:");
        Guard.checkArgument(!path.contains(".."), "invalid path");

        String rootPath = CFG_DEV_ROOT_PATH.get();
        if (StringHelper.isEmpty(rootPath)) {
            rootPath = FileHelper.currentDir().getAbsolutePath();
        }

        String devPath = path.substring(DevCoreConstants.RESOURCE_NS_DEV.length() + 1);

        File file = new File(rootPath, devPath);
        return new FileResource(path, file);
    }
}