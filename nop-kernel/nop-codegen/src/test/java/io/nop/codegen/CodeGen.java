/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.initialize.CoreInitialization;

import java.io.File;

public class CodeGen {
    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);

        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(CodeGen.class);
            // new GenAopProxy().execute(new File(projectDir,"../nop-graphql/nop-graphql-core"),false);

            XCodeGenerator.runPrecompile(new File(projectDir, "../nop-core"), "/", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../nop-xlang"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../nop-record-mapping"), "", false);

            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-core-framework/nop-ioc"), "", false);

            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-frontend-support/nop-ui"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-frontend-support/nop-web-page"), "", false);

            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-task/nop-task-core"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-wf/nop-wf-core"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-batch/nop-batch-dsl"), "", false);

            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-utils/nop-fsm"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-core-framework/nop-biz"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-persistence/nop-dao"), "", false);
            //
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-persistence/nop-orm-model"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-persistence/nop-orm-eql"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-persistence/nop-orm"), "", false);

            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-format/nop-excel"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-format/nop-record"), "", false);

            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-format/nop-converter"), "", false);

            // XCodeGenerator.runProjectFile(new File(projectDir, "../nop-orm"),
            // "/src/test/resources/gen-demo-entity.xgen", true);

            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-core-framework/nop-graphql/nop-graphql-core"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-rule/nop-rule-core"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-stream/nop-stream-cep"), "", false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-auth/nop-auth-core"), "", false);

            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-persistence/nop-dbtool/nop-dbtool-exp"), "", false);

            XCodeGenerator.runPrecompile(new File(projectDir, "../../nop-ai/nop-ai-core"), "", false);

            // System.setProperty("dryRun","true");
            // CodeGenTask.main(new String[]{new File("projectDir","../nop-graphql").getAbsolutePath()});
        } finally {
            CoreInitialization.destroy();
        }
    }
}
