package io.nop.rg.core.coordinator;

import io.nop.rg.core.search.ByteScan;

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
     * 推进游标到 offset 所在行，仅返回行起点（count 口径快速路径，plan 2275 P6：
     * 行计数只消费 lineStart，跳过 {@link #advance} 的行尾前向扫描）。
     * 游标状态与 advance 保持一致，两者可混用；offset 回退时全量重扫。
     */
    long advanceLineStart(long offset) {
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
        return lineStart;
    }

    /**
     * 提取行文本（不含行终止符）并做字节保真判定（plan 2575 Phase E1）。行文本解码的唯一实现
     * （plan 2273 A4：MatchAggregator 行内容经此解码，不再自持 decode 拷贝）。
     *
     * <p>合法 UTF-8：text = decoder 快路径解码（与改动前相同），bytes 为 null（零额外开销除
     * 一次线性扫描）；非法 UTF-8：text = lossy 形态（逐非法字节 {@code U+FFFD}），
     * bytes = 行内容原始字节（不含终止符；bytes 输出字段含终止符，由聚合层按
     * {@code lineStart..lineEnd} 另行截取，plan 2575 R1）。
     */
    public DecodedText decodedText(LineInfo info) {
        if (info.contentEnd() <= info.lineStart()) {
            return new DecodedText("", null);
        }
        int len = (int) (info.contentEnd() - info.lineStart());
        byte[] bytes = seg.asSlice(info.lineStart(), len).toArray(ValueLayout.JAVA_BYTE);
        if (ByteText.isValidUtf8(bytes, 0, len)) {
            return new DecodedText(ByteText.decodeValid(bytes), null);
        }
        return new DecodedText(ByteText.decodeLossy(bytes), bytes);
    }

    private long indexOf(byte target, long from, long to) {
        // Wave 5 item 16：SWAR 字长扫描替代逐字节循环（roadmap，2026-11-19）
        return ByteScan.findFirstByte(seg, from, to, target);
    }

    private byte byteAt(long pos) {
        return seg.get(ValueLayout.JAVA_BYTE, pos);
    }

    /**
     * 行信息：行号（1-based）、行起点、行终点（含终止符）、内容终点（不含终止符）。
     */
    public record LineInfo(long lineNumber, long lineStart, long lineEnd, long contentEnd) {
    }

    /**
     * 行文本字节保真结果：text 为可读文本（非法 UTF-8 时为 lossy 形态）；bytes 为原始
     * 内容字节（仅非法 UTF-8 时非 null，plan 2575 Phase E1）。
     */
    public record DecodedText(String text, byte[] bytes) {
    }
}
