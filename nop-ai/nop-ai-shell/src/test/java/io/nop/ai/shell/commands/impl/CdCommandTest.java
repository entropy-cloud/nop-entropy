package io.nop.ai.shell.commands.impl;

import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.io.ListShellOutput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CdCommandTest {

    @Test
    void testName() throws Exception {
        CdCommand cd = new CdCommand();
        assertEquals("cd", cd.name());
    }

    @Test
    void testDescription() throws Exception {
        CdCommand cd = new CdCommand();
        assertEquals("Change current working directory", cd.description());
    }

    @Test
    void testUsage() throws Exception {
        CdCommand cd = new CdCommand();
        assertEquals("cd [DIRECTORY]", cd.usage());
    }

    @Test
    void testGetHelp() throws Exception {
        CdCommand cd = new CdCommand();
        String help = cd.getHelp();
        assertNotNull(help);
        assertTrue(help.contains("cd"));
        assertTrue(help.contains("Change current working directory"));
    }

    @Test
    void testExecuteToAbsolutePath() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/home/user", new String[]{"/tmp"}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/tmp", context.workingDirectory());
    }

    @Test
    void testExecuteToRelativePath() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/home/user", new String[]{"documents"}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/home/user/documents", context.workingDirectory());
    }

    @Test
    void testExecuteToParentDirectory() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        ListShellOutput stderr = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stderr,
                java.util.Map.of(), "/home/user/documents", new String[]{".."}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/home/user", context.workingDirectory());
    }

    @Test
    void testExecuteToCurrentDirectory() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/home/user", new String[]{"."}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/home/user", context.workingDirectory());
    }

    @Test
    void testExecuteWithoutArguments() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/home/user", new String[0], null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/", context.workingDirectory());
    }

    @Test
    void testExecuteWithMultipleArguments() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        ListShellOutput stderr = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stderr,
                java.util.Map.of(), "/home/user", new String[]{"/tmp", "/home"}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(1, exitCode);
        boolean containsError = false;
        for (String line : stderr.getList()) {
            if (line != null && line.contains("too many arguments")) {
                containsError = true;
                break;
            }
        }
        assertTrue(containsError, "stderr should contain 'too many arguments'");
    }

    @Test
    void testExecuteWithEmptyArgument() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/home/user", new String[]{""}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/", context.workingDirectory());
    }

    @Test
    void testExecuteWithMultipleParentDirectories() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        ListShellOutput stderr = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stderr,
                java.util.Map.of(), "/home/user/documents/files", new String[]{"../.."}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/home/user", context.workingDirectory());
    }

    @Test
    void testExecuteWithRelativeAndAbsoluteMixed() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        ListShellOutput stderr = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stderr,
                java.util.Map.of(), "/home/user", new String[]{"documents", "/tmp/files", "subdir"}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(1, exitCode);
        boolean containsError = false;
        for (String line : stderr.getList()) {
            if (line != null && line.contains("too many arguments")) {
                containsError = true;
                break;
            }
        }
        assertTrue(containsError, "stderr should contain 'too many arguments'");
    }

    @Test
    void testExecuteFromRootToNested() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        ListShellOutput stderr = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stderr,
                java.util.Map.of(), "/home/user", new String[]{"home", "user", "documents"}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(1, exitCode);
        boolean containsError = false;
        for (String line : stderr.getList()) {
            if (line != null && line.contains("too many arguments")) {
                containsError = true;
                break;
            }
        }
        assertTrue(containsError, "stderr should contain 'too many arguments'");
    }

    @Test
    void testExecuteToRootDirectory() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/home/user", new String[]{"/"}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/", context.workingDirectory());
    }

    @Test
    void testExecuteFromRootToParent() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/", new String[]{".."}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/", context.workingDirectory());
    }

    @Test
    void testExecuteWithExtraSlashes() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/home/user", new String[]{"//tmp//files"}, null
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/tmp/files", context.workingDirectory());
    }
}
