package io.nop.ai.code_analyzer.maven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class MavenDependencyTreeParser {

    public static class MavenDependencyNode {
        private final MavenDependency dependency;
        private final List<MavenDependencyNode> children;

        public MavenDependencyNode(MavenDependency dependency) {
            this.dependency = dependency;
            this.children = new ArrayList<>();
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
    }

    /**
     * 解析mvn dependency:tree的输出，构建依赖树
     */
    public static MavenDependencyNode parse(List<String> treeLines) {
        if (treeLines == null || treeLines.isEmpty()) {
            throw new IllegalArgumentException("Input lines cannot be null or empty");
        }

        // 解析根节点
        MavenDependencyNode root = new MavenDependencyNode(
                MavenDependencyParser.parseFromTreeLine(treeLines.get(0)));

        // 使用栈来跟踪父节点和对应的缩进级别
        Stack<MavenDependencyNode> nodeStack = new Stack<>();
        Stack<Integer> levelStack = new Stack<>();
        nodeStack.push(root);
        levelStack.push(0);

        for (int i = 1; i < treeLines.size(); i++) {
            String line = treeLines.get(i);
            int currentLevel = calculateIndentLevel(line);

            // 找到正确的父节点
            while (currentLevel <= levelStack.peek() && nodeStack.size() > 1) {
                nodeStack.pop();
                levelStack.pop();
            }

            // 解析当前依赖
            MavenDependencyNode currentNode = new MavenDependencyNode(
                    MavenDependencyParser.parseFromTreeLine(line));
            nodeStack.peek().addChild(currentNode);

            // 压栈以备可能的子节点
            nodeStack.push(currentNode);
            levelStack.push(currentLevel);
        }

        return root;
    }

    /**
     * 计算行的缩进级别
     */
    private static int calculateIndentLevel(String line) {
        int level = 0;
        while (level < line.length() &&
                (line.charAt(level) == '|' || line.charAt(level) == ' ')) {
            level++;
        }
        // 每个层级用3个字符表示（如"|  "）
        return level / 3;
    }

}