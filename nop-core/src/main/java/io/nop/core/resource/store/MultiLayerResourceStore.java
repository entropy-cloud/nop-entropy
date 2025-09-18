/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.impl.UnknownResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 多层资源存储，按照优先级从高到低依次查找资源
 */
public class MultiLayerResourceStore implements IResourceStore {
    private final List<IResourceStore> stores;

    /**
     * 使用指定的资源存储列表创建多层资源存储
     *
     * @param stores 资源存储列表，按优先级从高到低排列
     */
    public MultiLayerResourceStore(List<IResourceStore> stores) {
        if (stores == null || stores.isEmpty()) {
            throw new IllegalArgumentException("stores cannot be null or empty");
        }
        this.stores = new ArrayList<>(stores);
    }

    public MultiLayerResourceStore() {
        this(new ArrayList<>());
    }

    public List<IResourceStore> getStores() {
        return stores;
    }

    public void appendLayer(IResourceStore store) {
        this.stores.add(store);
    }

    public void prependLayer(IResourceStore store) {
        this.stores.add(0, store);
    }

    public IResourceStore getTopLayer() {
        return stores.get(0);
    }

    @Override
    public IResource getResource(String path, boolean returnNullIfNotExists) {
        for (IResourceStore store : stores) {
            IResource resource = store.getResource(path, true);
            if (resource != null) {
                return resource;
            }
        }

        if (returnNullIfNotExists) {
            return null;
        }

        return new UnknownResource(path);
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        Map<String, IResource> map = new TreeMap<>();

        // 从优先级最低的开始查找，这样高优先级的会覆盖低优先级的
        for (int i = stores.size() - 1; i >= 0; i--) {
            IResourceStore store = stores.get(i);
            List<? extends IResource> children = store.getChildren(path);
            if (children != null) {
                for (IResource child : children) {
                    map.put(child.getName(), child);
                }
            }
        }

        return new ArrayList<>(map.values());
    }

    @Override
    public boolean supportSave(String path) {
        for (IResourceStore store : stores) {
            if (store.supportSave(path)) {
                return true;
            }

            if (store.getResource(path, true) != null)
                return false;
        }
        return false;
    }

    @Override
    public String saveResource(String path, IResource resource, IStepProgressListener listener,
                               Map<String, Object> options) {
        // 找到第一个支持保存的存储进行保存
        for (IResourceStore store : stores) {
            if (store.supportSave(path)) {
                return store.saveResource(path, resource, listener, options);
            }

            if (store.getResource(path, true) != null)
                throw new UnsupportedOperationException("store not support saving for path:" + path);
        }

        throw new UnsupportedOperationException("No store supports saving for path: " + path);
    }
}