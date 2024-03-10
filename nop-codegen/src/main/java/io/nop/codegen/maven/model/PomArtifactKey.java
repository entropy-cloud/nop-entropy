/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.maven.model;

import io.nop.api.core.util.Guard;

import java.io.Serializable;

public class PomArtifactKey implements Serializable, Comparable<PomArtifactKey> {

    private final String groupId;
    private final String artifactId;

    public PomArtifactKey(String groupId, String artifactId) {
        this.groupId = Guard.notEmpty(groupId, "groupId");
        this.artifactId = Guard.notEmpty(artifactId, "artifactId");
    }

    public String toString() {
        return "ArtifactKey[groupId=" + groupId + ",artifactId=" + artifactId + "]";
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof PomArtifactKey))
            return false;

        PomArtifactKey other = (PomArtifactKey) o;
        return groupId.equals(other.groupId) && artifactId.equals(other.artifactId);
    }

    public int hashCode() {
        return groupId.hashCode() * 31 + artifactId.hashCode();
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    @Override
    public int compareTo(PomArtifactKey o) {
        int cmp = groupId.compareTo(o.groupId);
        if (cmp != 0)
            return cmp;
        return artifactId.compareTo(o.artifactId);
    }
}