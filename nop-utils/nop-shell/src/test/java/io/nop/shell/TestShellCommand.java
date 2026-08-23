/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.shell;

import io.nop.commons.env.PlatformEnv;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestShellCommand {

    @Test
    public void testCreateUsesShellDashCOnUnix() {
        ShellCommand cmd = ShellCommand.create("echo hi");
        List<String> cmds = cmd.getCmds();
        if (PlatformEnv.isWindows()) {
            // Windows 分支保持 cmd /c <args...>
            assertEquals("cmd", cmds.get(0));
            assertEquals("/c", cmds.get(1));
        } else {
            // Unix 分支必须是 sh -c "<原始命令串>"，命令串作为单个参数交给 shell 解析
            assertEquals(Arrays.asList("sh", "-c", "echo hi"), cmds);
        }
    }

    @Test
    public void testRunSingleStringCommand() {
        // 真实命令仅在非 Windows 平台执行；Windows 上 shell 语义不同，跳过
        org.junit.jupiter.api.Assumptions.assumeTrue(!PlatformEnv.isWindows());

        ShellResult result = ShellRunner.runCommand("echo hi");
        assertEquals(0, result.getReturnCode());
        assertTrue(result.getOutput().contains("hi"));
    }

    // ==================== splitCommandLine 反斜杠语义测试 ====================

    @Test
    public void testSplitCommandLineWindowsPathPreserved() {
        // 双引号外的反斜杠按字面量保留，Windows 路径不能被吃掉分隔符
        String[] args = ShellCommand.splitCommandLine("C:\\tools\\bin");
        assertEquals(1, args.length);
        assertEquals("C:\\tools\\bin", args[0]);
    }

    @Test
    public void testSplitCommandLineQuotedWindowsPath() {
        String[] args = ShellCommand.splitCommandLine("\"C:\\test x\"");
        assertEquals(1, args.length);
        assertEquals("C:\\test x", args[0]);
    }

    @Test
    public void testSplitCommandLineEscapedQuoteAndBackslash() {
        // \\ -> \ ，\" -> "
        String[] args = ShellCommand.splitCommandLine("a\\\\b \"c\\\"d\"");
        assertEquals(2, args.length);
        assertEquals("a\\b", args[0]);
        assertEquals("c\"d", args[1]);
    }

    @Test
    public void testSplitCommandLineSingleQuoteKeepsBackslashLiteral() {
        // POSIX 单引号内反斜杠是字面量，不作为转义符
        String[] args = ShellCommand.splitCommandLine("'a\\b c'");
        assertEquals(1, args.length);
        assertEquals("a\\b c", args[0]);
    }

    @Test
    public void testSplitCommandLineDanglingBackslashRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ShellCommand.splitCommandLine("abc\\"));
    }

    @Test
    public void testSplitCommandLinePlainArgs() {
        String[] args = ShellCommand.splitCommandLine("mvn -DoutputFile=x dependency:tree");
        assertEquals(3, args.length);
        assertEquals("mvn", args[0]);
        assertEquals("-DoutputFile=x", args[1]);
        assertEquals("dependency:tree", args[2]);
    }
}
