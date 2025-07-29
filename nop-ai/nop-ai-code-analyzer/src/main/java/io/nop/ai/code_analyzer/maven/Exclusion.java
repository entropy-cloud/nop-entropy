package io.nop.ai.code_analyzer.maven;

import io.nop.api.core.annotations.data.DataBean;

// 排除依赖类
@DataBean
public class Exclusion {
    private String groupId;
    private String artifactId;

    public Exclusion(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    // getter和setter方法
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }
}