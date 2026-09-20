package io.nop.rg.core.coordinator;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 命中聚合器（plan 2268 Phase 2：自 {@link SearchCoordinator} 提取的行级聚合职责，纯移动无逻辑变更）。
 *
 * <p>契约（方法注释为准）：
 * <ul>
 *   <li>count 口径（includeLineText=false）走纯行计数快速路径（plan 2265 Round 5：跳过
 *       LineMatch/Submatch 构建，行文本/子匹配文本不解码）。</li>
 *   <li>text 口径构建 LineMatch 列表：submatches 所有权转移（Round 3：去掉 List.copyOf）、
 *       行终止符由长度直接判定免解码、行文本 UTF-8 解码。</li>
 *   <li>maxLines 截断按行计（对齐 rg -c/-m 语义）；截断时聚合提前终止。</li>
 * </ul>
 */
public final class MatchAggregator {

    private MatchAggregator() {
    }

    /**
     * 命中聚合入口：按 {@code includeLineText} 分派 count 快速路径与 LineMatch 构建。
     * spans 须按起始偏移升序（literalSpans/regexSpans 契约）。
     */
    public static FileMatches aggregate(List<long[]> spans, MemorySegment seg, long size, int maxLines,
                                        boolean includeLineText) {
        if (!includeLineText) {
            LineCursor cursor = new LineCursor(seg, size);
            int lineCount = 0;
            long lastLineStart = -1;
            boolean truncated = false;
            for (long[] span : spans) {
                LineCursor.LineInfo info = cursor.advance(span[0]);
                if (info.lineStart() != lastLineStart) {
                    if (maxLines > 0 && lineCount >= maxLines) {
                        truncated = true;
                        break;
                    }
                    lineCount++;
                    lastLineStart = info.lineStart();
                }
            }
            return FileMatches.ofCount(lineCount, truncated);
        }
        return buildLineMatches(seg, size, spans, maxLines);
    }

    private static FileMatches buildLineMatches(MemorySegment seg, long size, List<long[]> spans, int maxLines) {
        LineCursor cursor = new LineCursor(seg, size);
        List<LineMatch> lines = new ArrayList<>();
        List<Submatch> currentSubmatches = new ArrayList<>();
        LineCursor.LineInfo currentInfo = null;
        boolean truncated = false;
        for (long[] span : spans) { // spans 升序，同行命中的行信息相同
            LineCursor.LineInfo info = cursor.advance(span[0]);
            if (currentInfo == null || info.lineStart() != currentInfo.lineStart()) {
                if (currentInfo != null) {
                    if (maxLines > 0 && lines.size() >= maxLines) {
                        truncated = true;
                        break;
                    }
                    // submatches 所有权转移给 LineMatch（优化迭代 Round 3：去掉 List.copyOf）
                    lines.add(newLineMatch(seg, currentInfo, currentSubmatches));
                    currentSubmatches = new ArrayList<>();
                }
                currentInfo = info;
                currentSubmatches.clear();
            }
            currentSubmatches.add(new Submatch(span[0], span[1], decode(seg, span[0], (int) (span[1] - span[0]))));
        }
        if (currentInfo != null && !truncated) {
            if (maxLines > 0 && lines.size() >= maxLines) {
                truncated = true;
            } else {
                lines.add(newLineMatch(seg, currentInfo, currentSubmatches));
            }
        }
        return FileMatches.ofLines(lines, truncated);
    }

    private static LineMatch newLineMatch(MemorySegment seg, LineCursor.LineInfo info, List<Submatch> submatches) {
        String content = decode(seg, info.lineStart(), (int) (info.contentEnd() - info.lineStart()));
        // 终止符由长度直接判定，免解码（优化迭代 Round 3）
        long termLen = info.lineEnd() - info.contentEnd();
        String terminator = termLen == 0 ? "" : termLen == 1 ? "\n" : "\r\n";
        return new LineMatch(info, submatches, content, terminator);
    }

    private static String decode(MemorySegment seg, long offset, int length) {
        if (length <= 0) {
            return "";
        }
        byte[] bytes = seg.asSlice(offset, length).toArray(ValueLayout.JAVA_BYTE);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
