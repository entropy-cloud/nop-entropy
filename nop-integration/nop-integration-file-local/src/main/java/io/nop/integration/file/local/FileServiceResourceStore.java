/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.file.local;

import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.integration.api.file.FileStatus;
import io.nop.integration.api.file.IFileServiceClient;
import io.nop.integration.api.file.IFileServiceClientFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将FileServiceClient封装为IResourceStore
 */
public class FileServiceResourceStore implements IResourceStore {
    private IFileServiceClientFactory fileServiceClientFactory;
    private String basePath = "/";

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public void setFileServiceClientFactory(IFileServiceClientFactory factory) {
        this.fileServiceClientFactory = factory;
    }

    @Override
    public IResource getResource(String path, boolean returnNullIfNotExists) {
        IResource resource = newResource(path, null);
        if (returnNullIfNotExists && !resource.exists()) {
            return null;
        }
        return resource;
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        IFileServiceClient client = fileServiceClientFactory.newClient();
        try {
            List<FileStatus> children = client.listFiles(path);
            return children.stream().map(fs -> {
                return newResource(StringHelper.appendPath(path, fs.getName()), fs);
            }).collect(Collectors.toList());
        } finally {
            IoHelper.safeCloseObject(client);
        }
    }

    protected IResource newResource(String path, FileStatus status) {
        String fullPath = StringHelper.appendPath(basePath, path);
        return new FileServiceResource(fullPath, path, fileServiceClientFactory, status);
    }

    @Override
    public boolean supportSave(String path) {
        return true;
    }

    @Override
    public String saveResource(String path, IResource resource, IStepProgressListener listener, Map<String, Object> options) {
        IFileServiceClient client = fileServiceClientFactory.newClient();
        try {
            return client.uploadResource(resource, path);
        } finally {
            IoHelper.safeCloseObject(client);
        }
    }
}