/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.impl;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLoader;

import java.util.Collection;

public class RelativeResourceLoader implements IResourceLoader {
    private final IResourceLoader loader;
    private final String basePath;

    public RelativeResourceLoader(IResourceLoader loader, String basePath) {
        this.loader = loader;
        this.basePath = basePath;
    }

    public String getBasePath() {
        return basePath;
    }

    @Override
    public IResource getResource(String path) {
        String fullPath = StringHelper.appendPath(basePath, path);
        return loader.getResource(fullPath);
    }

    @Override
    public Collection<? extends IResource> getChildren(String path) {
        String fullPath = StringHelper.appendPath(basePath, path);
        return loader.getChildren(fullPath);
    }
}