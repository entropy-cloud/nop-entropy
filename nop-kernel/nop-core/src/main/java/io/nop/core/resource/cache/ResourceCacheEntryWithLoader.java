/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
