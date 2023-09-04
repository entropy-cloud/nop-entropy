/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceConstants;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_UNKNOWN_RESOURCE_NOT_ALLOW_OPERATION;

/**
 * IResourceStore解析resourcePath失败时，将会返回UnknownResource
 *
 * @author canonical_entropy@163.com
 */
public class DynamicResource extends AbstractResource implements IFile {

    private static final long serialVersionUID = 3061171246553624932L;

    private final static AtomicLong s_seq = new AtomicLong();

    public DynamicResource(String path) {
        super(path);
        Guard.checkArgument(path.startsWith(ResourceConstants.DYNAMIC_NS_PREFIX));
    }

    public static String newDynamicPath() {
        return ResourceConstants.DYNAMIC_NS_PREFIX + s_seq.incrementAndGet();
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    protected Object internalObj() {
        return getPath();
    }

    NopException error() {
        return new NopException(ERR_RESOURCE_UNKNOWN_RESOURCE_NOT_ALLOW_OPERATION).param(ARG_RESOURCE_PATH, getPath());
    }

    @Override
    public InputStream getInputStream() {
        throw error();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public OutputStream getOutputStream(boolean append) {
        throw error();
    }

    @Override
    public boolean mkdirs() {
        return false;
    }

    @Override
    public boolean createNewFile() {
        return false;
    }

    @Override
    public IFile createTempFile(String prefix, String postfix) {
        throw error();
    }

    @Override
    public boolean renameTo(IResource resource) {
        return false;
    }

    @Override
    public IFile getResource(String relativeName) {
        return new DynamicResource(getPath() + relativeName);
    }

    @Override
    public List<IFile> getChildren() {
        return null;
    }

    @Override
    public void deleteOnExit() {
    }
}