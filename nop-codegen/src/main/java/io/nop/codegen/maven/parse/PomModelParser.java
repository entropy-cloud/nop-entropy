/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.maven.parse;

import io.nop.codegen.maven.model.PomArtifactKey;
import io.nop.codegen.maven.model.PomDependencyModel;
import io.nop.codegen.maven.model.PomModel;
import io.nop.codegen.maven.model.PomParentModel;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PomModelParser extends AbstractResourceParser<PomModel> {

    @Override
    protected PomModel doParseResource(IResource resource) {
        XNode node = XNodeParser.instance().parseFromResource(resource);
        PomArtifactKey artifactKey = parseArtifactKey(node);
        String version = node.elementText("version");
        String packaging = node.elementText("packaging");

        PomParentModel parent = parseParent(node.childByTag("parent"));

        List<String> modules = parseModules(node.childByTag("modules"));

        List<PomDependencyModel> dependencies = parseDependencies(node.childByTag("dependencies"));

        PomModel model = new PomModel();
        model.setArtifactKey(artifactKey);
        model.setVersion(version);
        model.setPackaging(packaging);
        model.setVersion(version);
        model.setParent(parent);
        model.setModules(modules);
        model.addDependencies(dependencies);

        return model;
    }

    PomArtifactKey parseArtifactKey(XNode node) {
        String groupId = node.elementText("groupId");
        String artifactId = node.elementText("artifactId");
        if (groupId == null)
            groupId = "unknown";
        if (artifactId == null)
            artifactId = "unknown";
        return new PomArtifactKey(groupId, artifactId);
    }

    PomParentModel parseParent(XNode node) {
        if (node == null)
            return null;

        PomArtifactKey artifactKey = parseArtifactKey(node);
        String version = node.elementText("version");
        String relativePath = node.elementText("relativePath");
        PomParentModel parent = new PomParentModel(artifactKey, version, relativePath);
        return parent;
    }

    List<String> parseModules(XNode node) {
        if (node == null)
            return Collections.emptyList();

        List<String> list = new ArrayList<>(node.getChildCount());
        for (XNode child : node.getChildren()) {
            String value = child.content().asString();
            if (value != null)
                list.add(value);
        }
        return list;
    }

    List<PomDependencyModel> parseDependencies(XNode node) {
        if (node == null)
            return null;

        List<PomDependencyModel> ret = new ArrayList<>(node.getChildCount());
        for (XNode child : node.getChildren()) {
            PomArtifactKey artifactKey = parseArtifactKey(child);
            String version = child.elementText("version");
            PomDependencyModel dep = new PomDependencyModel();
            dep.setArtifactKey(artifactKey);
            dep.setVersion(version);
            ret.add(dep);
        }
        return ret;
    }
}
