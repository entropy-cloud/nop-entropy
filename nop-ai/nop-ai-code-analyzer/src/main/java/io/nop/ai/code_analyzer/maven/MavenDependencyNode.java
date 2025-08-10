package io.nop.ai.code_analyzer.maven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public String getArtifactId(){
        return dependency.getArtifactId();
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

    /**
     * 递归获取所有依赖（包含当前节点）
     *
     * @return 不重复的依赖列表（按依赖关系顺序）
     */
    public List<MavenDependency> getAllDependencies(List<String> scopes, boolean includeSelf) {
        Map<String, MavenDependency> uniqueDeps = new LinkedHashMap<>();
        collectDependencies(uniqueDeps, scopes, includeSelf);
        return new ArrayList<>(uniqueDeps.values());
    }

    private void collectDependencies(Map<String, MavenDependency> uniqueDeps, List<String> scopes, boolean includeSelf) {
        // 添加当前节点依赖（如果作用域匹配）
        if (scopes != null && !scopes.contains(this.dependency.getScope())) {
            return;
        }

        if (includeSelf) {
            if (uniqueDeps.putIfAbsent(dependency.getModuleId(), dependency) != null)
                return;
        }

        for (MavenDependencyNode child : children) {
            child.collectDependencies(uniqueDeps, scopes, true);
        }
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
