/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.web;

import io.nop.api.core.config.AppConfig;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.CoreConfigs;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;

import java.io.File;

public class NopAuthWebCodeGen {
    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);

        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(NopAuthWebCodeGen.class);
            XCodeGenerator.runPostcompile(new File(projectDir, "../nop-auth-codegen"), "/", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../nop-auth-meta"), "/", false);
            XCodeGenerator.runPostcompile(new File(projectDir, "../nop-auth-meta"), "/", false);
            XCodeGenerator.runPrecompile(projectDir, "/", false);

        } finally {
            CoreInitialization.destroy();
        }
    }
}
