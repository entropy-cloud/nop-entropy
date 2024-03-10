/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tool.migrate;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.util.Map;
import java.util.function.Predicate;

public abstract class AbstractMigrateTask {

    protected XNode readXml(File file) {
        return ResourceHelper.readXml(new FileResource(file));
    }

    protected Map<String, Object> readJson(File file) {
        return JsonTool.parseBeanFromResource(new FileResource(file), Map.class);
    }

    public abstract void migrateFile(File sourceFile, File targetFile);

    public void migrateDir(File sourceDir, Predicate<File> filter) {
        migrateDir(sourceDir, filter, sourceDir);
    }

    public void migrateDir(File sourceDir, Predicate<File> filter, File targetDir) {
        if (sourceDir.isDirectory()) {
            File[] subs = sourceDir.listFiles();
            if (subs != null) {
                for (File sub : subs) {
                    migrateDir(sub, filter, getTargetFile(targetDir, sub));
                }
            }
        } else {
            if (filter == null || filter.test(sourceDir)) {
                migrateFile(sourceDir, targetDir);
            }
        }
    }

    protected File getTargetFile(File targetDir, File srcFile) {
        return new File(targetDir, srcFile.getName());
    }
}
