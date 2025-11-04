/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.impl.DynamicResource;

public class DynamicNamespaceHandler implements IResourceNamespaceHandler {
    public static final DynamicNamespaceHandler INSTANCE = new DynamicNamespaceHandler();

    @Override
    public String getNamespace() {
        return ResourceConstants.RESOURCE_NS_DYNAMIC;
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        return new DynamicResource(vPath);
    }
}
