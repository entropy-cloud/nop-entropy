/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.text.StringTrie;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.impl.UnknownResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_DIR_NOT_SUPPORT_STREAM;
import static io.nop.core.CoreErrors.ERR_RESOURCE_STORE_PATH_NOT_SUPPORTED;

/**
 * 多个store组合成一个统一的ResourceStore。不同的虚拟目录前缀对应不同的store，多个store负责的部分不重叠。
 */
public class CompositeResourceStore implements IResourceStore {
    private final StringTrie<IResourceStore> storeTrie = new StringTrie<>();
    private final IResourceStore defaultStore;

    public CompositeResourceStore(IResourceStore defaultStore) {
        this.defaultStore = defaultStore;
    }

    public void addStore(String prefix, IResourceStore store) {
        storeTrie.add(prefix, store);
    }

    IResourceStore getStore(String path) {
        return storeTrie.findWithPrefix(path);
    }

    @Override
    public IResource getResource(String path, boolean returnNullIfNotExists) {
        IResourceStore store = getStore(path);
        if (store == null) {
            if (defaultStore != null)
                return defaultStore.getResource(path, returnNullIfNotExists);

            if (returnNullIfNotExists)
                return null;
            return new UnknownResource(path);
        }
        if (defaultStore != null) {
            IResource resource = store.getResource(path, true);
            if (resource != null)
                return resource;
            return defaultStore.getResource(path, returnNullIfNotExists);
        }
        return store.getResource(path, returnNullIfNotExists);
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        IResourceStore store = getStore(path);
        if (store == null) {
            if (defaultStore != null)
                return defaultStore.getChildren(path);
            return null;
        }
        if (defaultStore == null) {
            return store.getChildren(path);
        } else {
            Map<String, IResource> map = new TreeMap<>();
            List<? extends IResource> list = store.getChildren(path);
            if (list != null) {
                for (IResource resource : list) {
                    map.put(resource.getName(), resource);
                }
            }
            list = defaultStore.getChildren(path);
            if (list != null) {
                for (IResource resource : list) {
                    map.putIfAbsent(resource.getName(), resource);
                }
            }
            return new ArrayList<>(map.values());
        }
    }

    @Override
    public boolean supportSave(String path) {
        IResourceStore store = getStore(path);
        if (store == null) {
            store = defaultStore;
        }
        if (store == null)
            return false;
        return store.supportSave(path);
    }

    @Override
    public String saveResource(String path, IResource resource, IStepProgressListener listener,
                               Map<String, Object> options) {
        if (resource.isDirectory())
            throw new NopException(ERR_RESOURCE_DIR_NOT_SUPPORT_STREAM).param(ARG_RESOURCE, resource);

        IResourceStore store = getStore(path);
        if (store == null) {
            if (defaultStore != null) {
                store = defaultStore;
            } else {
                throw new NopException(ERR_RESOURCE_STORE_PATH_NOT_SUPPORTED).param(ARG_RESOURCE_PATH, path);
            }
        }
        String fullPath = StringHelper.appendPath(path, resource.getName());
        IResource targetResource = store.getResource(fullPath);
        resource.saveToResource(targetResource, listener);
        return fullPath;
    }
}