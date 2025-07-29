/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.impl;

import io.nop.core.model.tree.ITreeChildrenAdapter;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLoader;
import io.nop.core.resource.VirtualFileSystem;

import java.util.Collection;

public class ResourceChildrenAdapter implements ITreeChildrenAdapter<IResource> {
    private final IResourceLoader store;

    public ResourceChildrenAdapter(IResourceLoader store) {
        this.store = store;
    }

    public ResourceChildrenAdapter() {
        this(VirtualFileSystem.instance());
    }

    @Override
    public Collection<? extends IResource> getChildren(IResource resource) {
        if (resource.getPath().startsWith("file:") && resource instanceof FileResource) {
            return ((FileResource) resource).getChildren();
        }
        return store.getChildren(resource.getPath());
    }
}