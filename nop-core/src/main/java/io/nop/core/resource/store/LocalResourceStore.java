/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.impl.FileResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 基于本地文件夹实现的ResourceStore
 */
public class LocalResourceStore implements IResourceStore {
    static final Logger LOG = LoggerFactory.getLogger(LocalResourceStore.class);

    private final String basePath;
    private final File dir;

    public LocalResourceStore(String basePath, File dir) {
        Guard.checkArgument(basePath.endsWith("/"), "basePath must ends with slash");
        if (!dir.isDirectory())
            LOG.warn("nop.warn.core.resource.store.directory-not-exists:basePath={},dir={}", basePath, dir);
        this.basePath = basePath;
        this.dir = dir;
    }

    @Override
    public FileResource getResource(String path, boolean returnNullIfNotExists) {
        Guard.checkArgument(path.startsWith(basePath), "invalid path");
        String relativePath = path.substring(basePath.length());
        File file = new File(dir, relativePath);
        if (returnNullIfNotExists && !file.exists())
            return null;
        FileResource resource = new FileResource(path, file);
        return resource;
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        FileResource resource = getResource(path, true);
        if (resource == null)
            return null;
        return resource.getChildren();
    }

    @Override
    public boolean supportSave(String path) {
        return path.startsWith(basePath);
    }

    @Override
    public String saveResource(String path, IResource resource, IStepProgressListener listener,
                               Map<String, Object> options) {
        IResource targetResource = getResource(path);
        resource.saveToResource(targetResource);
        return path;
    }
}