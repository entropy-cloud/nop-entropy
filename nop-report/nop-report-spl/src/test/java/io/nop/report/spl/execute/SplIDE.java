/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.spl.execute;

import com.scudata.ide.spl.SPL;
import io.nop.commons.util.MavenDirHelper;

import java.io.File;

public class SplIDE {
    public static void main(String[] args) {
        File classesFile = MavenDirHelper.getClassesDir(SplIDE.class);
        System.setProperty("start.home", classesFile.getAbsolutePath());
        SPL.main(args);
    }
}
