
package io.nop.dyn.web;

import io.nop.api.core.config.AppConfig;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.CoreConfigs;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;

import java.io.File;

public class NopDynWebCodeGen {
    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);

        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(NopDynWebCodeGen.class);
            XCodeGenerator.runPostcompile(new File(projectDir, "../nop-dyn-codegen"), "/", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../nop-dyn-meta"), "/", false);
            XCodeGenerator.runPostcompile(new File(projectDir, "../nop-dyn-meta"), "/", false);
            XCodeGenerator.runPrecompile(projectDir, "/", false);
        } finally {
            CoreInitialization.destroy();
        }
    }
}
