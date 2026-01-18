package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinsTest {

    private ByteArrayOutputStream stdoutBuffer;
    private ByteArrayOutputStream stderrBuffer;
    private PrintStream stdout;
    private PrintStream stderr;

    @BeforeEach
    void setUp() {
        resetBuffers();
    }

    @AfterEach
    void tearDown() {
    }

    private void resetBuffers() {
        stdoutBuffer = new ByteArrayOutputStream();
        stderrBuffer = new ByteArrayOutputStream();
        stdout = new PrintStream(stdoutBuffer);
        stderr = new PrintStream(stderrBuffer);
    }

    private CommandRegistry.CommandSession createSession(String input) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        return new CommandRegistry.CommandSession(inputStream, stdout, stderr);
    }

    private String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private void createFruitFile(Path tempDir, String name) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "apple\nbanana\ncherry\ndate\nelderberry\n");
    }

    @Test
    void testEcho() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.echo(session, new String[]{"hello", "world"});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertEquals("hello world", output);
    }

    @Test
    void testEchoNoArgs() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.echo(session, new String[]{});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertEquals(System.lineSeparator(), output);
    }

    @Test
    void testPwd() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.pwd(session, new String[]{});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertEquals(".", output);
    }

    @Test
    void testDateDefault() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.date(session, new String[]{});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertNotNull(output);
        assertTrue(output.length() > 0);
    }

    @Test
    void testDateWithFormat() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.date(session, new String[]{"+yyyy-MM-dd"});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertNotNull(output);
    }

    @Test
    void testSleep() throws InterruptedException {
        long start = System.currentTimeMillis();
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.sleep(session, new String[]{"1"});
        long end = System.currentTimeMillis();
        assertEquals(0, result);
        assertTrue(end - start >= 1000);
    }

    @Test
    void testSleepInvalidNumber() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.sleep(session, new String[]{"abc"});
        assertEquals(1, result);
        String error = stderrBuffer.toString();
        assertTrue(error.contains("invalid number"));
    }

    @Test
    void testSleepNoArgs() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.sleep(session, new String[]{});
        assertEquals(1, result);
        String error = stderrBuffer.toString();
        assertTrue(error.contains("usage"));
    }

    @Test
    void testClear() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.clear(session, new String[]{});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertEquals("\u001B[2J", output);
    }

    @Test
    void testCatReadFromStdin() {
        String input = "line1\nline2\nline3";
        CommandRegistry.CommandSession session = createSession(input);
        Object result = Builtins.cat(session, new String[]{"-"});
        assertEquals(0, result);
        String output = normalizeLineEndings(stdoutBuffer.toString());
        assertEquals("line1\nline2\nline3\n", output);
    }

    @Test
    void testCatReadFromFile(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.cat(session, new String[]{testFile.toString()});
        assertEquals(0, result);
        String output = normalizeLineEndings(stdoutBuffer.toString());
        assertEquals("test content\n", output);
    }

    @Test
    void testCatMultipleFiles(@TempDir Path tempDir) throws Exception {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");

        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.cat(session, new String[]{file1.toString(), file2.toString()});
        assertEquals(0, result);
        String output = normalizeLineEndings(stdoutBuffer.toString());
        assertEquals("content1\ncontent2\n", output);
    }

    @Test
    void testLsDirectory(@TempDir Path tempDir) throws Exception {
        Files.createDirectory(tempDir.resolve("testdir"));
        Files.writeString(tempDir.resolve("testfile.txt"), "content");

        CommandRegistry.CommandSession session = createSession("");
        Object result = new LsCommand().execute(session, new String[]{tempDir.toString()});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertTrue(output.contains("testdir"));
        assertTrue(output.contains("testfile.txt"));
    }

    @Test
    void testLsNonExistentDirectory() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = new LsCommand().execute(session, new String[]{"\\nonexistent\\path"});
        assertEquals(1, result);
        String error = stderrBuffer.toString();
        assertTrue(error.contains("No such file or directory"));
    }

    @Test
    void testCdToHome() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.cd(session, new String[]{});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertNotNull(output);
    }

    @Test
    void testCdToPath() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.cd(session, new String[]{"\\some\\path"});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        Path normalizedPath = Path.of("\\some\\path").normalize();
        assertEquals(normalizedPath.toString(), output);
    }

    @Test
    void testCdTooManyArgs() {
        CommandRegistry.CommandSession session = createSession("");
        Object result = Builtins.cd(session, new String[]{"\\path1", "\\path2"});
        assertEquals(1, result);
        String error = stderrBuffer.toString();
        assertTrue(error.contains("too many arguments"));
    }
}
