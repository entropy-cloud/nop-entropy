package io.nop.find.core.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 单条查找命中（不可变）。
 *
 * <p>{@code score} 越小越优（三级打分：前缀 &lt; 子串 &lt; 子序列，叠加字段偏移）。
 * 输出顺序为 score 升序，同分按 {@code relativePath} 字典序。
 */
public class FindMatch {
    private final String relativePath;
    private final Path absolutePath;
    private final int score;

    public FindMatch(String relativePath, Path absolutePath, int score) {
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.absolutePath = Objects.requireNonNull(absolutePath, "absolutePath");
        this.score = score;
    }

    /**
     * 相对 root 的路径，'/' 分隔（跨平台稳定形态，供输出与 glob 匹配使用）。
     */
    public String getRelativePath() {
        return relativePath;
    }

    public Path getAbsolutePath() {
        return absolutePath;
    }

    /**
     * 打分（越小越优）。
     */
    public int getScore() {
        return score;
    }

    @Override
    public String toString() {
        return score + "\t" + relativePath;
    }
}