package io.nop.ai.maven.vfs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 * DeltaVirtualFileSystem 单元测试
 *
 * @author Nop AI
 */
public class DeltaVirtualFileSystemTest {

    private File baseDir;
    private File deltaDir;
    private DeltaVirtualFileSystem vfs;

    @Before
    public void setUp() throws IOException {
        // 创建临时目录
        Path basePath = Files.createTempDirectory("vfs-base-");
        Path deltaPath = Files.createTempDirectory("vfs-delta-");

        baseDir = basePath.toFile();
        deltaDir = deltaPath.toFile();

        // 在base目录中创建一些测试文件
        createFile(baseDir, "test/BaseFile.txt", "Base content");
        createFile(baseDir, "src/Main.java", "public class Main {}");
        createFile(baseDir, "config/settings.xml", "<settings/>");

        // 创建虚拟文件系统
        vfs = new DeltaVirtualFileSystem(baseDir, deltaDir);
    }

    @After
    public void tearDown() throws IOException {
        // 清理临时目录
        deleteDirectory(baseDir);
        deleteDirectory(deltaDir);
    }

    @Test
    public void testGetFileFromBase() {
        File file = vfs.getFile("test/BaseFile.txt");
        assertNotNull(file);
        assertTrue(file.exists());
        assertEquals(baseDir, file.getParentFile().getParentFile());
    }

    @Test
    public void testGetFileFromDelta() throws IOException {
        // 在delta目录中创建同名文件
        createFile(deltaDir, "test/BaseFile.txt", "Delta content");

        File file = vfs.getFile("test/BaseFile.txt");
        assertNotNull(file);
        assertTrue(file.exists());
        assertEquals(deltaDir, file.getParentFile().getParentFile());
    }

    @Test
    public void testGetFileNotExists() {
        File file = vfs.getFile("nonexistent/file.txt");
        assertNull(file);
    }

    @Test
    public void testExists() {
        assertTrue(vfs.exists("test/BaseFile.txt"));
        assertFalse(vfs.exists("nonexistent/file.txt"));
    }

    @Test
    public void testGetInputStreamFromBase() throws IOException {
        try (InputStream is = vfs.getInputStream("test/BaseFile.txt")) {
            assertNotNull(is);
            byte[] content = new byte[is.available()];
            is.read(content);
            assertEquals("Base content", new String(content));
        }
    }

    @Test
    public void testGetInputStreamFromDelta() throws IOException {
        // 在delta目录中创建同名文件
        createFile(deltaDir, "test/BaseFile.txt", "Delta content");

        try (InputStream is = vfs.getInputStream("test/BaseFile.txt")) {
            assertNotNull(is);
            byte[] content = new byte[is.available()];
            is.read(content);
            assertEquals("Delta content", new String(content));
        }
    }

    @Test
    public void testWriteToDelta() throws IOException {
        java.io.OutputStream os = vfs.getOutputStream("new/NewFile.txt");
        assertNotNull(os);
        os.write("New content".getBytes());
        os.close();

        // 验证文件在delta目录中
        File deltaFile = new File(deltaDir, "new/NewFile.txt");
        assertTrue(deltaFile.exists());

        // 验证可以通过vfs读取
        File file = vfs.getFile("new/NewFile.txt");
        assertNotNull(file);
        assertTrue(file.exists());
        assertEquals(deltaFile, file);
    }

    @Test
    public void testCopyToVirtual() throws IOException {
        // 创建一个临时源文件
        File sourceFile = Files.createTempFile("source-", ".txt").toFile();
        Files.write(sourceFile.toPath(), "Source content".getBytes());

        vfs.copyToVirtual(sourceFile, "copied/CopiedFile.txt");

        // 验证文件在delta目录中
        File copiedFile = new File(deltaDir, "copied/CopiedFile.txt");
        assertTrue(copiedFile.exists());
        assertEquals("Source content", new String(Files.readAllBytes(copiedFile.toPath())));

        // 清理临时文件
        Files.deleteIfExists(sourceFile.toPath());
    }

    @Test
    public void testDeleteFile() throws IOException {
        // 在delta目录中创建文件
        createFile(deltaDir, "test/ToDelete.txt", "To delete");

        // 删除文件
        vfs.deleteFile("test/ToDelete.txt");

        // 验证文件已删除
        File deletedFile = new File(deltaDir, "test/ToDelete.txt");
        assertFalse(deletedFile.exists());

        // 验证base目录中的同名文件仍存在
        File baseFile = new File(baseDir, "test/BaseFile.txt");
        assertTrue(baseFile.exists());
    }

    @Test
    public void testListFiles() {
        List<File> files = vfs.listFiles("test");
        assertNotNull(files);

        // 至少应该有base目录中的文件
        assertTrue(files.size() >= 1);
        assertTrue(files.stream().anyMatch(f -> f.getName().equals("BaseFile.txt")));
    }

    @Test
    public void testNormalizePath() {
        // 测试路径标准化
        File file1 = vfs.getFile("/test/BaseFile.txt");
        File file2 = vfs.getFile("test/BaseFile.txt");
        assertEquals(file1, file2);

        File file3 = vfs.getFile("\\test\\BaseFile.txt");
        assertEquals(file1, file3);
    }

    @Test
    public void testPriority() throws IOException {
        // 在delta目录中创建同名文件
        createFile(deltaDir, "test/BaseFile.txt", "Delta content");

        File file = vfs.getFile("test/BaseFile.txt");

        // 应该返回delta目录中的文件
        assertNotNull(file);
        assertEquals(deltaDir, file.getParentFile().getParentFile());
    }

    // ========== 辅助方法 ==========

    private void createFile(File parentDir, String relativePath, String content) throws IOException {
        File file = new File(parentDir, relativePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            Files.createDirectories(parent.toPath());
        }
        Files.write(file.toPath(), content.getBytes());
    }

    private void deleteDirectory(File directory) throws IOException {
        if (directory == null || !directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    Files.deleteIfExists(file.toPath());
                }
            }
        }

        Files.deleteIfExists(directory.toPath());
    }
}
