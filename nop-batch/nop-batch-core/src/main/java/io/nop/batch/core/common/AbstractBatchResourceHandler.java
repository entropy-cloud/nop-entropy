/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.common;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLoader;

public class AbstractBatchResourceHandler extends AbstractBatchHandler {
    private IResourceLoader resourceLoader;
    private String resourcePath;

    private IEvalAction pathExpr;
    private IResource resource;

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

    public void setResource(IResource resource) {
        this.resource = resource;
    }

    public void setPathExpr(IEvalAction pathExpr) {
        this.pathExpr = pathExpr;
    }

    public IResource getResource(IBatchTaskContext context) {
        if (resource != null)
            return resource;

        String resourcePath = this.resourcePath;
        if (pathExpr != null) {
            resourcePath = ConvertHelper.toString(pathExpr.invoke(context));
        }

        return resourceLoader.getResource(resourcePath);
    }

    public void onTaskEnd(Throwable exception, IBatchTaskContext context) {
    }
}