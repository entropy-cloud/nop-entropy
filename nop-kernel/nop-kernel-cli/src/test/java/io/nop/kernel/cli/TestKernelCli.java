package io.nop.kernel.cli;

import io.nop.api.core.ApiConfigs;
import io.nop.codegen.CodeGenConfigs;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestKernelCli extends BaseTestCase {

    @BeforeAll
    public static void init() {
        BaseTestCase.setTestConfig(ApiConfigs.CFG_DEBUG, true);
        BaseTestCase.setTestConfig(CodeGenConfigs.CFG_CODEGEN_TRACE_ENABLED, true);
        FileHelper.setCurrentDir(new File(MavenDirHelper.projectDir(TestKernelCli.class), "demo"));
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testCompile() {

        String[] args = new String[]{
                "gen", "model/demo.orm.xml", "-t=/demo/templates/boot", "-o=target/gen"
        };
        int retCode = KernelCliApplication.run(args);
        assertEquals(0, retCode);
    }
}
