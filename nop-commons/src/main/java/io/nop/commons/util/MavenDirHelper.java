/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import java.io.File;
import java.net.URL;

public class MavenDirHelper {

    public static File guessProjectDir() {
        try {
            URL url = MavenDirHelper.class.getClassLoader().getResource("application.yaml");
            if (url == null)
                url = MavenDirHelper.class.getClassLoader().getResource("bootstrap.yaml");

            if (url != null) {
                File dir = URLHelper.getFile(url);
                if (dir != null && url.toString().indexOf("/classes/") > 0)
                    return dir.getParentFile().getParentFile().getParentFile();
            }
        } catch (Exception e) { //NOPMD - suppressed EmptyCatchBlock
            // ignore
        }
        return FileHelper.currentDir();
    }

    public static File projectDir(Class<?> clazz) {
        File dir = getClassesDir(clazz);
        if (dir == null)
            return null;
        // classes -> target -> project
        return dir.getParentFile().getParentFile();
    }

    public static File getClassesDir(Class<?> clazz) {
        String className = clazz.getName();
        String path = className.replace('.', '/') + ".class";

        File file = FileHelper.getClassPathFile(path);
        if (file == null)
            return null;

        String fullPath = file.getAbsolutePath();
        return new File(fullPath.substring(0, fullPath.length() - path.length()));
    }
}