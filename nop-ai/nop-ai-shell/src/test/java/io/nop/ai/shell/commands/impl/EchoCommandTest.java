package io.nop.ai.shell.commands.impl;

import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.io.ListShellOutput;
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
        assertEquals("hello world", stdout.getList().get(0));
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
        assertEquals("hello", stdout.getList().get(0));
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
        assertEquals(1, stdout.getList().size());
        assertEquals("", stdout.getList().get(0));
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
        assertEquals(0, stdout.getList().size());
        stdout.close();
        assertEquals("hello", stdout.getList().get(0));
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
        assertEquals(1, stdout.getList().size());
        assertEquals("hello", stdout.getList().get(0));
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
        assertEquals(1, stdout.getList().size());
        assertEquals("one two three four", stdout.getList().get(0));
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
        assertTrue(stdout.getList().isEmpty());
    }
}
