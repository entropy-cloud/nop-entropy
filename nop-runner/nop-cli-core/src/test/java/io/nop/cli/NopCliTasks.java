/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli;

import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static io.nop.api.core.ApiConfigs.CFG_DEBUG;
import static io.nop.codegen.CodeGenConfigs.CFG_CODEGEN_TRACE_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
//@QuarkusTest
public class NopCliTasks {
    @Inject
    CommandLine.IFactory factory;

    @Test
    public void testReverseDb() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_DEBUG, true);
        String[] args = new String[]{"reverse-db", "--dump",
                "-c=com.mysql.cj.jdbc.Driver",
                "-j=jdbc:mysql://127.0.0.1:3306/dev?useUnicode=true&characterEncoding=utf-8&useSSL=true&serverTimezone=UTC",
                "-u=nop", "-p=nop-test", "-o=target/reverse.orm.xlsx", "datart",
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
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
        app.setFactory(factory);
        assertEquals(0, app.run(args));

        args = new String[]{"gen", "../nop-sys/model/nop-sys.orm.xlsx",
                "-t", "/nop/templates/orm",
                "-o", "target/gen/nop-sys"
        };
        assertEquals(0, app.run(args));


    }

    @Test
    public void testExtract() {
        String[] args = new String[]{"extract", "../nop-sys/model/nop-sys.orm.xlsx",
                "-f", "xml",
                "-o", "target/gen/out.xml"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        assertEquals(0, app.run(args));
    }

    @Test
    public void testRun() {
        String[] args = new String[]{"run", "data",
                "-i", "1000"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        app.run(args);
    }

    @Test
    public void testWatch() {
        String[] args = new String[]{"watch", "src",
                "-e", "tasks/gen-web.xrun"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        app.run(args);
    }

    @Test
    public void testGenDao() {
        String[] args = new String[]{"gen", "../nop-sys/model/nop-sys.orm.xlsx",
                "-t", "/nop/templates/orm-dao"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        assertEquals(0, app.run(args));
    }

    @Test
    public void testExportDb() {
        String[] args = new String[]{"export-db", "../nop-cli/demo/test.export-db.xml",
                "-o", "target/data", "-s=target/data/test-export.state.json"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        assertEquals(0, app.run(args));
    }

    @Test
    public void testImportDb() {
        String[] args = new String[]{"import-db", "../nop-cli/demo/test.import-db.xml",
                "-i", "target/data", "-s=target/data/test-import.state.json"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        assertEquals(0, app.run(args));
    }

    @Test
    public void testExternalTable() {
        String[] args = new String[]{"gen", "../nop-cli/demo/test-app.orm.xlsx",
                "-t", "/nop/templates/orm",
                "-o", "target/data"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        assertEquals(0, app.run(args));
    }

    @Test
    public void testLargeDataSet() {
        CoreInitialization.destroy();
        System.setProperty("nop.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        System.setProperty("nop.datasource.jdbc-url", "jdbc:mysql://127.0.0.1:3306/dev?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC");
        System.setProperty("nop.datasource.username", "nop");
        System.setProperty("nop.datasource.password", "nop-test");


        String[] args = new String[]{"gen-file",
                "-t", "/xpt/test-large-ds.xpt.xlsx", "-o", "target/gen/out.xlsx"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testGenOrmFromXDef() {
        String[] args = new String[]{"gen-orm-excel", "../nop-xdefs/src/main/resources/_vfs/nop/schema/orm/entity.xdef",
                "-o", "target/gen-from-xdef.orm.xlsx"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        assertEquals(0, app.run(args));
    }

    @Test
    public void testTransformRecordXml() {
        String[] args = new String[]{"transform", "../nop-cli/demo/record.xml",
                "-o", "target/test-transform.record-file.xlsx",
                "-t", "v:/nop/record/imp/record-file.imp.xml"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        assertEquals(0, app.run(args));
    }

    @Test
    public void testPathTree() {
        String[] args = new String[]{
                "file", "plain-path-tree", "-b", "c:/can/nop/nop-entropy",
                "-d","nop-xlang",
                "-o", "target/path-tree.txt"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        assertEquals(0, app.run(args));
    }

    @Test
    public void testPaths() {
        String[] args = new String[]{
                "file", "find", "-b", "c:/can/nop/nop-entropy",
                "-d","nop-kernel/nop-core",
                "-o", "target/find.txt"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        assertEquals(0, app.run(args));
    }

    @Test
    public void testSplitHtml(){
        String[] args = new String[]{"convert", "../nop-cli/demo/test-app.orm.xlsx",
                "-o", "target/test-app.shtml"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        assertEquals(0, app.run(args));

        args = new String[]{"split", "target/test-app.shtml",
                "-o", "target/test-app"
        };
        assertEquals(0, app.run(args));
    }

    @Test
    public void testConvertXlsx(){
        String[] args = new String[]{"convert", "../../nop-report/nop-report-demo/src/main/" +
                "resources/_vfs/nop/report/demo/base/17-动态Sheet和动态列.xpt.xlsx",
                "-o", "target/test-report.xpt.xml"
        };
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        assertEquals(0, app.run(args));
    }
}
