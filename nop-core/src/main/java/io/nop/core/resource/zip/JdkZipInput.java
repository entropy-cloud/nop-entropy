/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.zip;

import io.nop.api.core.util.ProcessResult;
import io.nop.api.core.util.progress.IProgressListener;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JdkZipInput implements IZipInput {
    private final IProgressListener progressListener;
    private final ZipInputStream zin;

    public JdkZipInput(ZipInputStream zin, IProgressListener progressListener) {
        this.zin = zin;
        this.progressListener = progressListener;
    }

    IStepProgressListener getStepListener(String reason, String path, long size) {
        if (progressListener == null)
            return null;
        return progressListener.toStepListener(reason + ':' + path, size);
    }

    @Override
    public void unzipToDir(IFile dir, Predicate<ZipEntry> filter) throws IOException {
        ZipEntry zipEntry = null;
        while ((zipEntry = zin.getNextEntry()) != null) { //NOSONAR
            String entryName = zipEntry.getName();
            if (entryName.endsWith("/") || entryName.endsWith("\\"))
                continue;
            if (filter != null && !filter.test(zipEntry))
                continue;

            IResource file = dir.getResource(entryName);
            ResourceHelper.saveFromStream(file, zin, getStepListener("unzip", file.getPath(), file.length()));
        }
    }

    @Override
    public void unzip(BiFunction<ZipEntry, InputStream, ProcessResult> processor) throws IOException {
        ZipEntry zipEntry = null;
        while ((zipEntry = zin.getNextEntry()) != null) { //NOSONAR
            if (processor.apply(zipEntry, zin) != ProcessResult.CONTINUE)
                break;
        }
    }

    @Override
    public void close() throws IOException {
        zin.close();
    }
}