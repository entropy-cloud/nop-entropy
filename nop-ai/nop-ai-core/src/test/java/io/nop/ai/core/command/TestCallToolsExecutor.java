/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.command;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true)
class TestCallToolsExecutor extends JunitBaseTestCase {

    @BeforeAll
    public static void initCore() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroyCore() {
        CoreInitialization.destroy();
    }

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
    void testApplyDeltaDryRun() throws IOException {
        CallToolsExecutor executor = new CallToolsExecutor();

        Path testFile = tempDir.resolve("auth-service.beans.xml");
        Files.writeString(testFile,
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
                        + "<beans x:schema=\"/nop/schema/beans.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\" xmlns:ioc=\"ioc\">\n"
                        + "    <bean id=\"baseBean\" class=\"demo.BaseBean\"/>\n"
                        + "</beans>\n");

        String original = FileHelper.readText(testFile.toFile(), null);

        String xml = "<call-tools>\n"
                + "  <apply-delta id=\"1\" path=\"auth-service.beans.xml\" dryRun=\"true\">\n"
                + "    <deltaContent>\n"
                + "      <beans>\n"
                + "        <bean id=\"extraBean\" class=\"demo.ExtraBean\"/>\n"
                + "      </beans>\n"
                + "    </deltaContent>\n"
                + "  </apply-delta>\n"
                + "</call-tools>";

        String result = executor.execute(xml, new CallToolsContext(tempDir.toFile()));

        assertTrue(result.contains("id=\"1\""));
        assertTrue(result.contains("status=\"0\""));
        assertTrue(result.contains("extraBean"));
        assertEquals(original, FileHelper.readText(testFile.toFile(), null));
    }

    @Test
    void testApplyDeltaUpdateFile() throws IOException {
        CallToolsExecutor executor = new CallToolsExecutor();

        Path testFile = tempDir.resolve("auth-service.beans.xml");
        Path deltaFile = tempDir.resolve("auth-service.delta.xml");
        Files.writeString(testFile,
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
                        + "<beans x:schema=\"/nop/schema/beans.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\" xmlns:ioc=\"ioc\">\n"
                        + "    <bean id=\"baseBean\" class=\"demo.BaseBean\"/>\n"
                        + "</beans>\n");
        Files.writeString(deltaFile,
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
                        + "<beans x:schema=\"/nop/schema/beans.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\" xmlns:ioc=\"ioc\" x:extends=\"super\">\n"
                        + "    <bean id=\"extraBean\" class=\"demo.ExtraBean\"/>\n"
                        + "</beans>\n");

        String xml = "<call-tools>\n"
                + "  <apply-delta id=\"2\" path=\"auth-service.beans.xml\" deltaPath=\"auth-service.delta.xml\"/>\n"
                + "</call-tools>";

        String result = executor.execute(xml, new CallToolsContext(tempDir.toFile()));

        assertTrue(result.contains("id=\"2\""));
        assertTrue(result.contains("status=\"0\""));
        assertTrue(result.contains("Delta applied successfully"));

        String merged = FileHelper.readText(testFile.toFile(), null);
        assertTrue(merged.contains("baseBean"));
        assertTrue(merged.contains("extraBean"));
        assertFalse(merged.contains("x:extends="));
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
