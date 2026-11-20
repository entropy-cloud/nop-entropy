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
 *       行终止符由长度直接判定免解码、行文本经 {@link LineCursor#text} 解码（plan 2273 A4：
 *       行文本解码单一实现归 LineCursor）。</li>
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
    static FileMatches aggregate(List<MatchSpan> spans, MemorySegment seg, long size, int maxLines,
                                 boolean includeLineText, boolean includeSubmatchText) {
        if (!includeLineText) {
            LineCursor cursor = new LineCursor(seg, size);
            int lineCount = 0;
            long lastLineStart = -1;
            boolean truncated = false;
            for (MatchSpan span : spans) {
                // count 口径只消费行起点（plan 2275 P6：advanceLineStart 免行尾前向扫描）
                long lineStart = cursor.advanceLineStart(span.start());
                if (lineStart != lastLineStart) {
                    if (maxLines > 0 && lineCount >= maxLines) {
                        truncated = true;
                        break;
                    }
                    lineCount++;
                    lastLineStart = lineStart;
                }
            }
            return FileMatches.ofCount(lineCount, truncated);
        }
        return buildLineMatches(seg, size, spans, maxLines, includeSubmatchText);
    }

    private static FileMatches buildLineMatches(MemorySegment seg, long size, List<MatchSpan> spans, int maxLines,
                                                boolean includeSubmatchText) {
        LineCursor cursor = new LineCursor(seg, size);
        List<LineMatch> lines = new ArrayList<>();
        List<Submatch> currentSubmatches = new ArrayList<>();
        LineCursor.LineInfo currentInfo = null;
        boolean truncated = false;
        for (MatchSpan span : spans) { // spans 升序，同行命中的行信息相同
            LineCursor.LineInfo info = cursor.advance(span.start());
            if (currentInfo == null || info.lineStart() != currentInfo.lineStart()) {
                if (currentInfo != null) {
                    if (maxLines > 0 && lines.size() >= maxLines) {
                        truncated = true;
                        break;
                    }
                    // submatches 所有权转移给 LineMatch（优化迭代 Round 3：去掉 List.copyOf）
                    lines.add(newLineMatch(seg, cursor, currentInfo, currentSubmatches));
                    currentSubmatches = new ArrayList<>();
                }
                currentInfo = info;
            }
            // R5：消费方不读命中文本时免逐命中解码（text 置空串，区间保留）
            LineCursor.DecodedText subText = includeSubmatchText
                    ? decodeFidelity(seg, span.start(), (int) (span.end() - span.start()))
                    : null;
            currentSubmatches.add(new Submatch(span.start(), span.end(),
                    subText == null ? "" : subText.text(),
                    subText == null ? null : subText.bytes()));
        }
        if (currentInfo != null && !truncated) {
            if (maxLines > 0 && lines.size() >= maxLines) {
                truncated = true;
            } else {
                lines.add(newLineMatch(seg, cursor, currentInfo, currentSubmatches));
            }
        }
        return FileMatches.ofLines(lines, truncated);
    }

    private static LineMatch newLineMatch(MemorySegment seg, LineCursor cursor, LineCursor.LineInfo info,
                                          List<Submatch> submatches) {
        // 行文本经 LineCursor.decodedText 解码 + 字节保真判定（plan 2575 Phase E1；plan 2273 A4 归一保持）
        LineCursor.DecodedText decoded = cursor.decodedText(info);
        String content = decoded.text();
        // 终止符由长度直接判定，免解码（优化迭代 Round 3）
        long termLen = info.lineEnd() - info.contentEnd();
        String terminator = termLen == 0 ? "" : termLen == 1 ? "\n" : "\r\n";
        // bytes 输出字段含行终止符（lineStart..lineEnd，plan 2575 R1）；仅行内容非法 UTF-8 时产出
        byte[] lineBytes = decoded.bytes() == null ? null
                : ByteText.rawBytes(seg, info.lineStart(), (int) (info.lineEnd() - info.lineStart()));
        return new LineMatch(info, submatches, content, terminator, lineBytes);
    }

    /**
     * 命中文本字节保真解码（plan 2575 Phase E1）：合法 UTF-8 走 decoder 快路径，
     * 非法走 lossy + 原始字节保留。
     */
    private static LineCursor.DecodedText decodeFidelity(MemorySegment seg, long offset, int length) {
        if (length <= 0) {
            return new LineCursor.DecodedText("", null);
        }
        byte[] bytes = ByteText.rawBytes(seg, offset, length);
        if (ByteText.isValidUtf8(bytes, 0, length)) {
            return new LineCursor.DecodedText(ByteText.decodeValid(bytes), null);
        }
        return new LineCursor.DecodedText(ByteText.decodeLossy(bytes), bytes);
    }
}
