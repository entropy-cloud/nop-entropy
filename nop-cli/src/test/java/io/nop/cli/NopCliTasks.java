/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cli;

import io.nop.api.core.config.AppConfig;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import javax.inject.Inject;

import static io.nop.api.core.ApiConfigs.CFG_DEBUG;
import static io.nop.codegen.CodeGenConfigs.CFG_CODEGEN_TRACE_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
@QuarkusTest
public class NopCliTasks {
    @Inject
    CommandLine.IFactory factory;

    @Test
    public void discover(){
        AppConfig.getConfigProvider().updateConfigValue(CFG_DEBUG, true);
        String[] args = new String[]{"reverse",
                "-c=com.mysql.cj.jdbc.Driver",
                "-j=jdbc:mysql://127.0.0.1:3306/dev?useUnicode=true&characterEncoding=utf-8&useSSL=true&serverTimezone=UTC",
                "-u=nop", "-p=nop-test","litemall","test"
        };
        NopCliApplication app = new NopCliApplication();
        app.factory = factory;
        assertEquals(0, app.run(args));
    }

    @Test
    public void genAuth() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_CODEGEN_TRACE_ENABLED, true);
        AppConfig.getConfigProvider().updateConfigValue(CFG_DEBUG, true);
        String[] args = new String[]{"gen", "../nop-auth/model/nop-auth.orm.xlsx",
                "-t", "/nop/templates/orm",
                "-o", "target/gen/nop-auth"
        };
        NopCliApplication app = new NopCliApplication();
        app.factory = factory;
        assertEquals(0, app.run(args));

        args = new String[]{"gen", "../nop-sys/model/nop-sys.orm.xlsx",
                "-t", "/nop/templates/orm",
                "-o", "target/gen/nop-sys"
        };
        assertEquals(0, app.run(args));
    }
}
