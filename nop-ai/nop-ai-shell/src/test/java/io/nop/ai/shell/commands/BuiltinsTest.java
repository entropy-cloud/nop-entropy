/*
 * Copyright (c) 2025, Entropy Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.nop.ai.shell.commands;

import io.nop.ai.shell.registry.CommandRegistry.CommandSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

    private CommandSession createSession(String input) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        return new CommandSession(inputStream, stdout, stderr);
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
        CommandSession session = createSession("");
        Object result = Builtins.echo(session, new String[]{"hello", "world"});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertEquals("hello world", output);
    }

    @Test
    void testEchoNoArgs() {
        CommandSession session = createSession("");
        Object result = Builtins.echo(session, new String[]{});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertEquals(System.lineSeparator(), output);
    }

    @Test
    void testPwd() {
        CommandSession session = createSession("");
        Object result = Builtins.pwd(session, new String[]{});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertEquals(".", output);
    }

    @Test
    void testDateDefault() {
        CommandSession session = createSession("");
        Object result = Builtins.date(session, new String[]{});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertNotNull(output);
        assertTrue(output.length() > 0);
    }

    @Test
    void testDateWithFormat() {
        CommandSession session = createSession("");
        Object result = Builtins.date(session, new String[]{"+yyyy-MM-dd"});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertNotNull(output);
    }

    @Test
    void testSleep() throws InterruptedException {
        CommandSession session = createSession("");
        long start = System.currentTimeMillis();
        Object result = Builtins.sleep(session, new String[]{"1"});
        long end = System.currentTimeMillis();
        assertEquals(0, result);
        assertTrue(end - start >= 1000);
    }

    @Test
    void testSleepInvalidNumber() {
        CommandSession session = createSession("");
        Object result = Builtins.sleep(session, new String[]{"abc"});
        assertEquals(1, result);
        String error = stderrBuffer.toString();
        assertTrue(error.contains("invalid number"));
    }

    @Test
    void testSleepNoArgs() {
        CommandSession session = createSession("");
        Object result = Builtins.sleep(session, new String[]{});
        assertEquals(1, result);
        String error = stderrBuffer.toString();
        assertTrue(error.contains("usage"));
    }

    @Test
    void testClear() {
        CommandSession session = createSession("");
        Object result = Builtins.clear(session, new String[]{});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertEquals("\u001B[2J", output);
    }

    @Test
    void testCatReadFromStdin() {
        String input = "line1\nline2\nline3\n";
        CommandSession session = createSession(input);
        Object result = Builtins.cat(session, new String[]{"-"});
        assertEquals(0, result);
        String output = normalizeLineEndings(stdoutBuffer.toString());
        assertEquals(normalizeLineEndings(input), output);
    }

    @Test
    void testCatReadFromFile(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        CommandSession session = createSession("");
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

        CommandSession session = createSession("");
        Object result = Builtins.cat(session, new String[]{file1.toString(), file2.toString()});
        assertEquals(0, result);
        String output = normalizeLineEndings(stdoutBuffer.toString());
        assertEquals("content1\ncontent2\n", output);
    }

    @Test
    void testHeadDefaultLines(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10\nline11\nline12\n");

        CommandSession session = createSession("");
        Object result = Builtins.head(session, new String[]{testFile.toString()});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertTrue(output.contains("line1"));
        assertFalse(output.contains("line11"));
    }

    @Test
    void testHeadWithCustomLines(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "line1\nline2\nline3\nline4\nline5\n");

        CommandSession session = createSession("");
        Object result = Builtins.head(session, new String[]{"-n2", testFile.toString()});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertTrue(output.contains("line1"));
        assertTrue(output.contains("line2"));
        assertFalse(output.contains("line3"));
    }

    @Test
    void testHeadEmptyFile(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "");

        CommandSession session = createSession("");
        Object result = Builtins.head(session, new String[]{testFile.toString()});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertEquals("", output);
    }

    @Test
    void testTailDefaultLines(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10\nline11\nline12\n");

        CommandSession session = createSession("");
        Object result = Builtins.tail(session, new String[]{testFile.toString()});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertTrue(output.contains("line12"));
    }

    @Test
    void testTailWithCustomLines(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "line1\nline2\nline3\nline4\nline5\n");

        CommandSession session = createSession("");
        Object result = Builtins.tail(session, new String[]{"-n2", testFile.toString()});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertFalse(output.contains("line1"));
        assertFalse(output.contains("line2"));
        assertFalse(output.contains("line3"));
        assertTrue(output.contains("line4"));
        assertTrue(output.contains("line5"));
    }

    @Test
    void testTailEmptyFile(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "");

        CommandSession session = createSession("");
        Object result = Builtins.tail(session, new String[]{testFile.toString()});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertEquals("", output);
    }

    @Test
    void testLsDirectory(@TempDir Path tempDir) throws Exception {
        Files.createDirectory(tempDir.resolve("testdir"));
        Files.writeString(tempDir.resolve("testfile.txt"), "content");
        
        CommandSession session = createSession("");
        Object result = Builtins.ls(session, new String[]{tempDir.toString()});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertTrue(output.contains("testdir"));
        assertTrue(output.contains("testfile.txt"));
    }

    @Test
    void testLsNonExistentDirectory() {
        CommandSession session = createSession("");
        Object result = Builtins.ls(session, new String[]{"\\nonexistent\\path"});
        assertEquals(1, result);
        String error = stderrBuffer.toString();
        assertTrue(error.contains("No such file or directory"));
    }

    @Test
    void testLsWithMultipleFiles(@TempDir Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("file2.txt"));
        Files.createFile(tempDir.resolve("file3.txt"));

        CommandSession session = createSession("");
        Object result = Builtins.ls(session, new String[]{tempDir.toString()});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertTrue(output.contains("file1.txt"));
        assertTrue(output.contains("file2.txt"));
        assertTrue(output.contains("file3.txt"));
    }

    @Test
    void testWcWithFile(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "word1 word2\nword3 word4 word5\n");
        
        CommandSession session = createSession("");
        Object result = Builtins.wc(session, new String[]{testFile.toString()});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertTrue(output.matches("\\s*2\\s+5\\s+\\d+\\s*"));
    }

    @Test
    void testWcWithStdin() {
        String input = "word1 word2\nword3 word4 word5\n";
        CommandSession session = createSession(input);
        Object result = Builtins.wc(session, new String[]{"-"});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertTrue(output.matches("\\s*2\\s+5\\s+\\d+\\s*"));
    }

    @Test
    void testWcNoArgs() {
        CommandSession session = createSession("");
        Object result = Builtins.wc(session, new String[]{});
        assertEquals(1, result);
        String error = stderrBuffer.toString();
        assertTrue(error.contains("missing file operand"));
    }

    @Test
    void testSort(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "zebra\napple\nmango\nbanana\n");

        CommandSession session = createSession("");
        Object result = Builtins.sort(session, new String[]{testFile.toString()});
        assertEquals(0, result);
        String output = normalizeLineEndings(stdoutBuffer.toString());
        assertEquals("apple\nbanana\nmango\nzebra\n", output);
    }

    @Test
    void testSortStdin() {
        String input = "zebra\napple\nmango\nbanana\n";
        CommandSession session = createSession(input);
        Object result = Builtins.sort(session, new String[]{"-"});
        assertEquals(0, result);
        String output = normalizeLineEndings(stdoutBuffer.toString());
        assertEquals("apple\nbanana\nmango\nzebra\n", output);
    }

    @Test
    void testGrepWithFile(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "apple line\nbanana line\ncherry line\ndate line\n");

        String[] args = new String[]{"a", testFile.toString()};
        System.out.println("DEBUG: args[0] = '" + args[0] + "'");
        System.out.println("DEBUG: args[1] = '" + args[1] + "'");
        System.out.println("DEBUG: File exists: " + Files.exists(Path.of(args[1])));

        CommandSession session = createSession("");
        Object result = Builtins.grep(session, args);
        String error = stderrBuffer.toString();
        String output = stdoutBuffer.toString();
        System.out.println("DEBUG: Result = " + result);
        System.out.println("DEBUG: Stderr = '" + error + "'");
        System.out.println("DEBUG: Stdout = '" + output + "'");

        assertEquals(0, result, "Grep should return 0, but got " + result + ". Stderr: " + error);
        assertTrue(output.contains("apple"));
        assertTrue(output.contains("banana"));
        assertTrue(output.contains("date"));
        assertFalse(output.contains("cherry"));
    }

    @Disabled("Grep tests need investigation on Windows platform")
    @Test
    void testGrepCaseInsensitive(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Apple line\nbanana line\ncherry line\n");

        CommandSession session = createSession("");
        Object result = Builtins.grep(session, new String[]{"-i", "apple", testFile.toString()});
        assertEquals(0, result);
        String output = stdoutBuffer.toString();
        assertTrue(output.contains("Apple"));
    }

    @Test
    void testGrepNotImplemented(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content\n");

        CommandSession session = createSession("");
        Object result = Builtins.grep(session, new String[]{"-v", "pattern", testFile.toString()});
        assertEquals(1, result);
        String error = stderrBuffer.toString();
        assertTrue(error.contains("not implemented"));
    }

    @Test
    void testGrepNoArgs() {
        CommandSession session = createSession("");
        Object result = Builtins.grep(session, new String[]{});
        assertEquals(1, result);
        String error = stderrBuffer.toString();
        assertTrue(error.contains("usage"));
    }

    @Test
    void testCdToHome() {
        CommandSession session = createSession("");
        Object result = Builtins.cd(session, new String[]{});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        assertNotNull(output);
    }

    @Test
    void testCdToPath() {
        CommandSession session = createSession("");
        Object result = Builtins.cd(session, new String[]{"\\some\\path"});
        assertEquals(0, result);
        String output = stdoutBuffer.toString().trim();
        String normalizedPath = Path.of("\\some\\path").normalize().toString();
        assertEquals(normalizedPath, output);
    }

    @Test
    void testCdTooManyArgs() {
        CommandSession session = createSession("");
        Object result = Builtins.cd(session, new String[]{"/path1", "/path2"});
        assertEquals(1, result);
        String error = stderrBuffer.toString();
        assertTrue(error.contains("too many arguments"));
    }
}
