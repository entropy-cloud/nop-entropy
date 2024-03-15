/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package demo.orm.codegen;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.CoreConfigs;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;

import java.io.File;

/**
 *
 */
public class OrmCodeGen {

    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(
                ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        AppConfig.getConfigProvider().updateConfigValue(
                CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);
        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(OrmCodeGen.class);
            XCodeGenerator.runProjectFile(
                    projectDir,
                    "/src/test/resources/gen-entities.xgen",
                    "src/test/java");
        } finally {
            CoreInitialization.destroy();
        }
    }

}
