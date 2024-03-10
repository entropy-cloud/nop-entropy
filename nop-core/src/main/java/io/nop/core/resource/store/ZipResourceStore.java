/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.scan.FileScanHelper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public class ZipResourceStore implements IResourceStore, AutoCloseable {
    private final ZipFile zipFile;
    private final InMemoryResourceStore store = new InMemoryResourceStore();

    /**
     * 收集zip文件中baseEntryPath目录下的所有文件，将其包装为虚拟路径basePath下的资源对象
     *
     * @param zipFile       压缩文件
     * @param baseEntryPath 需要扫描的压缩文件的子目录
     * @param basePath      返回的资源对象的虚拟路径前缀
     */
    public ZipResourceStore(ZipFile zipFile, String baseEntryPath, String basePath) {
        this.zipFile = zipFile;
        FileScanHelper.scanZip(zipFile, baseEntryPath, basePath, store);
    }

    public static ZipResourceStore build(File file, String basePath, String baseEntryPath) {
        try {
            return new ZipResourceStore(new ZipFile(file), baseEntryPath, basePath);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public IResource getResource(String path, boolean returnNullIfNotExists) {
        return store.getResource(path, returnNullIfNotExists);
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        return store.getChildren(path);
    }

    @Override
    public boolean supportSave(String path) {
        return false;
    }

    @Override
    public String saveResource(String path, IResource resource, IStepProgressListener listener,
                               Map<String, Object> options) {
        throw new UnsupportedOperationException("ZipResourceStore not support write");
    }

    @Override
    public void close() throws Exception {
        zipFile.close();
    }
}