package io.nop.xlang.truffle.translate;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.truffle.lang.XLangLanguage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 合成 Source 与 SourceSection 回映射（设计 truffle 02 §三/§四）：XLang 无 parser，
 * Source 按 SourceLocation（路径 + 行号/列号）合成——内容为按行列定位的占位网格，
 * section 的 startLine/startColumn 即回映射到的源位置。
 *
 * <p><b>按路径共享 Source</b>（check2 P3 修复）：此前每个翻译节点各建一个 Source（单字符
 * 内容 O(line+col) 构造），共享 Engine 的 source 管理承受每单元成百上千个 Source，且 LRU
 * 淘汰后重翻译反复重建。现按资源 path 缓存一个网格 Source（容量按请求的行列几何增长，
 * 超容量时重建更大的网格替换缓存项），同路径所有 section 复用同一 Source 实例，
 * {@code createSection(charIndex, 1)} 按行列定位。
 */
public final class SyntheticSources {

    /** 缓存上限：动态树的合成路径可能无界增长，超限清空后按需重建（防内存泄漏）。 */
    private static final int MAX_CACHED_SOURCES = 1024;

    private static final Map<String, GridSource> SOURCES_BY_PATH = new ConcurrentHashMap<>();

    private SyntheticSources() {
    }

    /**
     * SourceLocation → 合成 SourceSection（loc 为 null 或无 path 时返回 null，不虚构位置）。
     */
    public static SourceSection sectionOf(SourceLocation loc) {
        if (loc == null || loc.getPath() == null)
            return null;
        int line = Math.max(loc.getLine(), 1);
        int col = Math.max(loc.getCol(), 1);
        GridSource grid = sourceFor(loc.getPath(), line, col);
        // 行 l 占据网格第 (l-1)*cols 起 cols 个字符，列 c 的字符下标即 (l-1)*cols + (c-1)
        return grid.source.createSection((line - 1) * grid.cols + (col - 1), 1);
    }

    /**
     * SourceSection → 回映射的 SourceLocation（对拍第三层断言的回映射口径：path + line + col）。
     */
    public static SourceLocation toSourceLocation(SourceSection section) {
        if (section == null)
            return null;
        String path = section.getSource().getPath();
        if (path == null)
            path = section.getSource().getName();
        return SourceLocation.fromLine(path, section.getStartLine(), section.getStartColumn());
    }

    private static GridSource sourceFor(String path, int line, int col) {
        GridSource current = SOURCES_BY_PATH.get(path);
        if (current != null && current.lines >= line && current.cols >= col)
            return current;
        // 容量检查必须在compute之外：mapping函数内不得修改同一map（动态树合成路径无界时防泄漏）
        if (SOURCES_BY_PATH.size() > MAX_CACHED_SOURCES)
            SOURCES_BY_PATH.clear();
        return SOURCES_BY_PATH.compute(path, (p, existing) -> {
            if (existing != null && existing.lines >= line && existing.cols >= col)
                return existing;
            int lines = roundUpToPowerOfTwo(Math.max(line, existing == null ? 1 : existing.lines));
            int cols = roundUpToPowerOfTwo(Math.max(col, existing == null ? 1 : existing.cols));
            return new GridSource(buildGridSource(path, lines, cols), lines, cols);
        });
    }

    private static Source buildGridSource(String path, int lines, int cols) {
        // 每行 (cols-1) 个空格 + 行分隔符（末行为'^'）：行 l 列 c 的字符下标 = (l-1)*cols + (c-1)
        StringBuilder content = new StringBuilder(lines * cols);
        for (int i = 1; i <= lines; i++) {
            for (int j = 1; j < cols; j++)
                content.append(' ');
            content.append(i < lines ? '\n' : '^');
        }
        return Source.newBuilder(XLangLanguage.ID, content, path).build();
    }

    private static int roundUpToPowerOfTwo(int n) {
        // 容量按2的幂向上取整：请求行列递增时重建次数摊还为对数级
        if (n <= 1)
            return 1;
        return Integer.highestOneBit(n - 1) << 1;
    }

    private static final class GridSource {
        final Source source;
        final int lines;
        final int cols;

        GridSource(Source source, int lines, int cols) {
            this.source = source;
            this.lines = lines;
            this.cols = cols;
        }
    }
}
