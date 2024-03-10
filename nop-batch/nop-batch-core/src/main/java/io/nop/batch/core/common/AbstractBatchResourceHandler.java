/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.common;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLoader;

public class AbstractBatchResourceHandler extends AbstractBatchHandler {
    private IResourceLoader resourceLoader;
    private String resourcePath;

    public IResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    public void setResourceLoader(IResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public IResource getResource() {
        return resourceLoader.getResource(resourcePath);
    }
}
