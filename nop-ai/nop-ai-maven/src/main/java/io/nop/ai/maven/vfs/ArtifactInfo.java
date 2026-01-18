package io.nop.ai.maven.vfs;

/**
 * Artifact信息类
 * <p>
 * 封装Maven artifact的基本信息，避免依赖Maven API。
 *
 * @author Nop AI
 */
public class ArtifactInfo {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final String extension;

    /**
     * 创建Artifact信息
     *
     * @param groupId    groupId
     * @param artifactId artifactId
     * @param version    version
     * @param classifier classifier（可以为null）
     * @param extension  扩展名（默认为"jar"）
     */
    public ArtifactInfo(String groupId, String artifactId, String version,
                       String classifier, String extension) {
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("groupId cannot be null or empty");
        }
        if (artifactId == null || artifactId.trim().isEmpty()) {
            throw new IllegalArgumentException("artifactId cannot be null or empty");
        }
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("version cannot be null or empty");
        }

        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.extension = extension != null ? extension : "jar";
    }

    /**
     * 创建Artifact信息（无classifier，默认jar）
     *
     * @param groupId    groupId
     * @param artifactId artifactId
     * @param version    version
     */
    public ArtifactInfo(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, null, "jar");
    }

    /**
     * 创建Artifact信息（有classifier）
     *
     * @param groupId    groupId
     * @param artifactId artifactId
     * @param version    version
     * @param classifier classifier
     */
    public ArtifactInfo(String groupId, String artifactId, String version, String classifier) {
        this(groupId, artifactId, version, classifier, "jar");
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getExtension() {
        return extension;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId).append(':');
        sb.append(artifactId).append(':');
        sb.append(version);

        if (classifier != null && !classifier.isEmpty()) {
            sb.append(':').append(classifier);
        }

        sb.append(':').append(extension);
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        ArtifactInfo that = (ArtifactInfo) obj;

        if (!groupId.equals(that.groupId)) {
            return false;
        }
        if (!artifactId.equals(that.artifactId)) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }
        if (classifier != null ? !classifier.equals(that.classifier) : that.classifier != null) {
            return false;
        }
        return extension.equals(that.extension);
    }

    @Override
    public int hashCode() {
        int result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
        result = 31 * result + extension.hashCode();
        return result;
    }
}
