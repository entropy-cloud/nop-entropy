/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.impl;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.URLHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceRegion;
import io.nop.core.resource.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Objects;

import static io.nop.commons.CommonConfigs.CFG_IO_DEFAULT_BUF_SIZE;
import static io.nop.core.CoreErrors.ARG_DEST;
import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ARG_SRC;
import static io.nop.core.CoreErrors.ERR_RESOURCE_DIR_NOT_SUPPORT_STREAM;
import static io.nop.core.CoreErrors.ERR_RESOURCE_INVALID_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_IS_READONLY_FILE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_EXISTS;
import static io.nop.core.CoreErrors.ERR_RESOURCE_SAVE_TO_RESOURCE_FAIL;
import static io.nop.core.CoreErrors.ERR_RESOURCE_WRITE_TO_STREAM_FAIL;

public abstract class AbstractResource implements IResource {
    private static final long serialVersionUID = -2602160662193754475L;
    static final Logger LOG = LoggerFactory.getLogger(AbstractResource.class);

    private final String path;

    public AbstractResource(String path) {
        if (!path.startsWith("/") && path.indexOf(':') < 0)
            throw new NopException(ERR_RESOURCE_INVALID_PATH).param(ARG_RESOURCE_PATH, path);
        if (path.endsWith("/") && !path.equals("/") && !path.endsWith(":/"))
            throw new NopException(ERR_RESOURCE_INVALID_PATH).param(ARG_RESOURCE_PATH, path);
        this.path = path;
    }

    public int hashCode() {
        return path.hashCode();
    }

    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (this == o)
            return true;

        if (!(o instanceof AbstractResource))
            return false;

        AbstractResource resource = (AbstractResource) o;

        if (!path.equals(resource.getPath()))
            return false;

        if (!Objects.equals(internalObj(), resource.internalObj()))
            return false;

        return true;
    }

    protected abstract Object internalObj();

    public String toString() {
        return this.getClass().getSimpleName() + "[" + path + "]";
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public final String getStdPath() {
        return ResourceHelper.getStdPath(path);
    }

    @Override
    public final String getName() {
        return ResourceHelper.getName(path);
    }

    @Override
    public String getExternalPath() {
        URL url = toURL();
        return URLHelper.getCanonicalUrl(url);
    }

    @Override
    public long length() {
        return -1;
    }

    @Override
    public long lastModified() {
        return -1;
    }

    @Override
    public void setLastModified(long time) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean delete() {
        throw new UnsupportedOperationException();
    }

    @Override
    public File toFile() {
        return null;
    }

    @Override
    public URL toURL() {
        return null;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return getPath().endsWith("/");
    }

    protected void checkNotReadonly() {
        if (isReadOnly()) {
            throw new NopException(ERR_RESOURCE_IS_READONLY_FILE).param(ARG_RESOURCE, this);
        }
    }

    protected void checkSupportStream() {
        if (isDirectory())
            throw new NopException(ERR_RESOURCE_DIR_NOT_SUPPORT_STREAM).param(ARG_RESOURCE, this);
    }

    @Override
    public void saveToFile(@Nonnull File file) {
        saveToResource(new FileResource(file));
    }

    @Override
    public void saveToResource(IResource resource) {
        saveToResource(resource, null);
    }

    @Override
    public void saveToResource(IResource resource, IStepProgressListener listener) {
        checkSupportStream();

        if (!this.exists())
            throw new NopException(ERR_RESOURCE_NOT_EXISTS).param(ARG_RESOURCE, this);

        if (this.equals(resource)) {
            LOG.debug("nop.resource.ignore-save-to-same-resource:resource={}", resource);
            return;
        }

        File file = resource.toFile();
        if (file != null) {
            File srcFile = this.toFile();
            if (srcFile != null) {
                // 如果两者都是文件，可以通过操作系统的优化机制实现拷贝
                if (listener != null)
                    listener.begin();

                FileHelper.copyFile(srcFile, file);
                if (listener != null) {
                    listener.onStep(srcFile.length());
                    listener.end();
                }
                return;
            }
        }

        OutputStream os = null;
        try {
            os = resource.getOutputStream();
            this.writeToStream(os, listener);
            os.flush();
        } catch (Exception e) {
            throw new NopException(ERR_RESOURCE_SAVE_TO_RESOURCE_FAIL, e).param(ARG_SRC, this.getPath()).param(ARG_DEST,
                    resource.getPath());
        } finally {
            IoHelper.safeClose(os);
        }
    }

    @Override
    public InputStream getInputStream() {
        checkSupportStream();
        throw new UnsupportedOperationException("getInputStream");
    }

    @Override
    public OutputStream getOutputStream() {
        checkSupportStream();
        throw new UnsupportedOperationException("getOutputStream");
    }

    @Override
    public OutputStream getOutputStream(boolean append) {
        if (append)
            throw new UnsupportedOperationException("append");
        return this.getOutputStream();
    }

    @Override
    public void writeToStream(OutputStream os, IStepProgressListener listener) {
        checkSupportStream();

        InputStream is = null;
        try {
            is = getInputStream();
            IoHelper.copy(is, os, CFG_IO_DEFAULT_BUF_SIZE.get(), listener);
        } catch (IOException e) {
            throw new NopException(ERR_RESOURCE_WRITE_TO_STREAM_FAIL, e).param(ARG_SRC, getPath());
        } finally {
            IoHelper.safeClose(is);
        }
    }

    @Override
    public IResourceRegion getResourceRegion(LongRangeBean range) {
        checkSupportStream();

        return new DefaultResourceRegion(this, range);
    }
}