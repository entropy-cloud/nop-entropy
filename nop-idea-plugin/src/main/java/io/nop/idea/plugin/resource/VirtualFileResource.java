/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.resource;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.impl.AbstractResource;
import io.nop.idea.plugin.utils.ProjectFileHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

public class VirtualFileResource extends AbstractResource {
    private final VirtualFile file;

    public VirtualFileResource(String path, VirtualFile file) {
        super(path);
        this.file = file;
    }

    public VirtualFileResource(VirtualFile file) {
        this(ProjectFileHelper.getFileUrl(file), file);
    }

    @Override
    protected Object internalObj() {
        return file;
    }

    @Override
    public String getExternalPath() {
        return file.getPath();
    }

    @Override
    public long length() {
        return file.getLength();
    }

    @Override
    public long lastModified() {
        return file.getTimeStamp();
    }

    @Override
    public void setLastModified(long time) {

    }

    @Override
    public boolean delete() {
        try {
            file.delete(this);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public File toFile() {
        return VfsUtil.virtualToIoFile(file);
    }

    @Override
    public URL toURL() {
        return ProjectFileHelper.toURL(file);
    }

    @Override
    public boolean isReadOnly() {
        return !file.isWritable();
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public InputStream getInputStream() {
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public OutputStream getOutputStream() {
        try {
            return file.getOutputStream(this);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public boolean exists() {
        return file.exists();
    }
}
