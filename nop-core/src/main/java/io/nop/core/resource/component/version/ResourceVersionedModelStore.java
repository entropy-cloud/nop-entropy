/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component.version;

import io.nop.api.core.util.IComponentModel;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ResourceVersionedModelStore<T extends IComponentModel> implements IVersionedModelStore<T> {
    private String basePath;
    private String fileType;

    public void setBasePath(String basePath) {
        if (!basePath.endsWith("/"))
            basePath = basePath + "/";
        this.basePath = basePath;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    @Override
    public Long getLatestVersion(String modelName) {
        List<IResource> resources = ModuleManager.instance().findModuleResources(basePath + modelName, "." + fileType);
        Collections.sort(resources, Comparator.comparing(IResource::getName));
        if (resources.isEmpty())
            return null;
        IResource resource = resources.get(resources.size() - 1);
        return ResourceVersionHelper.getNumberVersion(resource.getName());
    }

    @Override
    public List<Long> getAllVersions(String modelName) {
        List<IResource> resources = ModuleManager.instance().findModuleResources(basePath + modelName, "." + fileType);
        Collections.sort(resources, Comparator.comparing(IResource::getName));
        if (resources.isEmpty())
            return Collections.emptyList();
        return resources.stream().map(resource -> ResourceVersionHelper.getNumberVersion(resource.getName())).collect(Collectors.toList());
    }

    public IResource getModelResource(String modelName, Long modelVersion) {
        modelVersion = normalizeVersion(modelName, modelVersion);
        String path = ResourceVersionHelper.buildPath("module:" + basePath, modelName, modelVersion, "xwf");

        return VirtualFileSystem.instance().getResource(path);
    }

    private Long normalizeVersion(String modelName, Long modelVersion) {
        if (modelVersion == null || modelVersion <= 0) {
            modelVersion = getLatestVersion(modelName);
            if (modelVersion == null)
                modelVersion = 1L;
        }
        return modelVersion;
    }

    @Override
    public T getModel(String modelName, Long modelVersion) {
        modelVersion = normalizeVersion(modelName, modelVersion);

        String path = ResourceVersionHelper.buildPath("module:" + basePath, modelName, modelVersion, fileType);
        return (T) ResourceComponentManager.instance().loadComponentModel(path);
    }

    @Override
    public void removeModelCache(String modelName, Long modelVersion) {
        if (modelVersion == null) {
            List<Long> versions = getAllVersions(modelName);
            for (Long version : versions) {
                removeModelCache(modelName, version);
            }
        } else {
            String path = ResourceVersionHelper.buildPath("module:" + basePath, modelName, modelVersion, fileType);
            ResourceComponentManager.instance().removeCachedModel(path);
        }
    }
}
