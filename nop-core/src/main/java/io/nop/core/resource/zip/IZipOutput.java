/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.zip;

import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;

import java.io.Closeable;
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

    void addData(ZipEntry entry, byte[] data) throws IOException;

    void addStream(ZipEntry entry, InputStream is) throws IOException;

    default void addDir(String basePath, IFile file) throws IOException {
        addDir(basePath, file, true, null);
    }

    default void addDir(String basePath, IFile file, boolean onlyChild, Predicate<IFile> filter) throws IOException {
        ZipToolHelper.defaultAddDir(this, basePath, file, onlyChild, filter);
    }
}