/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.file.local;

import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.impl.AbstractResource;
import io.nop.api.core.beans.file.FileStatusBean;
import io.nop.integration.api.file.IFileServiceClient;
import io.nop.integration.api.file.IFileServiceClientFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileServiceResource extends AbstractResource {
    private final IFileServiceClientFactory factory;
    private FileStatusBean status;
    private String remotePath;

    public FileServiceResource(String path, String remotePath,
                               IFileServiceClientFactory factory, FileStatusBean status) {
        super(path);
        this.factory = factory;
        this.status = status;
        this.remotePath = remotePath;
    }

    private synchronized FileStatusBean getFileStatus() {
        if (status == null) {
            synchronized (this) {
                IFileServiceClient client = factory.newClient();
                try {
                    status = client.getFileStatus(remotePath);
                    if (status == null) {
                        status = new FileStatusBean();
                        status.setName(getName());
                        status.setLastModified(-1);
                        status.setSize(0);
                    }
                } finally {
                    IoHelper.safeCloseObject(client);
                }
            }
        }
        return status;
    }

    @Override
    public InputStream getInputStream() {
        IFileServiceClient client = factory.newClient();
        boolean delayClose = false;
        try {
            InputStream is = client.getInputStream(remotePath);
            delayClose = true;
            return new FilterInputStream(is) {
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        IoHelper.safeClose(client);
                    }
                }
            };
        } finally {
            if (!delayClose)
                IoHelper.safeClose(client);
        }
    }

    @Override
    public void writeToStream(OutputStream os, IStepProgressListener listener) {
        IFileServiceClient client = factory.newClient();
        try {
            client.downloadToStream(remotePath, os);
        } finally {
            IoHelper.safeClose(client);
        }
    }


    @Override
    public boolean delete() {
        IFileServiceClient client = factory.newClient();
        try {
            client.deleteFile(remotePath);
            return true;
        } finally {
            IoHelper.safeClose(client);
        }
    }

    @Override
    public long length() {
        return getFileStatus().getSize();
    }

    @Override
    public long lastModified() {
        return getFileStatus().getLastModified();
    }

    @Override
    public boolean exists() {
        return getFileStatus().getLastModified() > 0;
    }

    @Override
    protected Object internalObj() {
        return this;
    }
}
