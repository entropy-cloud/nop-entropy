/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import jakarta.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_CREATE_NEW_FILE_FAIL;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_DIR;
import static io.nop.core.CoreErrors.ERR_RESOURCE_OPEN_INPUT_STREAM_FAIL;
import static io.nop.core.CoreErrors.ERR_RESOURCE_OPEN_OUTPUT_STREAM_FAIL;

public class FileResource extends AbstractFile implements IFile {

    private static final long serialVersionUID = 5074607141058118712L;

    private final File file;

    public FileResource(String path, File file) {
        super(path);
        this.file = file;
    }

    public FileResource(File file) {
        this(buildPath(file), file);
    }

    static String buildPath(File file) {
        String absPath = ResourceHelper.normalizePath(file.getAbsolutePath());
        return "file:" + (absPath.startsWith("/") ? "" : "/") + absPath;
    }

    public String toString() {
        return "FileResource[" + getPath() + ",file=" + file + "]";
    }

    @Override
    protected Object internalObj() {
        return file;
    }

    @Override
    public String getExternalPath() {
        return FileHelper.getFileUrl(file);
    }

    public String getCanonicalPath() {
        return FileHelper.getCanonicalPath(file);
    }

    @Override
    public long length() {
        return file.length();
    }

    @Override
    public long lastModified() {
        return file.lastModified();
    }

    @Override
    public void setLastModified(long time) {
        file.setLastModified(time);
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public boolean delete() {
        return file.delete();
    }

    @Override
    public void deleteOnExit() {
        file.deleteOnExit();
    }

    @Override
    public InputStream getInputStream() {
        try {
            return new FileInputStream(file);
        } catch (IOException e) {
            throw new NopException(ERR_RESOURCE_OPEN_INPUT_STREAM_FAIL, e).param(ARG_RESOURCE, this);
        }
    }

    @Override
    public OutputStream getOutputStream() {
        FileHelper.assureParent(file);
        try {
            return new FileOutputStream(file);
        } catch (IOException e) {
            throw new NopException(ERR_RESOURCE_OPEN_OUTPUT_STREAM_FAIL, e).param(ARG_RESOURCE, this);
        }
    }

    @Override
    public File toFile() {
        return file;
    }

    @Override
    public URL toURL() {
        try {
            return file.toURI().toURL();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void saveToFile(@Nonnull File file) {
        FileHelper.copyFile(this.file, file);
    }

    @Override
    protected IFile doGetRelative(String relativeName) {
        if (relativeName.equals("/"))
            return this;
        if (relativeName.endsWith("/")) {
            relativeName = relativeName.substring(0, relativeName.length() - 1);
        }

        File subFile = new File(file, relativeName);
        String subPath = StringHelper.appendPath(getPath(), relativeName);
        return new FileResource(subPath, subFile);
    }

    @Override
    public boolean createNewFile() {
        try {
            return file.createNewFile();
        } catch (IOException e) {
            throw new NopException(ERR_RESOURCE_CREATE_NEW_FILE_FAIL, e).param(ARG_RESOURCE, this);
        }
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public boolean isReadOnly() {
        return file.exists() && !file.canWrite();
    }

    @Override
    public OutputStream getOutputStream(boolean append) {
        FileHelper.assureParent(file);
        try {
            return new FileOutputStream(file, append);
        } catch (IOException e) {
            throw new NopException(ERR_RESOURCE_OPEN_OUTPUT_STREAM_FAIL, e).param(ARG_RESOURCE, this);
        }
    }

    @Override
    public boolean mkdirs() {
        if (!isDirectory())
            throw new NopException(ERR_RESOURCE_NOT_DIR).param(ARG_RESOURCE, this);
        return file.mkdirs();
    }

    @Override
    public boolean renameTo(IResource resource) {
        File dest = resource.toFile();
        if (dest == null)
            return false;
        boolean b = FileHelper.moveFile(file, dest);
        return b;
    }

    @Override
    public List<IFile> getChildren() {
        String[] names = file.list();
        if (names == null)
            return null;

        Arrays.sort(names);

        List<IFile> files = new ArrayList<>(names.length);
        for (String name : names) {
            IFile file = getResource(name);
            files.add(file);
        }

        return files;
    }
}