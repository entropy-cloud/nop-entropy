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

/**
 * 生成SimsClass等单元测试所需的实体类
 */
public class OrmCodeGen {
    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);

        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(OrmCodeGen.class);
            XCodeGenerator.runProjectFile(projectDir, "/src/test/resources/gen-demo-entity.xgen", "src/test/java");

        } finally {
            CoreInitialization.destroy();
        }
    }
}
