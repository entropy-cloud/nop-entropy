package io.nop.core.resource.cache;

import io.nop.core.resource.IResourceObjectLoader;

public class ResourceCacheEntryWithLoader<T> extends ResourceCacheEntry<T> {
    private final IResourceObjectLoader<T> loader;

    public ResourceCacheEntryWithLoader(String path, IResourceObjectLoader<T> loader) {
        super(path);
        this.loader = loader;
    }

    public T getObject(boolean checkChanged) {
        return getObject(checkChanged, loader);
    }
}
