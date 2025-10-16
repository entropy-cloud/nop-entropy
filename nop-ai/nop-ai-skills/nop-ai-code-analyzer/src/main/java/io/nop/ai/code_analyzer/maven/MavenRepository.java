package io.nop.ai.code_analyzer.maven;

import io.nop.api.core.util.Guard;

import java.io.File;
import java.nio.file.Paths;

public class MavenRepository {
    private final File repoDir;

    public MavenRepository(File repoDir) {
        this.repoDir = Guard.notNull(repoDir, "repoDir");
    }

    public static MavenRepository getDefault() {
        return new MavenRepository(new File(getDefaultMavenRepository()));
    }

    public File getRepoDir() {
        return repoDir;
    }

    public File getArtifactDir(String groupId, String artifactId, String version) {
        return new File(repoDir, groupId.replace('.', '/') + "/" + artifactId + "/" + version);
    }

    public File getArtifactFile(String groupId, String artifactId, String version, String classifier, String extension) {
        return new File(getArtifactDir(groupId, artifactId, version), artifactId + "-" + version + (classifier == null ? "" : "-" + classifier) + "." + extension);
    }

    public File getArtifactFile(String groupId, String artifactId, String version, String extension) {
        return getArtifactFile(groupId, artifactId, version, null, extension);
    }

    public File getMavenDependencyFile(MavenDependency dep) {
        return getArtifactFile(dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), dep.getClassifier(), dep.getType());
    }

    /**
     * 获取本地Maven仓库默认路径
     *
     * @return 本地Maven仓库路径字符串
     */
    public static String getDefaultMavenRepository() {
        // 1. 检查系统属性maven.repo.local（最高优先级）
        String customRepo = System.getProperty("maven.repo.local");
        if (customRepo != null) {
            return customRepo;
        }

        // 2. 检查环境变量M2_REPO（旧版Maven使用）
        String envRepo = System.getenv("M2_REPO");
        if (envRepo != null) {
            return envRepo;
        }

        // 3. 默认路径：用户主目录下的.m2/repository
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".m2", "repository").toString();
    }
}
