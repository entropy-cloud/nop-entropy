/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.store;

import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 先在firstStore中查找，找不到再到secondStore中查找
 */
public class OverrideResourceStore implements IResourceStore {
    private final IResourceStore firstStore;
    private final IResourceStore secondStore;

    public OverrideResourceStore(IResourceStore firstStore, IResourceStore secondStore) {
        this.firstStore = firstStore;
        this.secondStore = secondStore;
    }

    public IResourceStore getFirstStore() {
        return firstStore;
    }

    public IResourceStore getSecondStore() {
        return secondStore;
    }

    @Override
    public IResource getResource(String path, boolean returnNullIfNotExists) {
        IResource resource = firstStore.getResource(path, true);
        if (resource == null)
            resource = secondStore.getResource(path, returnNullIfNotExists);
        return resource;
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        List<? extends IResource> firstChildren = firstStore.getChildren(path);
        List<? extends IResource> secondChildren = secondStore.getChildren(path);
        if (firstChildren == null || firstChildren.isEmpty())
            return secondChildren;

        if (secondChildren == null || secondChildren.isEmpty())
            return firstChildren;

        Map<String, IResource> map = new TreeMap<>();
        firstChildren.forEach(resource -> map.put(resource.getName(), resource));
        secondChildren.forEach(resource -> map.putIfAbsent(resource.getName(), resource));
        return new ArrayList<>(map.values());
    }

    @Override
    public boolean supportSave(String path) {
        return firstStore.supportSave(path) || secondStore.supportSave(path);
    }

    @Override
    public String saveResource(String path, IResource resource, IStepProgressListener listener,
                               Map<String, Object> options) {
        if (firstStore.supportSave(path))
            return firstStore.saveResource(path, resource, listener, options);
        return secondStore.saveResource(path, resource, listener, options);
    }
}