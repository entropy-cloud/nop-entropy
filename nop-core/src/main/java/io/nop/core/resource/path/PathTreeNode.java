package io.nop.core.resource.path;

import io.nop.commons.util.StringHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class PathTreeNode {
    private final String name;
    private List<PathTreeNode> children;
    private int level;
    private final PathTreeNode parent;
    private boolean isDirectory;

    public PathTreeNode(String name, int level, PathTreeNode parent, boolean isDirectory) {
        this.name = Objects.requireNonNull(name, "Node name cannot be null");
        this.level = level;
        this.parent = parent;
        this.isDirectory = isDirectory;
        this.children = isDirectory ? new ArrayList<>() : null;
    }

    public static PathTreeNode createRootNode() {
        return new PathTreeNode("/", 0, null, true);
    }

    public static PathTreeNode parseFromText(String text) {
        List<String> lines = Arrays.asList(StringHelper.splitToLines(text));
        return new PathTreeParser().parse(lines);
    }

    public void removeEmptyDir() {
        if (this.children != null) {
            Iterator<PathTreeNode> it = children.iterator();
            while (it.hasNext()) {
                PathTreeNode child = it.next();
                child.removeEmptyDir();
                if (child.isDirectory() && !child.hasChild()) {
                    it.remove();
                }
            }
        }
    }

    public boolean hasChild() {
        return children != null && !children.isEmpty();
    }

    public String getName() {
        return name;
    }

    public List<PathTreeNode> getChildren() {
        return children;
    }

    public List<PathTreeNode> makeChildren() {
        if (children == null) {
            children = new ArrayList<>();
            isDirectory = true;
        }
        return children;
    }

    public int getLevel() {
        return level;
    }

    public void resetLevel(int level) {
        this.level = level;
        if (children != null) {
            for (PathTreeNode child : children) {
                child.resetLevel(level + 1);
            }
        }
    }

    public PathTreeNode getParent() {
        return parent;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public boolean isFile() {
        return !isDirectory;
    }

    public String getFileExtension() {
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex + 1) : "";
    }

    public String getFullPath() {
        if (parent == null) {
            return name + (isDirectory ? "/" : "");
        }
        String parentPath = parent.getFullPath();
        // 确保父路径末尾没有多余的/
        if (parentPath.endsWith("/")) {
            parentPath = parentPath.substring(0, parentPath.length() - 1);
        }
        return parentPath + "/" + name + (isDirectory ? "/" : "");
    }

    public List<PathTreeNode> getAllFiles() {
        List<PathTreeNode> files = new ArrayList<>();
        collectFiles(this, files);
        return files;
    }

    public PathTreeNode addSubNode(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        // 保留原始路径是否以斜杠结尾的信息
        boolean isPathDirectory = path.endsWith("/");

        // 标准化路径 - 去除开头和结尾的斜杠
        path = StringHelper.trimLeft(path, '/');
        path = StringHelper.trimRight(path, '/');

        if (path.isEmpty()) {
            return this; // 如果path只是斜杠，返回当前节点
        }

        List<String> parts = StringHelper.split(path, '/');
        PathTreeNode current = this;

        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            // 当前部分是目录的条件：
            // 1. 不是最后一部分，或者
            // 2. 是最后一部分且原始路径以斜杠结尾
            boolean isDirectory = (i < parts.size() - 1) || isPathDirectory;

            PathTreeNode child = current.getDirectChild(part);
            if (child == null) {
                // 创建新节点
                child = new PathTreeNode(part, current.level + 1, current, isDirectory);
                current.makeChildren().add(child);
            } else if (!child.isDirectory() && isDirectory) {
                // 将现有的文件节点转换为目录节点
                child.isDirectory = true;
                child.children = new ArrayList<>();
            }

            current = child;
        }

        return current;
    }

    public PathTreeNode getDirectChild(String name) {
        if (parent.children == null) {
            return null;
        }
        for (PathTreeNode child : parent.children) {
            if (child.name.equals(name)) {
                return child;
            }
        }
        return null;
    }

    private void collectFiles(PathTreeNode node, List<PathTreeNode> files) {
        if (node.isFile()) {
            files.add(node);
        } else {
            for (PathTreeNode child : node.getChildren()) {
                collectFiles(child, files);
            }
        }
    }

    public PathTreeNode findNode(String path) {
        if (path.equals("/"))
            return this;
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

        List<String> parts = StringHelper.split(path, '/');
        return findNode(this, parts, 0);
    }

    private PathTreeNode findNode(PathTreeNode current, List<String> pathParts, int index) {
        if (index >= pathParts.size()) {
            return current;
        }

        String part = pathParts.get(index);
        for (PathTreeNode child : current.getChildren()) {
            if (child.getName().equals(part)) {
                return findNode(child, pathParts, index + 1);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "PathTreeNode(name=" + name + ",level=" + level + ")";
    }

    /**
     * 构建仅使用空白字符缩进的树形字符串，避免使用特殊字符
     *
     * @return 树形结构的字符串表示
     */
    public String buildPlainTreeString() {
        StringBuilder sb = new StringBuilder();
        buildPlainTreeString(sb, 0);
        return sb.toString();
    }

    /**
     * 递归构建仅使用空白字符的树形字符串
     *
     * @param sb     字符串构建器
     * @param indent 缩进级别
     */
    private void buildPlainTreeString(StringBuilder sb, int indent) {
        // 添加缩进（每级缩进4个空格）
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }

        // 添加节点名称，如果是目录则加上/
        sb.append(name).append(isDirectory ? "/" : "").append("\n");

        // 递归处理子节点
        if (isDirectory && children != null) {
            for (PathTreeNode child : children) {
                child.buildPlainTreeString(sb, indent + 1);
            }
        }
    }

    public String buildTreeString() {
        StringBuilder sb = new StringBuilder();
        buildTreeString(sb, 0, new ArrayList<>());
        return sb.toString();
    }

    private void buildTreeString(StringBuilder sb, int indent, List<Boolean> lastFlags) {
        // 添加缩进（跳过根节点的缩进）
        for (int i = 0; i < indent - 1; i++) {
            if (i < lastFlags.size()) {
                if (lastFlags.get(i)) {
                    sb.append("    ");  // 上级是最后节点
                } else {
                    sb.append("│   ");  // 上级有后续兄弟节点
                }
            }
        }

        // 添加节点前缀（根节点不加前缀）
        if (indent > 0) {
            sb.append(lastFlags.get(indent - 1) ? "└── " : "├── ");
        }

        // 添加节点名称，如果是目录则加上/
        sb.append(name).append(isDirectory ? "/" : "").append("\n");

        if (isDirectory && children != null) {
            for (int i = 0; i < children.size(); i++) {
                List<Boolean> newLastFlags = new ArrayList<>(lastFlags);
                newLastFlags.add(i == children.size() - 1);
                children.get(i).buildTreeString(sb, indent + 1, newLastFlags);
            }
        }
    }

    public static class PathTreeParser {
        private static final int SPACES_PER_LEVEL = 4;

        public PathTreeNode parse(List<String> lines) {
            if (lines == null || lines.isEmpty()) {
                throw new IllegalArgumentException("Input lines cannot be null or empty");
            }

            String rootLine = lines.get(0).trim();
            if (rootLine.isEmpty()) {
                throw new IllegalArgumentException("Root node cannot be empty");
            }

            PathTreeNode root = new PathTreeNode(rootLine, 0, null, true);
            Deque<PathTreeNode> stack = new ArrayDeque<>();
            stack.push(root);

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) continue;

                int count = skipSeparator(line);
                int indent = count / SPACES_PER_LEVEL;
                boolean isDirectory = line.endsWith("/");
                String name = line.substring(count, isDirectory ? line.length() - 1 : line.length()).trim();

                // Pop stack until we find the parent
                while (stack.size() > 1 && stack.peek().getLevel() >= indent) {
                    stack.pop();
                }

                PathTreeNode parent = stack.peek();
                if (parent == null) {
                    throw new IllegalStateException("Invalid tree structure: file cannot have children, line=" + (i + 1));
                }

                PathTreeNode node = new PathTreeNode(name, indent, parent, isDirectory);
                parent.makeChildren().add(node);

                stack.push(node);
            }

            root.resetLevel(0);
            return root;
        }

        private int skipSeparator(String line) {
            int count = 0;
            while (count < line.length() && isSeparator(line.charAt(count))) {
                count++;
            }

            return count;
        }

        private boolean isSeparator(char c) {
            return Character.isWhitespace(c) || c == '│' || c == '-' || c == '├' || c == '─' || c == '└';
        }
    }
}