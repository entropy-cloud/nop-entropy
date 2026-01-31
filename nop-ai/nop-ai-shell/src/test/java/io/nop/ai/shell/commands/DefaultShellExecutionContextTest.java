package io.nop.ai.shell.commands;

import io.nop.ai.shell.io.ListShellOutput;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultShellExecutionContextTest {

    @Test
    void testConstructor() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of("HOME", "/tmp"), "/home", new String[]{"--verbose"}, null
        );

        assertEquals("/home", context.workingDirectory());
        assertEquals(Map.of("HOME", "/tmp"), context.environment());
        assertNotNull(context.arguments());
        assertEquals(1, context.arguments().length);
        assertEquals("--verbose", context.arguments()[0]);
    }

    @Test
    void testConstructorWithDefaults() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                null, null, null, null
        );

        assertEquals("/", context.workingDirectory());
        assertTrue(context.environment().isEmpty());
        assertEquals(0, context.arguments().length);
    }

    @Test
    void testHasFlagLongFormat() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of(), "/", new String[]{"--verbose", "file.txt"}, null
        );

        assertTrue(context.hasFlag("verbose"));
    }

    @Test
    void testHasFlagShortFormat() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of(), "/", new String[]{"-v", "file.txt"}, null
        );

        assertTrue(context.hasFlag("v"));
    }

    @Test
    void testHasFlagNotFound() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of(), "/", new String[]{"file.txt"}, null
        );

        assertFalse(context.hasFlag("verbose"));
    }

    @Test
    void testGetFlagValue() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of(), "/", new String[]{"--file=test.txt"}, null
        );

        assertEquals("test.txt", context.getFlagValue("file"));
    }

    @Test
    void testGetFlagValueLongFormat() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of(), "/", new String[]{"--output=/tmp/out.txt"}, null
        );

        assertEquals("/tmp/out.txt", context.getFlagValue("output"));
    }

    @Test
    void testGetFlagValueShortFormat() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of(), "/", new String[]{"-v"}, null
        );

        assertEquals("true", context.getFlagValue("v"));
    }

    @Test
    void testGetFlagValueNotFound() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of(), "/", new String[]{"file.txt"}, null
        );

        assertNull(context.getFlagValue("file"));
    }

    @Test
    void testPositionalArguments() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of(), "/", new String[]{"--verbose", "file1.txt", "file2.txt"}, null
        );

        String[] positional = context.positionalArguments();

        assertEquals(2, positional.length);
        assertEquals("file1.txt", positional[0]);
        assertEquals("file2.txt", positional[1]);
    }

    @Test
    void testPositionalArgumentsNone() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of(), "/", new String[]{"--verbose"}, null
        );

        String[] positional = context.positionalArguments();

        assertEquals(0, positional.length);
    }

    @Test
    void testSetWorkingDirectory() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of(), "/home", new String[0], null
        );

        assertEquals("/home", context.workingDirectory());

        context.setWorkingDirectory("/tmp");

        assertEquals("/tmp", context.workingDirectory());
    }

    @Test
    void testEnvironmentImmutable() {
        ListShellOutput stdout = new ListShellOutput();
        DefaultShellExecutionContext context = new DefaultShellExecutionContext(
                null, stdout, stdout,
                Map.of("HOME", "/tmp"), "/", new String[0], null
        );

        Map<String, String> env = context.environment();

        assertThrows(UnsupportedOperationException.class, () -> env.put("NEW", "value"));
    }
}
