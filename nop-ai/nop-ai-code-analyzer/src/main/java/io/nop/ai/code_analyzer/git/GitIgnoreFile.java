package io.nop.ai.code_analyzer.git;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 完全模拟Git行为的.gitignore处理器（使用Nop平台的IResource接口）
 */
public class GitIgnoreFile implements Predicate<IResource> {
    private final IResource projectRoot;
    private final Map<String, List<Rule>> rulesMap = new HashMap<>();
    private final Set<String> ignoredDirectories = new HashSet<>();

    /**
     * 表示单个.gitignore规则
     */
    public static class Rule {
        private final String originalPattern;
        private final Pattern regex;
        private final boolean negation;
        private final boolean directoryOnly;
        private final boolean anchored;

        public Rule(String originalPattern, Pattern regex, boolean negation, boolean directoryOnly, boolean anchored) {
            this.originalPattern = originalPattern;
            this.regex = regex;
            this.negation = negation;
            this.directoryOnly = directoryOnly;
            this.anchored = anchored;
        }

        public boolean matches(String relativePath, boolean isDirectory) {
            if (directoryOnly && !isDirectory) {
                return false;
            }
            return regex.matcher(relativePath).matches();
        }

        public boolean isNegation() {
            return negation;
        }

        public boolean isAnchored() {
            return anchored;
        }

        @Override
        public String toString() {
            return originalPattern;
        }
    }

    /**
     * 构建GitIgnoreFile实例
     */
    public static GitIgnoreFile create(IResource projectRoot) throws IOException {
        GitIgnoreFile instance = new GitIgnoreFile(projectRoot);
        instance.loadRules();
        return instance;
    }

    private GitIgnoreFile(IResource projectRoot) {
        this.projectRoot = projectRoot;
    }

    /**
     * 加载所有.gitignore文件规则
     */
    private void loadRules() throws IOException {
        // 先加载项目根目录的.gitignore
        loadGitIgnoreFile(projectRoot);

        // 递归加载子目录中的.gitignore文件
        loadSubdirectoryRules(projectRoot);
    }

    private void loadSubdirectoryRules(IResource directory) throws IOException {
        if (!directory.isDirectory() || isIgnored(directory)) {
            return;
        }

        List<? extends IResource> children = getChildren(directory);

        for (IResource child : children) {
            if (child.isDirectory()) {
                loadGitIgnoreFile(child);
                loadSubdirectoryRules(child);
            }
        }
    }

    private void loadGitIgnoreFile(IResource directory) {
        IResource gitIgnoreFile = getChild(directory, ".gitignore");
        if (gitIgnoreFile != null && gitIgnoreFile.exists()) {
            String content = gitIgnoreFile.readText();
            List<String> lines = StringHelper.stripedSplit(content, '\n');
            List<Rule> rules = parseRules(lines, directory.getPath());
            rulesMap.put(normalizePath(directory.getPath()), rules);
        }
    }

    protected IResource getChild(IResource resource, String path) {
        return ResourceHelper.resolveChildResource(resource, path);
    }

    protected List<? extends IResource> getChildren(IResource resource) {
        return VirtualFileSystem.instance().getChildren(resource.getStdPath());
    }

    private List<Rule> parseRules(List<String> lines, String directoryPath) {
        return lines.stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> createRule(line, directoryPath))
                .collect(Collectors.toList());
    }

    private Rule createRule(String line, String directoryPath) {
        boolean isNegation = line.startsWith("!");
        String pattern = isNegation ? line.substring(1) : line;
        boolean isDirectoryOnly = pattern.endsWith("/");
        boolean isAnchored = pattern.startsWith("/");

        if (isDirectoryOnly) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }

        // 处理相对路径模式
        if (pattern.startsWith("/")) {
            pattern = pattern.substring(1);
        } else if (!pattern.contains("/")) {
            // 简单模式，匹配所有子目录
            pattern = "**/" + pattern;
        }

        // 将模式转换为正则表达式
        String regex = convertPatternToRegex(pattern, isDirectoryOnly);
        return new Rule(line, Pattern.compile(regex), isNegation, isDirectoryOnly, isAnchored);
    }

    private String convertPatternToRegex(String pattern, boolean isDirectoryOnly) {
        StringBuilder regex = new StringBuilder();
        boolean inEscape = false;

        // 处理锚定模式
        if (pattern.startsWith("/") || pattern.startsWith("**/")) {
            regex.append("^");
            if (pattern.startsWith("/")) {
                pattern = pattern.substring(1);
            } else {
                pattern = pattern.substring(3); // 移除**/
            }
        } else {
            regex.append("(^|/)");
        }

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);

            if (inEscape) {
                regex.append(Pattern.quote(String.valueOf(c)));
                inEscape = false;
                continue;
            }

            switch (c) {
                case '\\':
                    inEscape = true;
                    break;
                case '*':
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                        if (i + 2 < pattern.length() && pattern.charAt(i + 2) == '/') {
                            // **/ 模式
                            regex.append("(.*/)?");
                            i += 2;
                        } else {
                            // ** 模式
                            regex.append(".*");
                            i++;
                        }
                    } else {
                        // * 模式
                        regex.append("[^/]*");
                    }
                    break;
                case '?':
                    regex.append("[^/]");
                    break;
                case '[':
                    int j = i + 1;
                    if (j < pattern.length() && pattern.charAt(j) == '!') {
                        j++;
                    }
                    if (j < pattern.length() && pattern.charAt(j) == ']') {
                        j++;
                    }
                    while (j < pattern.length() && pattern.charAt(j) != ']') {
                        j++;
                    }
                    if (j >= pattern.length()) {
                        regex.append("\\[");
                    } else {
                        String charClass = pattern.substring(i, j + 1);
                        regex.append(charClass);
                        i = j;
                    }
                    break;
                default:
                    regex.append(Pattern.quote(String.valueOf(c)));
                    break;
            }
        }

        // 如果模式以/结尾，确保匹配目录
        if (isDirectoryOnly) {
            regex.append("$");
        } else {
            regex.append("($|/)");
        }

        return regex.toString();
    }

    @Override
    public boolean test(IResource resource) {
        return isIgnored(resource);
    }

    /**
     * 检查资源是否被忽略
     */
    public boolean isIgnored(IResource resource) {
        String resourcePath = normalizePath(resource.getPath());
        String projectRootPath = normalizePath(projectRoot.getPath());

        if (!resourcePath.startsWith(projectRootPath)) {
            return true;
        }

        boolean isDirectory = resource.isDirectory();
        String relativePath = StringHelper.removeHead(resourcePath, projectRootPath + "/");

        // 收集所有匹配的规则，确保否定规则优先级
        List<Rule> matchedRules = new ArrayList<>();

        // 从文件所在目录向上查找所有适用的.gitignore文件
        String currentDirPath = isDirectory ? resourcePath : StringHelper.filePath(resourcePath);

        while (currentDirPath != null && currentDirPath.startsWith(projectRootPath)) {
            List<Rule> rules = rulesMap.get(currentDirPath);
            if (rules != null) {
                // 计算相对于当前.gitignore文件的路径
                String subRelativePath = StringHelper.removeHead(resourcePath, currentDirPath + "/");
                for (Rule rule : rules) {
                    if (rule.matches(subRelativePath, isDirectory)) {
                        matchedRules.add(rule);
                    }
                }
            }
            currentDirPath = StringHelper.filePath(currentDirPath);
        }

        // 应用Git的规则优先级：最后一个匹配的否定规则会覆盖前面的匹配
        boolean ignored = false;
        for (Rule rule : matchedRules) {
            if (rule.isNegation()) {
                ignored = false;
            } else if (!ignored) { // 只有当前不是忽略状态时才可能被设置为忽略
                ignored = true;
            }
        }

        return ignored;
    }

    /**
     * 规范化路径（统一使用/作为分隔符）
     */
    private String normalizePath(String path) {
        return path.replace('\\', '/');
    }

    /**
     * 获取所有加载的规则（用于调试）
     */
    public Map<String, List<Rule>> getAllRules() {
        return Collections.unmodifiableMap(rulesMap);
    }

    /**
     * 清除缓存和状态
     */
    public void clear() {
        rulesMap.clear();
        ignoredDirectories.clear();
    }

    /**
     * 重新加载规则
     */
    public void reload() throws IOException {
        clear();
        loadRules();
    }
}