package io.nop.rg.core.coordinator;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/**
 * 行提取器（plan 2264 M3 裁定：基于整文件映射，禁止基于 chunk 视图）。
 *
 * <p>游标式推进：命中按升序处理时，行号/行边界计算均摊 O(n)；offset 回退时全量重扫。
 * CRLF 与 LF 兼容；行文本含终止符（对齐 rg lines.text），同时提供不含终止符的内容端。
 */
public final class LineCursor {
    private static final byte LF = '\n';

    private final MemorySegment seg;
    private final long size;
    private long lineStart;
    private long lineNumber = 1;

    public LineCursor(MemorySegment seg, long size) {
        this.seg = seg;
        this.size = size;
    }

    /**
     * 推进游标到 offset 所在行（offset 必须不小于上次推进位置，否则重置游标）。
     */
    public LineInfo advance(long offset) {
        if (offset < lineStart) {
            lineStart = 0;
            lineNumber = 1;
        }
        while (lineStart < offset) {
            long nl = indexOf(LF, lineStart, Math.min(offset, size));
            if (nl < 0) {
                break;
            }
            lineStart = nl + 1;
            lineNumber++;
        }
        long contentEnd = indexOf(LF, offset, size);
        long lineEnd;
        if (contentEnd < 0) {
            contentEnd = size;
            lineEnd = size;
        } else {
            lineEnd = contentEnd + 1; // 含 LF
            // CRLF：CR 属于终止符
            if (contentEnd > offset && byteAt(contentEnd - 1) == '\r') {
                contentEnd -= 1;
            }
        }
        return new LineInfo(lineNumber, lineStart, lineEnd, contentEnd);
    }

    /**
     * 提取行文本（不含行终止符）。
     */
    public String text(LineInfo info) {
        if (info.contentEnd() <= info.lineStart()) {
            return "";
        }
        byte[] bytes = seg.asSlice(info.lineStart(), info.contentEnd() - info.lineStart())
                .toArray(ValueLayout.JAVA_BYTE);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private long indexOf(byte target, long from, long to) {
        for (long i = from; i < to; i++) {
            if (byteAt(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private byte byteAt(long pos) {
        return seg.get(ValueLayout.JAVA_BYTE, pos);
    }

    /**
     * 行信息：行号（1-based）、行起点、行终点（含终止符）、内容终点（不含终止符）。
     */
    public record LineInfo(long lineNumber, long lineStart, long lineEnd, long contentEnd) {
    }
}
