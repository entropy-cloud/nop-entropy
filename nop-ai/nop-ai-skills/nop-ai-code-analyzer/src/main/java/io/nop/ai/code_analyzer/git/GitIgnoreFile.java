package io.nop.ai.code_analyzer.git;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 完全模拟Git行为的.gitignore处理器（使用Nop平台的IResource接口）
 * 说明：
 * - rulesMap 的 key 为相对于 projectRoot 的目录路径，根目录使用 ""。
 * - 每条规则按其所在 .gitignore 文件的目录为基准进行匹配。
 */
public class GitIgnoreFile implements Predicate<IResource> {
    private final IResource projectRoot;
    // key: 相对 projectRoot 的目录路径；根目录为 ""
    private final Map<String, List<Rule>> rulesMap = new HashMap<>();

    /**
     * 表示单个.gitignore规则
     */
    public static class Rule {
        private final String originalPattern;
        private final boolean negation;
        private final boolean directoryOnly;
        private final boolean anchored;

        // 非目录规则使用 regex
        private final Pattern regex;
        // 目录规则使用两个正则：一个匹配目录本身，一个匹配目录的子项（要求至少一个后续段）
        private final Pattern dirSelfRegex;
        private final Pattern dirDescRegex;

        public Rule(String originalPattern,
                    boolean negation,
                    boolean directoryOnly,
                    boolean anchored,
                    Pattern regex,
                    Pattern dirSelfRegex,
                    Pattern dirDescRegex) {
            this.originalPattern = originalPattern;
            this.negation = negation;
            this.directoryOnly = directoryOnly;
            this.anchored = anchored;
            this.regex = regex;
            this.dirSelfRegex = dirSelfRegex;
            this.dirDescRegex = dirDescRegex;
        }

        public boolean matches(String relativePath, boolean isDirectory) {
            if (directoryOnly) {
                if (isDirectory) {
                    return dirSelfRegex.matcher(relativePath).matches();
                } else {
                    // 文件或非目录资源：仅当它是该目录的子项时匹配
                    return dirDescRegex.matcher(relativePath).matches();
                }
            } else {
                return regex.matcher(relativePath).matches();
            }
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
    public static GitIgnoreFile create(IResource projectRoot) {
        GitIgnoreFile instance = new GitIgnoreFile(projectRoot);
        instance.loadRules();
        return instance;
    }

    private GitIgnoreFile(IResource projectRoot) {
        this.projectRoot = projectRoot;
    }

    public boolean isEmpty() {
        return this.rulesMap.isEmpty();
    }

    /**
     * 加载所有.gitignore文件规则
     */
    private void loadRules() {
        // 先加载项目根目录的.gitignore
        loadGitIgnoreFile(projectRoot);

        // 递归加载子目录中的.gitignore文件
        loadSubdirectoryRules(projectRoot);
    }

    private void loadSubdirectoryRules(IResource directory) {
        if (!directory.isDirectory() || isIgnored(directory)) {
            return;
        }

        List<? extends IResource> children = getChildren(directory);
        if (children == null) return;

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
            List<Rule> rules = parseRules(lines);
            String dirRel = toRelativeDirPath(directory.getPath());
            rulesMap.put(dirRel, rules);
        }
    }

    protected IResource getChild(IResource resource, String path) {
        return ResourceHelper.resolveChildResource(resource, path);
    }

    protected List<? extends IResource> getChildren(IResource resource) {
        return VirtualFileSystem.instance().getChildren(resource.getStdPath());
    }

    private List<Rule> parseRules(List<String> lines) {
        return lines.stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(this::createRule)
                .collect(Collectors.toList());
    }

    private Rule createRule(String line) {
        boolean isNegation = line.startsWith("!");
        String pattern = isNegation ? line.substring(1) : line;
        boolean isDirectoryOnly = pattern.endsWith("/");
        boolean isAnchored = pattern.startsWith("/");

        if (isDirectoryOnly) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }

        // 对 anchored 模式：去掉起始 "/"，以“相对当前.gitignore目录”的起始处匹配
        if (isAnchored && pattern.startsWith("/")) {
            pattern = pattern.substring(1);
        }

        // 非 anchored 且不含 "/" 的简单模式，匹配所有子目录中的该名字
        if (!isAnchored && !pattern.contains("/")) {
            pattern = "**/" + pattern;
        }

        // 构造正则
        RegexBundle rb = buildRegexBundle(pattern, isDirectoryOnly, isAnchored);

        return new Rule(line,
                isNegation,
                isDirectoryOnly,
                isAnchored,
                rb.regex,
                rb.dirSelfRegex,
                rb.dirDescRegex);
    }

    private static class RegexBundle {
        Pattern regex;       // 非目录规则
        Pattern dirSelfRegex; // 目录本身
        Pattern dirDescRegex; // 目录子项（至少一个段）
    }

    private RegexBundle buildRegexBundle(String globPattern, boolean isDirectoryOnly, boolean anchored) {
        StringBuilder prefix = new StringBuilder();
        if (anchored) {
            prefix.append("^");
        } else {
            // 允许在任意子目录边界开始匹配
            prefix.append("(^|.*/)");
        }

        String body = globToRegexBody(globPattern);

        RegexBundle rb = new RegexBundle();
        if (isDirectoryOnly) {
            // 目录本身：不允许后续段
            rb.dirSelfRegex = Pattern.compile(prefix + body + "$");
            // 目录子项：必须至少有一个后续段
            rb.dirDescRegex = Pattern.compile(prefix + body + "/.+$");
        } else {
            // 非目录规则：匹配到路径段结束或继续子段
            rb.regex = Pattern.compile(prefix + body + "($|/)");
        }

        return rb;
    }

    // 将 glob 的主体（不含前缀锚定与结尾约束）转换为正则
    private String globToRegexBody(String pattern) {
        StringBuilder regex = new StringBuilder();
        boolean inEscape = false;

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
                            // **/ 模式：任意多段目录（含空）
                            regex.append("(.*/)?");
                            i += 2;
                        } else {
                            // ** 模式：跨段匹配
                            regex.append(".*");
                            i++;
                        }
                    } else {
                        // * 模式：单段内匹配
                        regex.append("[^/]*");
                    }
                    break;
                case '?':
                    regex.append("[^/]");
                    break;
                case '[':
                    // 简单保留字符类，未对 '!' 转 '^' 做额外转换，保持与原始实现一致
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

        // 不在项目根目录下：视为忽略
        if (!resourcePath.startsWith(projectRootPath)) {
            return true;
        }

        // project root 自身不忽略
        if (resourcePath.equals(projectRootPath)) {
            return false;
        }

        boolean isDirectory = resource.isDirectory();

        // 资源相对于项目根的相对路径
        String resourceRel = toRelativePath(resourcePath);

        // 目标父目录的相对路径（从父目录开始应用规则）
        String parentRel = parentOf(resourceRel);

        // 从根目录 "" 到 parentRel，逐层应用规则（最后匹配的规则生效）
        List<String> ancestry = buildAncestryDirs(parentRel);

        boolean ignored = false;
        for (String baseDirRel : ancestry) {
            List<Rule> rules = rulesMap.get(baseDirRel);
            if (rules == null || rules.isEmpty()) continue;

            String subRelativePath;
            if (baseDirRel.isEmpty()) {
                subRelativePath = resourceRel; // 根目录下的相对路径就是自身
            } else {
                subRelativePath = StringHelper.removeHead(resourceRel, baseDirRel + "/");
            }

            for (Rule rule : rules) {
                if (rule.matches(subRelativePath, isDirectory)) {
                    // 最后一个匹配的规则生效
                    ignored = !rule.isNegation();
                }
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
     * 将绝对路径转换为相对于 projectRoot 的路径；根目录返回 ""。
     */
    private String toRelativePath(String absolutePath) {
        String normAbs = normalizePath(absolutePath);
        String normRoot = normalizePath(projectRoot.getPath());
        if (normAbs.equals(normRoot)) return "";
        return StringHelper.removeHead(normAbs, normRoot + "/");
    }

    /**
     * 目录路径的相对路径（相对于 projectRoot）；根目录返回 ""。
     */
    private String toRelativeDirPath(String absoluteDirPath) {
        return toRelativePath(absoluteDirPath);
    }

    /**
     * 构建从根目录 "" 到 parentRel 的目录链（包含根，包含 parentRel）
     */
    private List<String> buildAncestryDirs(String parentRel) {
        List<String> list = new ArrayList<>();
        list.add(""); // 根目录
        if (parentRel == null || parentRel.isEmpty()) {
            return list;
        }
        String[] parts = parentRel.split("/");
        String acc = "";
        for (String p : parts) {
            if (p.isEmpty()) continue;
            acc = acc.isEmpty() ? p : acc + "/" + p;
            list.add(acc);
        }
        return list;
    }

    /**
     * 相对路径的父目录（相对 projectRoot）。对于顶层文件（如 "a.txt"）返回 ""；对于根 "" 返回 ""。
     */
    private String parentOf(String relPath) {
        if (relPath == null || relPath.isEmpty()) return "";
        int idx = relPath.lastIndexOf('/');
        if (idx < 0) return "";
        return relPath.substring(0, idx);
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
    }

    /**
     * 重新加载规则
     */
    public void reload() throws IOException {
        clear();
        loadRules();
    }
}