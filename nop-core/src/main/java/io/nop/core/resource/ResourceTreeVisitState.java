/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource;

import io.nop.commons.util.StringHelper;
import io.nop.core.model.tree.ITreeChildrenAdapter;
import io.nop.core.model.tree.TreeVisitState;
import io.nop.core.resource.impl.ResourceChildrenAdapter;

import java.util.List;

public class ResourceTreeVisitState extends TreeVisitState<IResource> {
    public ResourceTreeVisitState(IResource root, ITreeChildrenAdapter<IResource> adapter) {
        super(root, adapter);
    }

    public ResourceTreeVisitState(IResource root) {
        super(root, new ResourceChildrenAdapter(VirtualFileSystem.instance()));
    }

    public String getParentTreePath() {
        List<IResource> parents = getParents();
        if (parents.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        for (IResource parent : parents) {
            sb.append(parent.getName()).append('/');
        }
        return sb.toString();
    }

    public String getCurrentTreePath() {
        List<IResource> parents = getParents();
        if (parents.isEmpty())
            return getCurrent().getName();

        StringBuilder sb = new StringBuilder();
        for (IResource parent : parents) {
            sb.append(parent.getName()).append('/');
        }
        sb.append(getCurrent().getName());
        return sb.toString();
    }

    public String buildFullPath(String basePath) {
        List<IResource> parents = getParents();
        if (parents.isEmpty())
            return StringHelper.appendPath(basePath, getCurrent().getName());

        StringBuilder sb = new StringBuilder();
        sb.append(basePath);
        if (!basePath.endsWith("/"))
            sb.append("/");

        for (IResource parent : parents) {
            sb.append(parent.getName()).append('/');
        }
        sb.append(getCurrent().getName());
        return sb.toString();
    }
}