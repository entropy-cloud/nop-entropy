package io.nop.cli.commands;

import io.nop.ai.toolkit.api.IToolManager;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.FileHelper;
import io.nop.core.resource.IFile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = false)
public class TestCliCallToolsCommand extends JunitBaseTestCase {

    @Inject
    IToolManager toolManager;

    private File tempDir;
    private CliCallToolsCommand command;

    @BeforeEach
    void setUp() {
        tempDir = getTargetResource("temp").toFile();
        FileHelper.deleteAll(tempDir);
        tempDir.mkdirs();
        command = new CliCallToolsCommand();
    }

    @Test
    void testToolManagerInjected() {
        assertNotNull(toolManager, "IToolManager should be injected by IoC container");
    }

    @Test
    void testListTools() {
        var tools = toolManager.listTools();
        assertNotNull(tools, "listTools should return non-null");
        assertFalse(tools.isEmpty(), "Should have some tools registered");
    }

    @Test
    void testWriteAndReadFile() {
        File testFile = new File(tempDir, "test.txt");
        String xmlContent = "<call-tools>\n" +
                "  <write-file id=\"1\" path=\"test.txt\">\n" +
                "    <input>Hello World</input>\n" +
                "  </write-file>\n" +
                "</call-tools>";

        command.baseDir = tempDir;
        command.xmlContent = xmlContent;

        Integer result = command.call();
        assertEquals(0, result, "Command should succeed");

        assertTrue(testFile.exists(), "File should be created");
        String content = FileHelper.readText(testFile, null);
        assertEquals("Hello World", content);
    }

    @Test
    void testCreateDirectory() {
        File testDir = new File(tempDir, "subdir");
        String xmlContent = "<call-tools>\n" +
                "  <create-dir id=\"1\" path=\"subdir\"/>\n" +
                "</call-tools>";

        command.baseDir = tempDir;
        command.xmlContent = xmlContent;

        Integer result = command.call();
        assertEquals(0, result, "Command should succeed");

        assertTrue(testDir.exists() && testDir.isDirectory(), "Directory should be created");
    }

    @Test
    void testMultipleOperations() {
        File testFile = new File(tempDir, "multi-test.txt");
        String xmlContent = "<call-tools>\n" +
                "  <write-file id=\"1\" path=\"multi-test.txt\">\n" +
                "    <input>Line 1\nLine 2\nLine 3</input>\n" +
                "  </write-file>\n" +
                "  <read-file id=\"2\" path=\"multi-test.txt\"/>\n" +
                "</call-tools>";

        command.baseDir = tempDir;
        command.xmlContent = xmlContent;

        Integer result = command.call();
        assertEquals(0, result, "Command should succeed");
    }

    @Test
    void testListDirectory() {
        File subDir = new File(tempDir, "list-test");
        subDir.mkdirs();
        File testFile = new File(subDir, "test.txt");
        FileHelper.writeText(testFile, "test", null);

        String xmlContent = "<call-tools>\n" +
                "  <list-dir id=\"1\" path=\"list-test\"/>\n" +
                "</call-tools>";

        command.baseDir = tempDir;
        command.xmlContent = xmlContent;

        Integer result = command.call();
        assertEquals(0, result, "Command should succeed");
    }

    @Test
    void testCopyFile() {
        File srcFile = new File(tempDir, "source.txt");
        File dstFile = new File(tempDir, "dest.txt");
        FileHelper.writeText(srcFile, "source content", null);

        String xmlContent = "<call-tools>\n" +
                "  <copy-file id=\"1\" source=\"source.txt\" target=\"dest.txt\"/>\n" +
                "</call-tools>";

        command.baseDir = tempDir;
        command.xmlContent = xmlContent;

        Integer result = command.call();
        assertEquals(0, result, "Command should succeed");

        assertTrue(dstFile.exists(), "Destination file should exist");
        assertEquals("source content", FileHelper.readText(dstFile, null));
    }

    @Test
    void testMoveFile() {
        File srcFile = new File(tempDir, "to-move.txt");
        File dstFile = new File(tempDir, "moved.txt");
        FileHelper.writeText(srcFile, "move content", null);

        String xmlContent = "<call-tools>\n" +
                "  <move-file id=\"1\" source=\"to-move.txt\" target=\"moved.txt\"/>\n" +
                "</call-tools>";

        command.baseDir = tempDir;
        command.xmlContent = xmlContent;

        Integer result = command.call();
        assertEquals(0, result, "Command should succeed");

        assertFalse(srcFile.exists(), "Source file should not exist");
        assertTrue(dstFile.exists(), "Destination file should exist");
    }

    @Test
    void testDeleteFile() {
        File delFile = new File(tempDir, "to-delete.txt");
        FileHelper.writeText(delFile, "delete me", null);

        String xmlContent = "<call-tools>\n" +
                "  <delete-file id=\"1\" path=\"to-delete.txt\"/>\n" +
                "</call-tools>";

        command.baseDir = tempDir;
        command.xmlContent = xmlContent;

        Integer result = command.call();
        assertEquals(0, result, "Command should succeed");

        assertFalse(delFile.exists(), "File should be deleted");
    }
}
