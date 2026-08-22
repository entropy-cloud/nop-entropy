/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.shell;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.shell.utils.ShellCommands;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.nop.shell.ShellConfigs.CFG_SHELL_TASK_ROOT_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestShellRunner {

    static File getResourceFile(String name) {
        return new File(TestShellRunner.class.getClassLoader().getResource(name).getFile());
    }

    @BeforeAll
    public static void setUp() {
        File dir = getResourceFile("test/shell-tasks");
        AppConfig.getConfigProvider().updateConfigValue(CFG_SHELL_TASK_ROOT_DIR, FileHelper.getAbsolutePath(dir));
    }

    @Test
    public void testTask() {
        ShellResult result = new ShellRunner().run(ShellCommands.task("my-task"));
        System.out.println(result.getOutput());
        assertEquals(0, result.getReturnCode());
        assertTrue(result.getOutput().indexOf("aaa") >= 0);
    }

    @Test
    public void testUnknown() {
        ShellCommand cmd = new ShellCommand("_unknown_call");
        try {
            ShellResult result = new ShellRunner().run(cmd);
            assertTrue(false);
            System.out.println(result.getReturnCode());
        } catch (NopException e) {
            assertEquals(ShellErrors.ERR_SHELL_EXEC_COMMAND_FAIL.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void testRunWithInput() {
        // cat 从 stdin 读到 EOF 才退出：写完 input 后必须关闭进程 stdin，否则永久挂起直到超时
        org.junit.jupiter.api.Assumptions.assumeTrue(!io.nop.commons.env.PlatformEnv.isWindows());

        ShellCommand cmd = new ShellCommand("cat").input("hello").timeout(10_000);
        DefaultShellOutputCollector collector = new DefaultShellOutputCollector();
        int exitCode = new ShellRunner().run(cmd, collector);
        assertEquals(0, exitCode);
        assertTrue(collector.getOutput().contains("hello"));
    }
}