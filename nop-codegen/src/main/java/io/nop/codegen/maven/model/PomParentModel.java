/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.maven.model;

import java.io.Serializable;

public class PomParentModel implements Serializable {
    final PomArtifactKey artifactKey;
    final String relativePath;
    final String version;

    public PomParentModel(PomArtifactKey artifactKey, String version, String relativePath) {
        this.artifactKey = artifactKey;
        this.version = version;
        this.relativePath = relativePath;
    }

    public String getGroupId() {
        return artifactKey.getGroupId();
    }

    public String getArtifactId() {
        return artifactKey.getArtifactId();
    }

    public PomArtifactKey getArtifactKey() {
        return artifactKey;
    }

    public String getRelativePath() {
        return relativePath;
    }
}
