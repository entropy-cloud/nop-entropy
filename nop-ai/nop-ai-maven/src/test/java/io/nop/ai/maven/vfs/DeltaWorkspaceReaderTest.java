package io.nop.ai.maven.vfs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 * DeltaWorkspaceReader 单元测试
 *
 * @author Nop AI
 */
public class DeltaWorkspaceReaderTest {

    private File baseRepo;
    private File deltaRepo;
    private DeltaWorkspaceReader reader;

    @Before
    public void setUp() throws IOException {
        // 创建临时目录
        Path basePath = Files.createTempDirectory("repo-base-");
        Path deltaPath = Files.createTempDirectory("repo-delta-");

        baseRepo = basePath.toFile();
        deltaRepo = deltaPath.toFile();

        // 在base仓库中创建一些测试artifact
        createArtifact(baseRepo, "com.example", "test-artifact", "1.0.0", "jar");
        createArtifact(baseRepo, "com.example", "test-artifact", "2.0.0", "jar");
        createArtifact(baseRepo, "org.test", "lib", "1.0.0", "jar");

        // 创建WorkspaceReader
        reader = new DeltaWorkspaceReader(baseRepo, deltaRepo);
    }

    @After
    public void tearDown() throws IOException {
        // 清理临时目录
        deleteDirectory(baseRepo);
        deleteDirectory(deltaRepo);
    }

    @Test
    public void testFindArtifactFromBase() {
        ArtifactInfo artifact = new ArtifactInfo("com.example", "test-artifact", "1.0.0");
        File file = reader.findArtifact(artifact);

        assertNotNull(file);
        assertTrue(file.exists());
        assertTrue(file.getAbsolutePath().contains(baseRepo.getAbsolutePath()));
    }

    @Test
    public void testFindArtifactFromDelta() throws IOException {
        // 在delta仓库中创建同名artifact
        ArtifactInfo artifact = new ArtifactInfo("com.example", "test-artifact", "1.0.0");
        createArtifact(deltaRepo, "com.example", "test-artifact", "1.0.0", "jar");

        File file = reader.findArtifact(artifact);

        assertNotNull(file);
        assertTrue(file.exists());
        assertTrue(file.getAbsolutePath().contains(deltaRepo.getAbsolutePath()));
    }

    @Test
    public void testFindArtifactNotExists() {
        ArtifactInfo artifact = new ArtifactInfo("com.example", "nonexistent", "1.0.0");
        File file = reader.findArtifact(artifact);

        assertNull(file);
    }

    @Test
    public void testFindVersions() {
        ArtifactInfo artifact = new ArtifactInfo("com.example", "test-artifact", "1.0.0");
        List<String> versions = reader.findVersions(artifact);

        assertNotNull(versions);
        assertTrue(versions.contains("1.0.0"));
        assertTrue(versions.contains("2.0.0"));
    }

    @Test
    public void testFindVersionsWithDeltaOverride() throws IOException {
        // 在delta仓库中创建一个新版本
        createArtifact(deltaRepo, "com.example", "test-artifact", "3.0.0", "jar");

        ArtifactInfo artifact = new ArtifactInfo("com.example", "test-artifact", "1.0.0");
        List<String> versions = reader.findVersions(artifact);

        assertNotNull(versions);
        assertTrue(versions.contains("1.0.0"));
        assertTrue(versions.contains("2.0.0"));
        assertTrue(versions.contains("3.0.0"));
    }

    @Test
    public void testInstallArtifact() throws Exception {
        // 创建一个临时artifact文件
        Path tempFile = Files.createTempFile("artifact-", ".jar");
        Files.write(tempFile, "Test artifact content".getBytes());

        ArtifactInfo artifact = new ArtifactInfo("com.example", "new-artifact", "1.0.0");

        // 安装artifact
        reader.installArtifact(tempFile.toFile(), artifact);

        // 验证artifact已安装到delta仓库
        File installedFile = reader.findArtifact(artifact);
        assertNotNull(installedFile);
        assertTrue(installedFile.exists());
        assertTrue(installedFile.getAbsolutePath().contains(deltaRepo.getAbsolutePath()));

        // 验证内容
        String content = new String(Files.readAllBytes(installedFile.toPath()));
        assertEquals("Test artifact content", content);

        // 清理
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testGetRepositoryKey() {
        String key = reader.getRepositoryKey();
        assertNotNull(key);
        assertEquals("delta-vfs", key);
    }

    @Test
    public void testGetVirtualFileSystem() {
        assertNotNull(reader.getVirtualFileSystem());
    }

    @Test
    public void testGetBaseRepoPath() {
        assertEquals(baseRepo, reader.getBaseRepoPath());
    }

    @Test
    public void testGetDeltaRepoPath() {
        assertEquals(deltaRepo, reader.getDeltaRepoPath());
    }

    @Test
    public void testArtifactInfoWithClassifier() throws IOException {
        // 创建带classifier的artifact
        ArtifactInfo artifact = new ArtifactInfo("com.example", "test-artifact", "1.0.0", "sources");
        createArtifactWithClassifier(baseRepo, "com.example", "test-artifact", "1.0.0", "sources", "jar");

        File file = reader.findArtifact(artifact);
        assertNotNull(file);
        assertTrue(file.exists());
    }

    @Test
    public void testArtifactInfoWithDifferentExtension() throws IOException {
        // 创建不同扩展名的artifact
        ArtifactInfo artifact = new ArtifactInfo("com.example", "test-artifact", "1.0.0", null, "pom");
        createArtifact(baseRepo, "com.example", "test-artifact", "1.0.0", "pom");

        File file = reader.findArtifact(artifact);
        assertNotNull(file);
        assertTrue(file.exists());
        assertTrue(file.getName().endsWith(".pom"));
    }

    // ========== 辅助方法 ==========

    private void createArtifact(File repo, String groupId, String artifactId,
                               String version, String extension) throws IOException {
        createArtifactWithClassifier(repo, groupId, artifactId, version, null, extension);
    }

    private void createArtifactWithClassifier(File repo, String groupId, String artifactId,
                                             String version, String classifier, String extension) throws IOException {
        String groupPath = groupId.replace('.', '/');
        File artifactDir = new File(repo, groupPath + "/" + artifactId + "/" + version);

        Files.createDirectories(artifactDir.toPath());

        StringBuilder fileName = new StringBuilder();
        fileName.append(artifactId).append('-').append(version);

        if (classifier != null && !classifier.isEmpty()) {
            fileName.append('-').append(classifier);
        }

        fileName.append('.').append(extension);

        File artifactFile = new File(artifactDir, fileName.toString());
        Files.write(artifactFile.toPath(),
                ("Artifact: " + groupId + ":" + artifactId + ":" + version).getBytes());
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
