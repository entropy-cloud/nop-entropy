package io.nop.rg.core.coordinator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * 一次搜索命令（coordinator 输入模型）。
 */
public class SearchCommand {
    private final Path root;
    private final String pattern;
    private final SearchCoordinator.Strategy strategy;
    private final boolean ignoreCase;
    private final List<String> globs;
    private final int maxMatchesPerFile; // 0 = 不限制（按行计）
    private final boolean includeLineText; // false = 不解码行文本/子匹配文本（count 口径，rg -c 等价）
    private byte[] patternBytes; // 惰性缓存（plan 2268 F7：分块路径每文件调用一次，免重复 UTF-8 编码）

    public SearchCommand(Path root, String pattern, SearchCoordinator.Strategy strategy,
                         boolean ignoreCase, List<String> globs, int maxMatchesPerFile) {
        this(root, pattern, strategy, ignoreCase, globs, maxMatchesPerFile, true);
    }

    public SearchCommand(Path root, String pattern, SearchCoordinator.Strategy strategy,
                         boolean ignoreCase, List<String> globs, int maxMatchesPerFile, boolean includeLineText) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("search pattern must not be empty");
        }
        this.root = root;
        this.pattern = pattern;
        this.strategy = strategy;
        this.ignoreCase = ignoreCase;
        this.globs = globs == null ? List.of() : List.copyOf(globs);
        this.maxMatchesPerFile = maxMatchesPerFile;
        this.includeLineText = includeLineText;
    }

    public Path getRoot() {
        return root;
    }

    public String getPattern() {
        return pattern;
    }

    /**
     * 模式字节（UTF-8）。惰性缓存；返回克隆，调用方修改不影响后续调用（防御性语义不变）。
     */
    public byte[] patternBytes() {
        if (patternBytes == null) {
            patternBytes = pattern.getBytes(StandardCharsets.UTF_8);
        }
        return patternBytes.clone();
    }

    public SearchCoordinator.Strategy strategy() {
        return strategy;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public List<String> getGlobs() {
        return globs;
    }

    public int getMaxMatchesPerFile() {
        return maxMatchesPerFile;
    }

    public boolean isIncludeLineText() {
        return includeLineText;
    }
}
