/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_USE_CP_CACHE;
import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_PARSE_MISSING_RESOURCE;

public abstract class AbstractResourceParser<T> implements IResourceParser<T> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractResourceParser.class);

    private String resourcePath;
    private boolean traceDepends = true;

    public IResourceParser<T> shouldTraceDepends(boolean value) {
        this.traceDepends = value;
        return this;
    }

    public boolean shouldTraceDepends() {
        return traceDepends;
    }

    protected boolean isUseCpCache() {
        return CFG_COMPONENT_USE_CP_CACHE.get();
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getResourceStdPath() {
        return ResourceHelper.getStdPath(resourcePath);
    }

    protected void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    protected IResource findResource(String path) {
        return VirtualFileSystem.instance().getResource(path);
    }

    public String getResourceName() {
        return StringHelper.fileFullName(this.resourcePath);
    }

    public String getResourceNameNoExt() {
        return StringHelper.fileNameNoExt(this.resourcePath);
    }

    public IResource getSiblingResource(String relativeName) {
        return ResourceHelper.getSibling(findResource(resourcePath), relativeName);
    }

    @Override
    public final T parseFromResource(final IResource resource, boolean ignoreUnknown) {
        this.resourcePath = resource.getPath();

        if (!resource.exists()) {
            // 尝试读取编译缓存对象，如果存在则直接返回
            if (isUseCpCache()) {
                T compiled = ResourceComponentManager.instance().loadPrecompiledObject(resourcePath);
                if (compiled != null)
                    return compiled;
            }

            if (ignoreUnknown)
                return null;
            throw new NopException(ERR_COMPONENT_PARSE_MISSING_RESOURCE).param(ARG_RESOURCE_PATH, resource.getPath())
                    .param(ARG_RESOURCE, resource);
        }

        LOG.debug("nop.core.component.begin-parse-resource:resourcePath={},parser={}", resourcePath, getClass());

        long beginTime = CoreMetrics.nanoTime();
        try {
            if (shouldTraceDepends()) {
                T ret = ResourceComponentManager.instance().collectDepends(resourcePath,
                        () -> doParseResource(resource));
                return ret;
            } else {
                return doParseResource(resource);
            }
        } catch (NopException e) {
            e.addXplStack(getClass().getSimpleName() + ".parseFromResource(" + resourcePath + ")");
            throw e;
        } finally {
            long diff = CoreMetrics.nanoTimeDiff(beginTime);

            LOG.info("nop.core.component.finish-parse-resource:usedTime={},path={},parser={}",
                    CoreMetrics.nanoToMillis(diff), resourcePath, getClass());
        }
    }

    protected abstract T doParseResource(IResource resource);
}