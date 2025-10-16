package io.nop.ai.code_analyzer.maven;

public class MavenDependencyParser {
    
    /**
     * 解析标准格式的Maven依赖字符串
     * 格式: groupId:artifactId:type:version[:scope][:classifier]
     * 示例: org.slf4j:slf4j-api:jar:2.0.6:compile
     */
    public static MavenDependency parse(String dependencyStr) {
        if (dependencyStr == null || dependencyStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Dependency string cannot be null or empty");
        }

        String[] parts = dependencyStr.split(":");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid dependency format. Expected at least 4 parts: " + dependencyStr);
        }

        String groupId = parts[0];
        String artifactId = parts[1];
        String type = parts[2];
        String version = parts[3];
        String scope = parts.length > 4 ? parts[4] : null;
        String classifier = parts.length > 5 ? parts[5] : null;

        return new MavenDependency(groupId, artifactId, type, version, scope, classifier);
    }

    /**
     * 从mvn dependency:tree的输出行中解析依赖
     * 会先清理树形结构标记(+-|等)
     */
    public static MavenDependency parseFromTreeLine(String treeLine) {
        if (treeLine == null || treeLine.trim().isEmpty()) {
            throw new IllegalArgumentException("Tree line cannot be null or empty");
        }

        // 清理树形结构标记
        String cleanLine = treeLine.replaceAll("^[\\| ]*[+\\\\]-\\s*", "").trim();
        return parse(cleanLine);
    }
}