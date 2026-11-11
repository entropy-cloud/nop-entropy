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
    private final boolean includeSubmatchText; // false = Submatch 携带区间但不解码命中文本（plan 2273 R5；纯文本输出不消费该文本）
    private final byte[] patternBytes; // 急切编码（plan 2564 W-01：构造器内一次编码，final 字段获得 JMM 安全发布）

    public SearchCommand(Path root, String pattern, SearchCoordinator.Strategy strategy,
                         boolean ignoreCase, List<String> globs, int maxMatchesPerFile) {
        this(root, pattern, strategy, ignoreCase, globs, maxMatchesPerFile, true, true);
    }

    public SearchCommand(Path root, String pattern, SearchCoordinator.Strategy strategy,
                         boolean ignoreCase, List<String> globs, int maxMatchesPerFile, boolean includeLineText) {
        this(root, pattern, strategy, ignoreCase, globs, maxMatchesPerFile, includeLineText, true);
    }

    /**
     * @param includeLineText     false = count 口径，行文本/子匹配均不解码
     * @param includeSubmatchText false = Submatch 仅携带字节区间、text 为空串
     *                            （消费方不读命中文本时免逐命中 UTF-8 解码，plan 2273 R5）
     */
    public SearchCommand(Path root, String pattern, SearchCoordinator.Strategy strategy,
                         boolean ignoreCase, List<String> globs, int maxMatchesPerFile,
                         boolean includeLineText, boolean includeSubmatchText) {
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
        this.includeSubmatchText = includeSubmatchText;
        // 急切编码（plan 2564 W-01，裁定 A）：主构造器内一次 UTF-8 编码——final 字段消除
        // 惰性 check-then-act 双写窗与 JMM 未安全发布窗（3 个调用点均在命令构造后的搜索
        // 主线程段，急切零额外成本；FindCommand 全 final 对照形态归一）
        this.patternBytes = pattern.getBytes(StandardCharsets.UTF_8);
    }

    public Path getRoot() {
        return root;
    }

    public String getPattern() {
        return pattern;
    }

    /**
     * 模式字节（UTF-8，plan 2564 W-01 起构造器急切编码）。返回克隆，调用方修改不影响
     * 后续调用（防御性语义不变）。
     */
    public byte[] patternBytes() {
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

    public boolean isIncludeSubmatchText() {
        return includeSubmatchText;
    }
}
