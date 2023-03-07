/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.deps;

import io.nop.api.core.resource.IResourceReference;

public class ResourceChangeCheckResult {
    private final IResourceReference resource;
    private final boolean changed;
    private final long lastModified;

    public ResourceChangeCheckResult(IResourceReference resource, boolean changed, long lastModified) {
        this.resource = resource;
        this.changed = changed;
        this.lastModified = lastModified;
    }

    public IResourceReference getResource() {
        return resource;
    }

    public boolean isChanged() {
        return changed;
    }

    public long getLastModified() {
        return lastModified;
    }
}