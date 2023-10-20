/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.tool.migrate;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;

import java.io.File;
import java.util.function.Predicate;

public class NopMigrateTask {
    File rootDir;

    File getDir(String postfix) {
        File[] subList = rootDir.listFiles();
        if (subList != null) {
            for (File sub : subList) {
                if (sub.getName().endsWith(postfix))
                    return sub;
            }
        }
        return null;
    }

    private void moveToDir(File srcDir, File targetDir, Predicate<File> filter) {
        File[] children = srcDir.listFiles();
        if (children != null) {
            for (File child : children) {
                File targetChild = new File(targetDir, child.getName());
                if (child.isDirectory()) {
                    moveToDir(child, targetChild, filter);
                } else {
                    if (filter.test(child)) {
                        if (!child.renameTo(targetChild)) {
                            FileHelper.copyFile(child, targetChild);
                            child.delete();
                        }
                    }
                }
            }
        }
    }

    /**
     * 讲meta文件从service模块移动到专门的meta模块中，便于直接引用meta文件
     */
    public void fix20230819() {
        File serviceDir = getDir("-service");
        if (serviceDir != null) {
            FileHelper.deleteAll(new File(serviceDir, "_templates"));
            FileHelper.deleteAll(new File(serviceDir, "postcompile"));
            FileHelper.deleteAll(new File(serviceDir, "precompile"));

            String appName = StringHelper.removeTail(serviceDir.getName(), "-service");
            moveToDir(serviceDir, new File(rootDir, appName + "-meta"), file -> {
                if (!file.getAbsolutePath().replace('\\', '/').contains("/src/main/resources"))
                    return false;
                if (file.getName().endsWith(".xmeta"))
                    return true;
                if (file.getName().endsWith(".dict.yaml"))
                    return true;
                if (file.getName().endsWith(".i18n.yaml"))
                    return true;
                return false;
            });
        }

        File daoDir = getDir("-dao");
        if (daoDir != null) {
            String appName = StringHelper.removeTail(daoDir.getName(), "-dao");
            moveToDir(daoDir, new File(rootDir, appName + "-meta"), file -> {
                if (!file.getAbsolutePath().replace('\\', '/').contains("/src/main/resources"))
                    return false;
                if (file.getName().endsWith(".dict.yaml"))
                    return true;
                return false;
            });
        }
    }

    public static void main(String[] args) {
        NopMigrateTask task = new NopMigrateTask();

//        File base = new File("c:/can/nop/nop-entropy");
//        String[] modules = new String[]{
//                "nop-auth", "nop-sys", "nop-wf", "nop-rule", "nop-task",
//                "nop-tcc", "nop-report", "nop-job", "nop-file",
//                "nop-batch"
//        };
//
//        for (String module : modules) {
//            task.rootDir = new File(base, module);
//            task.fix20230819();
//        }

        task.rootDir = new File("c:/can/nop/nop-app-mall");
        task.fix20230819();
    }
}
