package io.nop.ai.code_analyzer.maven;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.ImmutableBean;

import java.util.Objects;

@ImmutableBean
public class MavenDependency {
    private final String groupId;

    private final String artifactId;

    private final String type;

    private final String version;

    private final String scope;

    private final String classifier;

    @JsonCreator
    public MavenDependency(
            @JsonProperty("groupId") String groupId,
            @JsonProperty("artifactId") String artifactId,
            @JsonProperty("type") String type,
            @JsonProperty("version") String version,
            @JsonProperty("scope") String scope,
            @JsonProperty("classifier") String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.type = type;
        this.version = version;
        this.scope = scope;
        this.classifier = classifier;
    }

    public String getModuleId() {
        return groupId + ":" + artifactId;
    }

    // Getters (保持原有不变)
    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getType() {
        return type;
    }

    public String getVersion() {
        return version;
    }

    public String getScope() {
        return scope;
    }

    public String getClassifier() {
        return classifier;
    }

    @Override
    public String toString() {
        // 保持原有toString实现
        StringBuilder sb = new StringBuilder();
        sb.append(groupId).append(":")
                .append(artifactId).append(":")
                .append(type).append(":")
                .append(version);

        if (scope != null) {
            sb.append(":").append(scope);
        }
        if (classifier != null) {
            sb.append(":").append(classifier);
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        // 保持原有equals实现
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MavenDependency that = (MavenDependency) o;
        return Objects.equals(groupId, that.groupId) &&
                Objects.equals(artifactId, that.artifactId) &&
                Objects.equals(type, that.type) &&
                Objects.equals(version, that.version) &&
                Objects.equals(scope, that.scope) &&
                Objects.equals(classifier, that.classifier);
    }

    @Override
    public int hashCode() {
        // 保持原有hashCode实现
        return Objects.hash(groupId, artifactId, type, version, scope, classifier);
    }
}