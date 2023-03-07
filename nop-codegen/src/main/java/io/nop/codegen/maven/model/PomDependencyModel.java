/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen.maven.model;

import java.io.Serializable;

public class PomDependencyModel implements Serializable {
    private PomArtifactKey artifactKey;
    private String version;

    public PomArtifactKey getArtifactKey() {
        return artifactKey;
    }

    public String getGroupId() {
        return artifactKey.getGroupId();
    }

    public String getArtifactId() {
        return artifactKey.getArtifactId();
    }

    public void setArtifactKey(PomArtifactKey artifactKey) {
        this.artifactKey = artifactKey;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
