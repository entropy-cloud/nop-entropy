/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.zip;

import io.nop.api.core.util.ProcessResult;
import io.nop.core.resource.IFile;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;

public interface IZipInput extends Closeable {
    /**
     * 将resource对应的压缩文件解压缩到指定目录下。例如 a.zip 解压缩到/u目录下， a.zip中的b/c.txt文件最后对应/u/b/c.txt文件。
     *
     * @param dir
     * @param filter 过滤需要解压缩的文件
     */
    void unzipToDir(IFile dir, Predicate<ZipEntry> filter) throws IOException;

    default void unzipToDir(IFile dir) throws IOException {
        unzipToDir(dir, null);
    }

    void unzip(BiFunction<ZipEntry, InputStream, ProcessResult> processor) throws IOException;
}