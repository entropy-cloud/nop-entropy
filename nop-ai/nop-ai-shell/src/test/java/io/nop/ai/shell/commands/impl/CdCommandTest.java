package io.nop.ai.shell.commands.impl;

import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.io.ListShellOutput;
import io.nop.ai.shell.io.ShellChunk;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.fs.LocalToolFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CdCommandTest {

    private IToolFileSystem fileSystem;

    @BeforeEach
    void setUp() throws IOException {
        Path tempDir = Files.createTempDirectory("cd-test");
        tempDir.toFile().deleteOnExit();
        fileSystem = new LocalToolFileSystem(tempDir.toFile());
    }

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
                java.util.Map.of(), "/home/user", new String[]{"/tmp"}, fileSystem
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
                java.util.Map.of(), "/home/user", new String[]{"documents"}, fileSystem
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
                java.util.Map.of(), "/home/user/documents", new String[]{".."}, fileSystem
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
                java.util.Map.of(), "/home/user", new String[]{"."}, fileSystem
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
                java.util.Map.of(), "/home/user", new String[0], fileSystem
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/", context.workingDirectory());
    }

    private boolean stderrContains(ListShellOutput stderr, String text) {
        for (ShellChunk chunk : stderr.getChunks()) {
            if (chunk.isText() && chunk.asText().contains(text)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void testExecuteWithMultipleArguments() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        ListShellOutput stderr = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stderr,
                java.util.Map.of(), "/home/user", new String[]{"/tmp", "/home"}, fileSystem
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(1, exitCode);
        assertTrue(stderrContains(stderr, "too many arguments"));
    }

    @Test
    void testExecuteWithEmptyArgument() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/home/user", new String[]{""}, fileSystem
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
                java.util.Map.of(), "/home/user/documents/files", new String[]{"../.."}, fileSystem
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
                java.util.Map.of(), "/home/user", new String[]{"documents", "/tmp/files", "subdir"}, fileSystem
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(1, exitCode);
        assertTrue(stderrContains(stderr, "too many arguments"));
    }

    @Test
    void testExecuteFromRootToNested() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        ListShellOutput stderr = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stderr,
                java.util.Map.of(), "/home/user", new String[]{"home", "user", "documents"}, fileSystem
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(1, exitCode);
        assertTrue(stderrContains(stderr, "too many arguments"));
    }

    @Test
    void testExecuteToRootDirectory() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/home/user", new String[]{"/"}, fileSystem
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
                java.util.Map.of(), "/", new String[]{".."}, fileSystem
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
                java.util.Map.of(), "/home/user", new String[]{"//tmp//files"}, fileSystem
        );
        CdCommand cd = new CdCommand();
        int exitCode = cd.execute(context);
        assertEquals(0, exitCode);
        assertEquals("/tmp/files", context.workingDirectory());
    }
}
