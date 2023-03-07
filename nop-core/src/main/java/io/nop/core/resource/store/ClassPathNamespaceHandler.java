/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.store;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceNamespaceHandler;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.impl.ClassPathResource;

public class ClassPathNamespaceHandler implements IResourceNamespaceHandler {
    public static final ClassPathNamespaceHandler INSTANCE = new ClassPathNamespaceHandler();

    @Override
    public String getNamespace() {
        return ResourceConstants.CLASSPATH_NS;
    }

    @Override
    public IResource getResource(String vPath, IResourceStore locator) {
        return new ClassPathResource(vPath);
    }
}
