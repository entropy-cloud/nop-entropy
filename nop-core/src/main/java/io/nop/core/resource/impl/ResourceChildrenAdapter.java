/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.impl;

import io.nop.core.model.tree.ITreeChildrenAdapter;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLoader;

import java.util.Collection;

public class ResourceChildrenAdapter implements ITreeChildrenAdapter<IResource> {
    private final IResourceLoader store;

    public ResourceChildrenAdapter(IResourceLoader store) {
        this.store = store;
    }

    @Override
    public Collection<? extends IResource> getChildren(IResource resource) {
        if (resource.getPath().startsWith("file:") && resource instanceof FileResource) {
            return ((FileResource) resource).getChildren();
        }
        return store.getChildren(resource.getPath());
    }
}