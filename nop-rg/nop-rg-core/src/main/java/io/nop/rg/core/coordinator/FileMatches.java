package io.nop.rg.core.coordinator;

import java.util.List;

/**
 * 单文件搜索结果：命中的行列表（升序）与截断标志；count 口径（includeLineText=false 且仅需行数）
 * 下 lines 为空、仅携带 lineCount（plan 2265 优化迭代 Round 5：跳过 LineMatch/Submatch 构建）。
 *
 * <p>plan 2268 Phase 2：自 {@link SearchCoordinator} 嵌套类提升为顶层（纯移动，行为不变）。
 */
public final class FileMatches {
    private final List<LineMatch> lines;
    private final boolean truncated;
    private final int countOnly; // -1 表示非 count-only

    FileMatches(List<LineMatch> lines, boolean truncated, int countOnly) {
        this.lines = lines;
        this.truncated = truncated;
        this.countOnly = countOnly;
    }

    static FileMatches ofLines(List<LineMatch> lines, boolean truncated) {
        return new FileMatches(lines, truncated, -1);
    }

    static FileMatches ofCount(int lineCount, boolean truncated) {
        return new FileMatches(List.of(), truncated, lineCount);
    }

    public List<LineMatch> getLines() {
        return lines;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public int lineCount() {
        return countOnly >= 0 ? countOnly : lines.size();
    }

    public boolean isCountOnly() {
        return countOnly >= 0;
    }
}
