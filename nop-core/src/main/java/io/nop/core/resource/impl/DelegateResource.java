/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.impl;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceRegion;

import jakarta.annotation.Nonnull;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

public class DelegateResource extends AbstractResource {
    private static final long serialVersionUID = -5436740054573415588L;

    private final IResource resource;

    public DelegateResource(String path, IResource resource) {
        super(path);
        this.resource = resource;
    }

    public IResource getResource() {
        return resource;
    }

    @Override
    protected Object internalObj() {
        if (resource instanceof AbstractResource)
            return ((AbstractResource) resource).internalObj();
        return resource;
    }

    @Override
    public String getExternalPath() {
        return resource.getExternalPath();
    }

    @Override
    public String toString() {
        return "DelegateResource[path=" + getPath() + ",resource=" + resource + "]";
    }

    @Override
    public long length() {
        return resource.length();
    }

    @Override
    public long lastModified() {
        return resource.lastModified();
    }

    @Override
    public void setLastModified(long time) {
        resource.setLastModified(time);
    }

    @Override
    public boolean delete() {
        return resource.delete();
    }

    @Override
    public File toFile() {
        return resource.toFile();
    }

    @Override
    public URL toURL() {
        return resource.toURL();
    }

    @Override
    public boolean isReadOnly() {
        return resource.isReadOnly();
    }

    @Override
    public void saveToFile(@Nonnull File file) {
        resource.saveToFile(file);
    }

    @Override
    public void saveToResource(IResource resource) {
        this.resource.saveToResource(resource);
    }

    @Override
    public void saveToResource(IResource resource, IStepProgressListener listener) {
        this.resource.saveToResource(resource, listener);
    }

    @Override
    public InputStream getInputStream() {
        return resource.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() {
        return resource.getOutputStream();
    }

    @Override
    public OutputStream getOutputStream(boolean append) {
        return resource.getOutputStream(append);
    }

    @Override
    public void writeToStream(OutputStream os) {
        resource.writeToStream(os);
    }

    @Override
    public void writeToStream(OutputStream os, IStepProgressListener listener) {
        resource.writeToStream(os, listener);
    }

    @Override
    public IResourceRegion getResourceRegion(LongRangeBean range) {
        return resource.getResourceRegion(range);
    }

    @Override
    public boolean exists() {
        return resource.exists();
    }
}