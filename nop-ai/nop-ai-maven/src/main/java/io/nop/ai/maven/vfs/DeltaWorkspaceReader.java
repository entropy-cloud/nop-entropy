package io.nop.ai.maven.vfs;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 支持虚拟文件系统的WorkspaceReader实现
 * <p>
 * 此实现将Maven本地仓库分为两个部分：
 * <ul>
 *   <li>base repository：基础仓库，只读，包含原始artifact</li>
 *   <li>delta repository：增量仓库，可写，覆盖base repository中的artifact</li>
 * </ul>
 * <p>
 * 当查找artifact时，优先从delta仓库查找，如果不存在则从base仓库查找。
 * 当安装artifact时，总是安装到delta仓库。
 * <p>
 * 注意：此实现定义了自己的ArtifactInfo接口，以避免依赖Maven API。
 *
 * @author Nop AI
 */
public class DeltaWorkspaceReader {

    private static final Logger LOG = LoggerFactory.getLogger(DeltaWorkspaceReader.class);

    private static final String REPOSITORY_KEY = "delta-vfs";

    private final String repositoryKey;
    private final DeltaVirtualFileSystem vfs;

    /**
     * 创建DeltaWorkspaceReader
     *
     * @param baseRepoPath  基础仓库路径
     * @param deltaRepoPath 增量仓库路径
     */
    public DeltaWorkspaceReader(File baseRepoPath, File deltaRepoPath) {
        if (baseRepoPath == null) {
            throw new IllegalArgumentException("baseRepoPath cannot be null");
        }
        if (deltaRepoPath == null) {
            throw new IllegalArgumentException("deltaRepoPath cannot be null");
        }

        this.vfs = new DeltaVirtualFileSystem(baseRepoPath, deltaRepoPath);
        this.repositoryKey = REPOSITORY_KEY;

        LOG.info("Initialized DeltaWorkspaceReader with base={}, delta={}",
                baseRepoPath.getAbsolutePath(), deltaRepoPath.getAbsolutePath());
    }

    /**
     * 创建DeltaWorkspaceReader（使用字符串路径）
     *
     * @param baseRepoPath  基础仓库路径
     * @param deltaRepoPath 增量仓库路径
     */
    public DeltaWorkspaceReader(String baseRepoPath, String deltaRepoPath) {
        this(new File(baseRepoPath), new File(deltaRepoPath));
    }

    /**
     * 获取Repository Key
     *
     * @return Repository Key
     */
    public String getRepositoryKey() {
        return repositoryKey;
    }

    /**
     * 查找artifact
     *
     * @param artifact artifact对象（使用自定义接口）
     * @return artifact文件，如果不存在则返回null
     */
    public File findArtifact(ArtifactInfo artifact) {
        if (artifact == null) {
            return null;
        }

        String artifactPath = getArtifactPath(artifact);
        File file = vfs.getFile(artifactPath);

        if (file != null && file.exists()) {
            LOG.debug("Found artifact in virtual FS: {} -> {}",
                    artifact, file.getAbsolutePath());
            return file;
        }

        LOG.debug("Artifact not found in virtual FS: {}", artifact);
        return null;
    }

    /**
     * 查找artifact的所有版本
     *
     * @param artifact artifact对象（使用自定义接口）
     * @return 版本列表
     */
    public List<String> findVersions(ArtifactInfo artifact) {
        if (artifact == null) {
            return Collections.emptyList();
        }

        // 查找所有可用版本
        List<String> versions = new ArrayList<>();

        String groupPath = artifact.getGroupId().replace('.', '/');
        String artifactBasePath = groupPath + "/" + artifact.getArtifactId();

        try {
            // 列出delta仓库中的版本目录
            List<File> deltaVersions = vfs.listFiles(artifactBasePath);
            for (File versionDir : deltaVersions) {
                if (versionDir.isDirectory()) {
                    versions.add(versionDir.getName());
                }
            }

            // 列出base仓库中的版本目录（排除已存在于delta的）
            File baseVersionsPath = new File(vfs.getBaseDir(), artifactBasePath);
            if (baseVersionsPath.exists() && baseVersionsPath.isDirectory()) {
                File[] versionDirs = baseVersionsPath.listFiles(File::isDirectory);
                if (versionDirs != null) {
                    for (File versionDir : versionDirs) {
                        String version = versionDir.getName();
                        if (!versions.contains(version)) {
                            versions.add(version);
                        }
                    }
                }
            }

            LOG.debug("Found {} versions for artifact {}: {}",
                    versions.size(), artifact, versions);
        } catch (Exception e) {
            LOG.warn("Error finding versions for artifact: " + artifact, e);
        }

        return versions;
    }

    /**
     * 将artifact安装到delta仓库
     *
     * @param sourceFile 源artifact文件
     * @param artifact    artifact对象
     * @throws Exception 如果安装失败
     */
    public void installArtifact(File sourceFile, ArtifactInfo artifact) throws Exception {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("sourceFile does not exist: " + sourceFile);
        }
        if (artifact == null) {
            throw new IllegalArgumentException("artifact cannot be null");
        }

        String artifactPath = getArtifactPath(artifact);
        vfs.copyToVirtual(sourceFile, artifactPath);

        LOG.info("Installed artifact to delta repo: {} -> {}",
                artifact, new File(vfs.getDeltaDir(), artifactPath).getAbsolutePath());
    }

    /**
     * 获取artifact在仓库中的相对路径
     *
     * @param artifact artifact对象
     * @return 相对路径
     */
    private String getArtifactPath(ArtifactInfo artifact) {
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();
        String classifier = artifact.getClassifier();
        String extension = artifact.getExtension();

        StringBuilder path = new StringBuilder();

        // groupId路径
        path.append(groupId.replace('.', '/')).append('/');

        // artifactId路径
        path.append(artifactId).append('/');

        // version路径
        path.append(version).append('/');

        // 文件名
        path.append(artifactId).append('-').append(version);

        // classifier
        if (classifier != null && !classifier.isEmpty()) {
            path.append('-').append(classifier);
        }

        // extension
        path.append('.').append(extension);

        return path.toString();
    }

    /**
     * 获取虚拟文件系统
     *
     * @return 虚拟文件系统对象
     */
    public DeltaVirtualFileSystem getVirtualFileSystem() {
        return vfs;
    }

    /**
     * 获取基础仓库路径
     *
     * @return 基础仓库路径
     */
    public File getBaseRepoPath() {
        return vfs.getBaseDir();
    }

    /**
     * 获取增量仓库路径
     *
     * @return 增量仓库路径
     */
    public File getDeltaRepoPath() {
        return vfs.getDeltaDir();
    }
}
