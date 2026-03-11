/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.command;

import io.nop.commons.util.FileHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestCallToolsExecutor extends BaseTestCase {

    Path tempDir;

    @BeforeEach
    public void init() {
        this.tempDir = getTargetFile("temp").toPath();
    }

    @Test
    void testWriteFile() throws IOException {
        CallToolsExecutor executor = new CallToolsExecutor();

        String xml = "<call-tools>\n" +
                "  <write-file id=\"1\" path=\"test.txt\">Hello World</write-file>\n" +
                "</call-tools>";

        String result = executor.execute(xml, new CallToolsContext(tempDir.toFile()));

        assertTrue(result.contains("id=\"1\""));
        assertTrue(result.contains("status=\"0\""));

        File testFile = tempDir.resolve("test.txt").toFile();
        assertTrue(testFile.exists());
        assertEquals("Hello World", FileHelper.readText(testFile, null));
    }

    @Test
    void testWriteFileToSubdirectory() throws IOException {
        CallToolsExecutor executor = new CallToolsExecutor();

        String xml = "<call-tools>\n" +
                "  <write-file id=\"1\" path=\"sub/dir/test.txt\">Content in subdir</write-file>\n" +
                "</call-tools>";

        String result = executor.execute(xml, new CallToolsContext(tempDir.toFile()));

        assertTrue(result.contains("status=\"0\""));

        File testFile = tempDir.resolve("sub/dir/test.txt").toFile();
        assertTrue(testFile.exists());
        assertEquals("Content in subdir", FileHelper.readText(testFile, null));
    }

    @Test
    void testWriteFileWithoutId() throws IOException {
        CallToolsExecutor executor = new CallToolsExecutor();

        String xml = "<call-tools>\n" +
                "  <write-file path=\"no-id.txt\">No ID content</write-file>\n" +
                "</call-tools>";

        String result = executor.execute(xml, new CallToolsContext(tempDir.toFile()));

        assertTrue(result.contains("status=\"0\""));
        assertFalse(result.contains("id="));

        File testFile = tempDir.resolve("no-id.txt").toFile();
        assertTrue(testFile.exists());
        assertEquals("No ID content", FileHelper.readText(testFile, null));
    }

    @Test
    void testWriteFileEmptyPath() throws IOException {
        CallToolsExecutor executor = new CallToolsExecutor();

        String xml = "<call-tools>\n" +
                "  <write-file id=\"1\">No path</write-file>\n" +
                "</call-tools>";

        String result = executor.execute(xml, new CallToolsContext(tempDir.toFile()));

        assertTrue(result.contains("status=\"1\""));
        assertTrue(result.contains("error="));
    }

    @Test
    void testPatchFile() throws IOException {
        CallToolsExecutor executor = new CallToolsExecutor();

        Path testFile = tempDir.resolve("patch-test.txt");
        Files.writeString(testFile, "line1\nline2\nline3\n");

        String xml = "<call-tools>\n" +
                "  <patch-file id=\"1\" path=\"patch-test.txt\">\n" +
                "@@ -1,3 +1,3 @@\n" +
                " line1\n" +
                "-line2\n" +
                "+line2-modified\n" +
                " line3\n" +
                "</patch-file>\n" +
                "</call-tools>";

        String result = executor.execute(xml, new CallToolsContext(tempDir.toFile()));

        assertTrue(result.contains("id=\"1\""));
        assertTrue(result.contains("status=\"0\""));

        assertEquals("line1\nline2-modified\nline3\n", FileHelper.readText(testFile.toFile(), null));
    }

    @Test
    void testMultipleCommands() throws IOException {
        CallToolsExecutor executor = new CallToolsExecutor();

        String xml = "<call-tools>\n" +
                "  <write-file id=\"w1\" path=\"file1.txt\">Content 1</write-file>\n" +
                "  <write-file id=\"w2\" path=\"file2.txt\">Content 2</write-file>\n" +
                "</call-tools>";

        String result = executor.execute(xml, new CallToolsContext(tempDir.toFile()));

        assertTrue(result.contains("id=\"w1\""));
        assertTrue(result.contains("id=\"w2\""));
        assertTrue(result.contains("status=\"0\""));

        assertTrue(tempDir.resolve("file1.txt").toFile().exists());
        assertTrue(tempDir.resolve("file2.txt").toFile().exists());
    }
}
