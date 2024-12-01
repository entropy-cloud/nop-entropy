
package app.demo.web;

import io.nop.api.core.config.AppConfig;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.CoreConfigs;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;

import java.io.File;

public class DemoDddWebCodeGen {
    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);

        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(DemoDddWebCodeGen.class);
            XCodeGenerator.runPostcompile(new File(projectDir, "../demo-ddd-codegen"), "/", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../demo-ddd-meta"), "/", false);
            XCodeGenerator.runPostcompile(new File(projectDir, "../demo-ddd-meta"), "/", false);
            XCodeGenerator.runPrecompile(projectDir, "/", false);
        } finally {
            CoreInitialization.destroy();
        }
    }
}
