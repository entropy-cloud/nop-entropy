/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.impl;

import io.nop.api.core.exceptions.NopException;

import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_DIR_PATH_SHOULD_END_WITH_SLASH;

/**
 * 在内存中起占位作用的目录节点
 */
public class InMemoryDirResource extends AbstractResource {
    private static final long serialVersionUID = 8844233581913521419L;

    private boolean readonly;
    private long lastModified = -1;

    public InMemoryDirResource(String path, boolean readonly) {
        super(path);
        this.readonly = readonly;
        if (!isDirectory())
            throw new NopException(ERR_RESOURCE_DIR_PATH_SHOULD_END_WITH_SLASH).param(ARG_RESOURCE_PATH, path);
    }

    public InMemoryDirResource(String path) {
        this(path, false);
    }

    @Override
    protected Object internalObj() {
        return null;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return readonly;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public long lastModified() {
        return lastModified;
    }

    @Override
    public void setLastModified(long time) {
        this.lastModified = time;
    }
}