package io.nop.core.resource.impl;

import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLocator;
import io.nop.core.resource.IResourceStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 简单的资源存储实现，只支持按照路径获取单个资源，
 * 不支持子目录列表等操作
 */
public class SimpleResourceStore implements IResourceStore {

    private final IResourceLocator resourceLoader;

    public SimpleResourceStore(IResourceLocator resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public IResource getResource(String path, boolean returnNullIfNotExists) {
        IResource resource = resourceLoader.getResource(path);
        if (resource != null)
            return resource;
        if (returnNullIfNotExists)
            return null;
        return new UnknownResource(path);
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        // 不支持子目录列表操作，返回空列表
        return Collections.emptyList();
    }

    @Override
    public boolean supportSave(String path) {
        // 不支持保存操作
        return false;
    }

    @Override
    public String saveResource(String path, IResource resource, IStepProgressListener listener, Map<String, Object> options) {
        // 不支持保存操作，抛出异常
        throw new UnsupportedOperationException("saveResource operation is not supported");
    }
}