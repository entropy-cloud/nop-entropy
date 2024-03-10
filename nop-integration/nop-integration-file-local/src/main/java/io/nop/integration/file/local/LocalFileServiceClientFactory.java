/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.file.local;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.store.LocalResourceStore;
import io.nop.integration.api.file.FileStatus;
import io.nop.integration.api.file.IFileServiceClient;
import io.nop.integration.api.file.IFileServiceClientFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 将IResourceStore接口封装为IFileServiceClient
 */
public class LocalFileServiceClientFactory implements IFileServiceClientFactory, IFileServiceClient {
    private IResourceStore resourceStore;

    public void setResourceStore(IResourceStore resourceStore) {
        this.resourceStore = resourceStore;
    }

    public void setLocalDir(File localDir) {
        this.resourceStore = new LocalResourceStore("/", localDir);
    }

    @Override
    public List<FileStatus> listFiles(String remotePath) {
        List<? extends IResource> resources = resourceStore.getChildren(remotePath);
        return resources.stream().map(this::newFileStatus).collect(Collectors.toList());
    }

    public FileStatus getFileStatus(String remotePath) {
        IResource resource = resourceStore.getResource(remotePath);
        return newFileStatus(resource);
    }

    private FileStatus newFileStatus(IResource resource) {
        FileStatus status = new FileStatus();
        status.setName(resource.getName());
        status.setLastModified(resource.lastModified());
        status.setSize(resource.length());
        return status;
    }

    @Override
    public boolean deleteFile(String remotePath) {
        return resourceStore.getResource(remotePath).delete();
    }

    @Override
    public String uploadFile(String localPath, String remotePath) {
        return uploadResource(new FileResource(remotePath, new File(localPath)), remotePath);
    }

    @Override
    public String downloadFile(String remotePath, String localPath) {
        resourceStore.getResource(remotePath).saveToFile(new File(localPath));
        return localPath;
    }

    @Override
    public String uploadResource(IResourceReference file, String remotePath) {
        IResource resource = resourceStore.getResource(remotePath);
        if (file instanceof IResource) {
            ((IResource) file).saveToResource(resource);
        } else {
            InputStream is = null;
            OutputStream os = null;
            try {
                is = file.getInputStream();
                os = resource.getOutputStream();
                IoHelper.copy(is, os);
            } catch (IOException e) {
                throw NopException.adapt(e);
            } finally {
                IoHelper.safeCloseObject(is);
                IoHelper.safeCloseObject(os);
            }
        }
        return remotePath;
    }

    @Override
    public void downloadToStream(String remotePath, OutputStream out) {
        resourceStore.getResource(remotePath).writeToStream(out);
    }

    @Override
    public InputStream getInputStream(String remotePath) {
        return resourceStore.getResource(remotePath).getInputStream();
    }

    @Override
    public IFileServiceClient newClient() {
        return this;
    }

    @Override
    public void close() {

    }
}
