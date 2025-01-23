/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_GET_INPUT_STREAM_FAIL;

public class ZipEntryResource extends AbstractResource {
    private static final long serialVersionUID = 2209589191705603692L;
    private final ZipFile zipFile;
    private final ZipEntry entry;

    public ZipEntryResource(String path, ZipFile zipFile, ZipEntry entry) {
        super(path);
        this.zipFile = zipFile;
        this.entry = entry;
    }

    public URL toURL() {
        try {
            return new URL(getExternalPath());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    protected Object internalObj() {
        return entry;
    }

    @Override
    public String getExternalPath() {
        String protocol = "jar";
        String url = protocol + "://" + FileHelper.getCanonicalPath(new File(zipFile.getName())) + "!/"
                + entry.getName();
        return url;
    }

    @Override
    public boolean exists() {
        return true;
    }

    public ZipFile getZipFile() {
        return zipFile;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (this == o)
            return true;
        if (this.getClass() != o.getClass())
            return false;

        ZipEntryResource res = (ZipEntryResource) o;
        if (!getPath().equals(res.getPath()))
            return false;

        return res.getZipFile().equals(this.zipFile);
    }

    public int hashCode() {
        int h = getPath().hashCode();
        h = h * 37 + zipFile.hashCode();
        return h;
    }

    @Override
    public long length() {
        return entry.getSize(); //NOSONAR
    }

    @Override
    public long lastModified() {
        return entry.getTime();
    }

    @Override
    public InputStream getInputStream() {
        try {
            return zipFile.getInputStream(entry);
        } catch (IOException e) {
            throw new NopException(ERR_RESOURCE_GET_INPUT_STREAM_FAIL, e).param(ARG_RESOURCE, this);
        }
    }
}