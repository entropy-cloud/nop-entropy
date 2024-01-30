/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service;

import io.nop.codegen.XCodeGenerator;
import io.nop.codegen.task.GenAopProxy;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.initialize.CoreInitialization;

import java.io.File;

public class AuthCodeGen {
    public static void main(String[] args) {
        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(AuthCodeGen.class);
            XCodeGenerator.runPrecompile(projectDir, "/", false);

            new GenAopProxy().execute(projectDir, false);
            new GenAopProxy().execute(projectDir, true);
        } finally {
            CoreInitialization.destroy();
        }
    }
}
