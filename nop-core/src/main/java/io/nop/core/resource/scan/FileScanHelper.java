/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.scan;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ZipEntryResource;
import io.nop.core.resource.store.InMemoryResourceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Enumeration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileScanHelper {
    static final Logger LOG = LoggerFactory.getLogger(FileScanHelper.class);

    public static void scanZip(ZipFile file, String entryPath, String basePath, InMemoryResourceStore store) {
        ResourceHelper.checkNormalVirtualPath(basePath);
        if (entryPath.startsWith("/"))
            entryPath = entryPath.substring(1);

        String normalizeEntry = normalizeEntryPath(entryPath);

        scanZip(file, entryPath, (zipFile, entry) -> {
            if (entry.getName().endsWith("/"))
                return;
            String path = StringHelper.appendPath(basePath, entry.getName().substring(normalizeEntry.length()));
            LOG.trace("nop.vfs.add-zip-entry:path={},entry={},file={}", path, entry.getName(), file.getName());
            IResource resource = new ZipEntryResource(path, file, entry);
            store.addResourceIfAbsent(resource);
        });
    }

    static String normalizeEntryPath(String entryPath) {
        if (entryPath.endsWith("/"))
            return entryPath.substring(0, entryPath.length() - 1);
        return entryPath;
    }

    public static void scanZip(ZipFile file, String entryPath, BiConsumer<ZipFile, ZipEntry> action) {
        Enumeration<? extends ZipEntry> en = file.entries();
        while (en.hasMoreElements()) {
            ZipEntry entry = en.nextElement();
            if (entry.getName().startsWith(entryPath)) {
                action.accept(file, entry);
            }
        }
    }

    public static void scanDir(File dir, Consumer<File> action) {
        File[] subFiles = dir.listFiles();
        if (subFiles != null) {
            for (File subFile : subFiles) {
                if (subFile.isFile()) {
                    action.accept(subFile);
                } else {
                    scanDir(subFile, action);
                }
            }
        }
    }
}