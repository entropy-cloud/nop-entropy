
package io.nop.oauth.web;

import io.nop.api.core.config.AppConfig;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.CoreConfigs;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;

import java.io.File;

public class NopOauthWebCodeGen {
    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);

        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(NopOauthWebCodeGen.class);
            XCodeGenerator.runPostcompile(new File(projectDir, "../nop-oauth-codegen"), "/", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../nop-oauth-meta"), "/", false);
            XCodeGenerator.runPostcompile(new File(projectDir, "../nop-oauth-meta"), "/", false);
            XCodeGenerator.runPrecompile(projectDir, "/", false);
        } finally {
            CoreInitialization.destroy();
        }
    }
}
