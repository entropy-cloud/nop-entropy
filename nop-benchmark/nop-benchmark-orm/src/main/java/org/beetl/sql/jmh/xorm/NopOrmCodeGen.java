/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.xorm;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.annotations.ide.NopDebugger;
import io.nop.api.core.config.AppConfig;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelHelper;

import java.io.File;
import java.util.Collections;

@NopDebugger
public class NopOrmCodeGen {
    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);

        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(NopOrmCodeGen.class);
            String targetRootPath = FileHelper.getFileUrl(new File(projectDir, "src/main/java"));
            XCodeGenerator generator = new XCodeGenerator("/nop/templates/orm-entity", targetRootPath);
            IResource resource = VirtualFileSystem.instance().getResource("/nop/test/orm/app.orm.xml");
            OrmModel ormModel = (OrmModel) DslModelHelper.loadDslModel(resource);
            generator.execute("", Collections.singletonMap("codeGenModel", ormModel), XLang.newEvalScope());
        } finally {
            CoreInitialization.destroy();
        }
    }
}
