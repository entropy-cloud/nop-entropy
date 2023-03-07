/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.zip;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IFile;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;

public class ZipToolHelper {
    public static void defaultAddDir(IZipOutput out, String basePath, IFile file, boolean onlyChild,
                                     Predicate<IFile> filter) throws IOException {
        if (!onlyChild) {
            basePath = StringHelper.appendPath(basePath, file.getName() + "/");
            out.addEntry(out.newZipEntry(basePath));
        }
        List<IFile> children = file.getChildren();
        if (children != null) {
            for (IFile child : children) {
                if (child.isDirectory()) {
                    out.addDir(basePath, child, false, filter);
                } else {
                    if (filter == null || filter.test(file)) {
                        ZipEntry zipEntry = out.newZipEntry(StringHelper.appendPath(basePath, child.getName()));
                        out.addResource(zipEntry, child);
                    }
                }
            }
        }
    }
}
