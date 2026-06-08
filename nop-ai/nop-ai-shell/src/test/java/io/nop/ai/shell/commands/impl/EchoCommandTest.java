package io.nop.ai.shell.commands.impl;

import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.io.ListShellOutput;
import io.nop.ai.shell.io.ShellChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EchoCommandTest {

    @Test
    void testName() throws Exception {
        EchoCommand echo = new EchoCommand();
        assertEquals("echo", echo.name());
    }

    @Test
    void testDescription() throws Exception {
        EchoCommand echo = new EchoCommand();
        assertEquals("Display a line of text", echo.description());
    }

    @Test
    void testUsage() throws Exception {
        EchoCommand echo = new EchoCommand();
        assertEquals("echo [OPTIONS] [TEXT]...", echo.usage());
    }

    @Test
    void testGetHelp() throws Exception {
        EchoCommand echo = new EchoCommand();
        String help = echo.getHelp();
        assertNotNull(help);
        assertTrue(help.contains("echo"));
        assertTrue(help.contains("Display a line of text"));
        assertTrue(help.contains("-n"));
        assertTrue(help.contains("Do not output the trailing newline"));
    }

    private String getFirstLine(ListShellOutput stdout) {
        for (ShellChunk chunk : stdout.getChunks()) {
            if (chunk.isText()) {
                return chunk.asText().replace("\n", "");
            }
        }
        return null;
    }

    @Test
    void testExecuteWithArguments() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/", new String[]{"hello", "world"}, null
        );
        EchoCommand echo = new EchoCommand();
        int exitCode = echo.execute(context);
        assertEquals(0, exitCode);
        assertEquals("hello world", getFirstLine(stdout));
    }

    @Test
    void testExecuteWithSingleArgument() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/", new String[]{"hello"}, null
        );
        EchoCommand echo = new EchoCommand();
        int exitCode = echo.execute(context);
        assertEquals(0, exitCode);
        assertEquals("hello", getFirstLine(stdout));
    }

    @Test
    void testExecuteWithoutArguments() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/", new String[0], null
        );
        EchoCommand echo = new EchoCommand();
        int exitCode = echo.execute(context);
        assertEquals(0, exitCode);
        assertEquals(1, stdout.getChunks().stream().filter(c -> c.isText()).count());
        assertEquals("\n", stdout.getChunks().stream().filter(c -> c.isText()).findFirst().get().asText());
    }

    @Test
    void testExecuteWithNoNewlineFlag() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/", new String[]{"-n", "hello"}, null
        );
        EchoCommand echo = new EchoCommand();
        int exitCode = echo.execute(context);
        assertEquals(0, exitCode);
        assertEquals("hello", getFirstLine(stdout));
    }

    @Test
    void testExecuteWithLongNoNewlineFlag() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/", new String[]{"--no-newline", "hello"}, null
        );
        EchoCommand echo = new EchoCommand();
        int exitCode = echo.execute(context);
        assertEquals(0, exitCode);
        // --no-newline sets flag "no-newline", but EchoCommand only checks hasFlag("n")
        // so this does NOT suppress newline. It calls println("hello") which writes "hello\n"
        assertEquals("hello\n", stdout.getChunks().stream().filter(c -> c.isText()).findFirst().get().asText());
    }

    @Test
    void testExecuteWithMultipleArguments() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/", new String[]{"one", "two", "three", "four"}, null
        );
        EchoCommand echo = new EchoCommand();
        int exitCode = echo.execute(context);
        assertEquals(0, exitCode);
        assertEquals("one two three four", getFirstLine(stdout));
    }

    @Test
    void testExecuteWithNoNewlineFlagAndNoArgs() throws Exception {
        ListShellOutput stdout = new ListShellOutput();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                java.util.Map.of(), "/", new String[]{"-n"}, null
        );
        EchoCommand echo = new EchoCommand();
        int exitCode = echo.execute(context);
        assertEquals(0, exitCode);
        // -n with no args: hasFlag("n") is true, args.length == 0, noNewline is true
        // the code does: if args.length == 0 and noNewline, nothing is written
        assertTrue(stdout.getChunks().stream().filter(c -> c.isText()).findFirst().isEmpty());
    }
}
