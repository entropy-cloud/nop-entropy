package io.nop.ai.code_analyzer.maven;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.ai.code_analyzer.CodeAnalyzerConstants.DEPENDENCY_TREE_FILE_PATH;

public class MavenModuleStructure {
    private final Map<String, MavenDependencyNode> modules = new TreeMap<>();

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, MavenDependencyNode> entry : modules.entrySet()) {
            sb.append(entry.getValue().toSimplifiedTreeString(1)).append("\n\n");
        }
        return sb.toString();
    }

    public void load(File dir) {
        File pomFile = new File(dir, "pom.xml");
        if (pomFile.exists()) {
            MavenDependencyNode node = loadDependencyNode(dir);
            if (node != null) {
                modules.put(node.getModuleId(), node);
            }

            File[] subFiles = dir.listFiles();
            if (subFiles != null) {
                for (File subFile : subFiles) {
                    if (subFile.isDirectory()) {
                        load(subFile);
                    }
                }
            }
        }
    }

    protected MavenDependencyNode loadDependencyNode(File dir) {
        File dependencyFile = new File(dir, DEPENDENCY_TREE_FILE_PATH);
        if (dependencyFile.exists()) {
            return MavenDependencyTreeParser.parseFromFile(dependencyFile);
        }
        return null;
    }

    /**
     * 简化依赖树，只保留modules集合中存在的模块依赖
     *
     * @param node 要简化的依赖节点
     * @return 简化后的依赖节点
     */
    protected MavenDependencyNode simplifyDependenciesForModule(MavenDependencyNode node) {
        if (node == null) {
            return null;
        }

        // 创建新节点，保留原始依赖信息
        MavenDependencyNode simplifiedNode = new MavenDependencyNode(node.getDependency());

        // 遍历子节点
        for (MavenDependencyNode child : node.getChildren()) {
            String moduleId = child.getModuleId();

            // 如果子节点是modules集合中的模块，则保留并递归简化
            if (modules.containsKey(moduleId)) {
                MavenDependencyNode simplifiedChild = simplifyDependenciesForModule(child);
                if (simplifiedChild != null)
                    simplifiedNode.addChild(simplifiedChild);
            }
            // 否则跳过这个外部依赖
        }

        return simplifiedNode;
    }

    /**
     * 简化所有模块的依赖关系
     */
    public void simplifyDependencies() {
        for (Map.Entry<String, MavenDependencyNode> entry : modules.entrySet()) {
            MavenDependencyNode simplified = simplifyDependenciesForModule(entry.getValue());
            modules.put(entry.getKey(), simplified);
        }
    }
}
