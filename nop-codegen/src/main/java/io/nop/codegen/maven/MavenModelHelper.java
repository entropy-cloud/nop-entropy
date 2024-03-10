/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.maven;

import io.nop.codegen.maven.model.PomArtifactKey;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;

import java.io.File;

public class MavenModelHelper {
    public static PomArtifactKey getProjectArtifactKey(File projectDir) {
        File pomFile = new File(projectDir, "pom.xml");
        if (!pomFile.exists())
            return null;

        XNode node = XNodeParser.instance().parseFromResource(new FileResource(pomFile));
        String groupId = node.elementText("groupId");
        String artifactId = node.elementText("artifactId");
        if (StringHelper.isEmpty(groupId)) {
            XNode parent = node.childByTag("parent");
            if (parent != null) {
                groupId = parent.elementText("groupId");
            }
        }
        if (StringHelper.isEmpty(groupId))
            groupId = "unknown";
        if (StringHelper.isEmpty(artifactId))
            artifactId = "unknown";

        return new PomArtifactKey(groupId, artifactId);
    }
}
