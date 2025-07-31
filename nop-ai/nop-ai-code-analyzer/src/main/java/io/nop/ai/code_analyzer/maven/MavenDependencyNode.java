package io.nop.ai.code_analyzer.maven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenDependencyNode {
    private final MavenDependency dependency;
    private final List<MavenDependencyNode> children;

    public MavenDependencyNode(MavenDependency dependency) {
        this.dependency = dependency;
        this.children = new ArrayList<>();
    }

    public String getModuleId() {
        return dependency.getModuleId();
    }

    public MavenDependency getDependency() {
        return dependency;
    }

    public List<MavenDependencyNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(MavenDependencyNode child) {
        children.add(child);
    }

    @Override
    public String toString() {
        return toStringWithIndent(0);
    }

    private String toStringWithIndent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
        sb.append(level > 0 ? "+- " : "").append(dependency.toString());
        for (MavenDependencyNode child : children) {
            sb.append("\n").append(child.toStringWithIndent(level + 1));
        }
        return sb.toString();
    }

    public String toSimplifiedTreeString(int maxLevel) {
        return toSimplifiedTreeString(0, maxLevel);
    }

    public String toSimplifiedTreeString(int level, int maxLevel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
        sb.append(level > 0 ? "+- " : "").append(dependency.getArtifactId());
        if (level == maxLevel) {
            return sb.toString();
        }

        for (MavenDependencyNode child : children) {
            sb.append("\n").append(child.toSimplifiedTreeString(level + 1, maxLevel));
        }
        return sb.toString();
    }
}
