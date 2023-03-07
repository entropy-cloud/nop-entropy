/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.zip;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.progress.IProgressListener;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.nop.commons.CommonConfigs.CFG_IO_DEFAULT_BUF_SIZE;
import static io.nop.core.CoreErrors.ARG_NAME;
import static io.nop.core.CoreErrors.ERR_RESOURCE_INVALID_DIR_ZIP_ENTRY_NAME;
import static io.nop.core.CoreErrors.ERR_RESOURCE_INVALID_ZIP_ENTRY_NAME;

public class JdkZipOutput implements IZipOutput {
    private final ZipOutputStream os;
    private final IProgressListener listener;
    private final boolean jarFile;
    private boolean firstEntry = true;

    public JdkZipOutput(ZipOutputStream os, boolean jarFile, IProgressListener listener) {
        this.os = os;
        this.listener = listener;
        this.jarFile = jarFile;
    }

    @Override
    public void close() throws IOException {
        os.close();
    }

    IStepProgressListener getStepListener(String reason, String path, long size) {
        if (listener == null)
            return null;
        return listener.toStepListener(reason + ':' + path, size);
    }

    @Override
    public void addResource(ZipEntry entry, IResource resource) throws IOException {
        if (entry.getTime() == -1L) {
            entry.setTime(resource.lastModified());
        }

        long len = resource.length();
        if (len >= 0)
            entry.setSize(len);

        if (resource.isDirectory()) {
            if (!entry.getName().endsWith("/"))
                throw new NopException(ERR_RESOURCE_INVALID_DIR_ZIP_ENTRY_NAME).param(ARG_NAME, entry.getName());
            this.addEntry(entry);
        } else {
            InputStream in = resource.getInputStream();
            try {
                addStream(entry, in);
            } finally {
                IoHelper.safeClose(in);
            }
        }
    }

    @Override
    public void addData(ZipEntry entry, byte[] data) throws IOException {
        os.putNextEntry(entry);
        if (data != null) {
            IoHelper.write(os, data, getStepListener("zip", entry.getName(), data.length));
        }
        firstEntry = false;
    }

    @Override
    public OutputStream addEntry(ZipEntry entry) throws IOException {
        /*
         * if (!entry.getName().endsWith("/")) throw new
         * EntropyException("resource.err_dir_entry_name_not_endsWith_slash").param("entryName", entry.getName());
         */
        os.putNextEntry(entry);
        firstEntry = false;
        return os;
    }

    @Override
    public ZipEntry newZipEntry(String entryName) {
        if (!StringHelper.isValidFilePath(entryName) || entryName.startsWith("/"))
            throw new NopException(ERR_RESOURCE_INVALID_ZIP_ENTRY_NAME).param(ARG_NAME, entryName);

        ZipEntry entry = new ZipEntry(entryName);
        return entry;
    }

    @Override
    public void addStream(ZipEntry entry, InputStream is) throws IOException {
        if (jarFile && !firstEntry && entry.getName().equals(JarFile.MANIFEST_NAME)) {
            return;
        }

        os.putNextEntry(entry);
        IoHelper.copy(is, os, CFG_IO_DEFAULT_BUF_SIZE.get(), getStepListener("zip", entry.getName(), entry.getSize()));
        firstEntry = false;
    }

    @Override
    public void addDir(String basePath, IFile file, boolean onlyChild, Predicate<IFile> filter) throws IOException {
        if (basePath == null)
            basePath = "";

        if (firstEntry && jarFile) {
            // jar文件的第一个entry必须是manifest
            if (basePath.length() == 0) {
                IFile manifest = file.getResource(JarFile.MANIFEST_NAME);
                if (manifest.exists()) {
                    addResource(newZipEntry(JarFile.MANIFEST_NAME), manifest);
                }
            }
        }
        IZipOutput.super.addDir(basePath, file, onlyChild, filter);
    }
}