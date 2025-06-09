/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.util.FileHelper;
import io.nop.core.CoreConfigs;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.File;

import static io.nop.codegen.CodeGenConfigs.CFG_CODEGEN_TRACE_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestNopCli extends BaseTestCase {

    @Inject
    CommandLine.IFactory factory;

    @Test
    public void testGen() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_CODEGEN_TRACE_ENABLED, true);
        String[] args = new String[]{"gen", "src/test/resources/io/nop/cli/test.orm.xlsx",
                "-t=v:/nop/templates/orm",
                "-o", "target/gen", "-F"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testGenOrmExcelForPdm() {
        String[] args = new String[]{"gen-orm-excel", "src/test/resources/io/nop/cli/test.pdm",
                "-o", "target/gen/gen-from-pdm.orm.xlsx",};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testGenOrmExcelForPdman() {
        String[] args = new String[]{"gen-orm-excel", "src/test/resources/io/nop/cli/test.pdma.json",
                "-o", "target/gen/gen-from-pdman.orm.xlsx",};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testGenXpt() {
        String[] args = new String[]{"gen-file", "src/test/resources/data/data.json5",
                "-t", "/xpt/test.xpt.xlsx",
                "-o", "target/gen/test.xpt.html"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testRunTask() {
        CoreInitialization.destroy();
        String[] args = new String[]{"run-task", "src/test/resources/task/test.task.xml",
                "-if", "src/test/resources/data/data.json5"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testRunBatchGenDemo() {
        CoreInitialization.destroy();
        File file = new File(getModuleDir(), "../nop-cli/demo/_vfs");
        System.setProperty(CoreConfigs.CFG_RESOURCE_DIR_OVERRIDE_VFS.getName(), file.getAbsolutePath());
        String[] args = new String[]{"run-task", "v:/batch/batch-gen-demo.task.xml", "-i", "{totalCount:2000,taskKey:'abc'}"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
        System.getProperties().remove(CoreConfigs.CFG_RESOURCE_DIR_OVERRIDE_VFS.getName());

        // 2000 * (0.3 * (1/3 + 1/3 + 2/3) + 0.7) * 10 = 2200 * 10
        String text = FileHelper.readText(getTargetFile("txn-abc.dat"), null);
        assertTrue(text.contains("totalCount=22000 "));
    }

    @Test
    public void testRunBatchDemo() {
        CoreInitialization.destroy();
        File file = new File(getModuleDir(), "../nop-cli/demo/_vfs");
        File devDir = new File(getModuleDir(), "../nop-cli/demo/");
        System.setProperty(CoreConfigs.CFG_DEV_ROOT_PATH.getName(), devDir.getAbsolutePath());
        System.setProperty(CoreConfigs.CFG_RESOURCE_DIR_OVERRIDE_VFS.getName(), file.getAbsolutePath());
        String[] args = new String[]{"run-task", "v:/batch/batch-demo.task.xml", "-i", "{bizDate:'2024-12-08'}"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
        System.getProperties().remove(CoreConfigs.CFG_RESOURCE_DIR_OVERRIDE_VFS.getName());
    }

    @Test
    public void testFile() {
        CoreInitialization.destroy();
        String[] args = new String[]{"gen", "../nop-cli/demo/_vfs/app/demo/orm/app.orm.xml",
                "-t", "/demo/templates/orm", "-o", "target/demo"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testTransform() {
        CoreInitialization.destroy();
        String[] args = new String[]{"transform", "../nop-cli/demo/_vfs/app/demo/orm/app.orm.xml",
                "-t", "/nop/orm/imp/orm.imp.xml", "-o", "target/app.orm.xlsx"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);

        args = new String[]{"transform", "target/app.orm.xlsx",
                "-t", "/nop/orm/imp/orm.imp.xml", "-o", "target/app.orm.json"};

        app = new NopCliApplication();
        app.setFactory(factory);
        ret = app.run(args);
        assertEquals(0, ret);

        args = new String[]{"transform", "target/app.orm.xlsx",
                "-t", "/nop/orm/imp/orm.imp.xml", "-o", "target/app.orm.xml"};

        app = new NopCliApplication();
        app.setFactory(factory);
        ret = app.run(args);
        assertEquals(0, ret);

        args = new String[]{"transform", "../nop-cli/demo/_vfs/app/demo/orm/app.orm.xml",
                "-o", "target/app.orm.json"};

        app = new NopCliApplication();
        app.setFactory(factory);
        ret = app.run(args);
        assertEquals(0, ret);

        args = new String[]{"transform", "../nop-cli/demo/_vfs/app/demo/orm/app.orm.xml",
                "-o", "target/app.orm.yaml"};

        app = new NopCliApplication();
        app.setFactory(factory);
        ret = app.run(args);
        assertEquals(0, ret);

        args = new String[]{"transform", "target/app.orm.xlsx",
                "-o", "target/app.orm.shtml"};

        app = new NopCliApplication();
        app.setFactory(factory);
        ret = app.run(args);
        assertEquals(0, ret);

        args = new String[]{"transform", "target/app.orm.xlsx",
                "-o", "target/app.xml"};

        app = new NopCliApplication();
        app.setFactory(factory);
        ret = app.run(args);
        assertEquals(0, ret);
    }

    @Test
    public void testRenderPages() {
        CoreInitialization.destroy();
        String[] args = new String[]{"run", "../nop-cli/demo/scripts/render-pages.xrun",
                "-i", "{moduleId:'app/demo'}", "-o", "target/demo"};
        NopCliApplication app = new NopCliApplication();
        app.setFactory(factory);
        int ret = app.run(args);
        assertEquals(0, ret);

        File file = getTargetFile("demo/app/demo/pages/Demo/main.page.json");
        String text = FileHelper.readText(file, null);
        assertTrue(text.contains("\"name\": \"b\""));
        assertTrue(text.contains("\"name\": \"a\""));
    }
}
