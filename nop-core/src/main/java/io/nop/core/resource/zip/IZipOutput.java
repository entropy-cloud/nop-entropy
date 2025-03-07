/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.zip;

import io.nop.api.core.time.CoreMetrics;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;

public interface IZipOutput extends Closeable {

    ZipEntry newZipEntry(String entryName);

    void flush() throws IOException;

    OutputStream addEntry(ZipEntry entry) throws IOException;

    void addResource(ZipEntry entry, IResource resource) throws IOException;

    default void addResourceEntry(String entryName, IResource resource) throws IOException {
        ZipEntry zipEntry = newZipEntry(entryName);
        zipEntry.setSize(resource.length());
        zipEntry.setTime(resource.lastModified());
        addResource(newZipEntry(entryName), resource);
    }

    default void addFileEntry(String entryName, File file) throws IOException {
        addResourceEntry(entryName, new FileResource(file));
    }

    default void addDataEntry(String entryName, byte[] data) throws IOException {
        ZipEntry entry = newZipEntry(entryName);
        entry.setTime(CoreMetrics.currentTimeMillis());
        entry.setSize(data.length);
        addData(entry, data);
    }

    default void addStreamEntry(String entryName, InputStream is) throws IOException {
        ZipEntry entry = newZipEntry(entryName);
        entry.setTime(CoreMetrics.currentTimeMillis());
        addStream(entry, is);
    }

    void addData(ZipEntry entry, byte[] data) throws IOException;

    void addStream(ZipEntry entry, InputStream is) throws IOException;

    default void addDir(String basePath, IFile file) throws IOException {
        addDir(basePath, file, true, null);
    }

    default void addDir(String basePath, IFile file, boolean onlyChild, Predicate<IFile> filter) throws IOException {
        ZipToolHelper.defaultAddDir(this, basePath, file, onlyChild, filter);
    }
}