package io.nop.rg.core.search;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

/**
 * 预编译的字面量模式（plan 2265 优化迭代 Round 1）：
 * 锚点选择与坏字符跳表编译一次、多次扫描（此前 coordinator 每次命中都重建跳表，
 * JFR 显示 buildSkipTable 占 14%）。语义与 {@link ScalarByteSearcher} 一致
 * （offset 绝对、limit exclusive、未找到 -1、空模式抛 IllegalArgumentException）；
 * ignoreCase 为 ASCII 折叠（与 FoldingByteSearcher 一致）。
 */
public final class PreparedLiteral {

    private static final ValueLayout.OfByte JAVA_BYTE = ValueLayout.JAVA_BYTE;

    private final int patternLength;
    private final int anchorIdx;
    private final byte anchorByte;
    private final long[] skip;
    private final byte[] foldedPattern;
    private final boolean ignoreCase;

    private PreparedLiteral(byte[] pattern, boolean ignoreCase) {
        if (pattern.length == 0) {
            throw new IllegalArgumentException("search pattern must not be empty");
        }
        this.patternLength = pattern.length;
        this.ignoreCase = ignoreCase;
        if (ignoreCase) {
            byte[] folded = new byte[pattern.length];
            for (int i = 0; i < pattern.length; i++) {
                folded[i] = fold(pattern[i]);
            }
            this.foldedPattern = folded;
        } else {
            this.foldedPattern = pattern.clone();
        }
        this.anchorIdx = ScalarByteSearcher.selectAnchorIndex(foldedPattern);
        this.anchorByte = foldedPattern[anchorIdx];
        this.skip = new long[256];
        Arrays.fill(skip, anchorIdx + 1L);
        for (int p = 0; p < anchorIdx; p++) {
            skip[foldedPattern[p] & 0xFF] = anchorIdx - p;
        }
    }

    public static PreparedLiteral compile(byte[] pattern, boolean ignoreCase) {
        return new PreparedLiteral(pattern, ignoreCase);
    }

    public int patternLength() {
        return patternLength;
    }

    public long find(MemorySegment seg, long offset, long limit) {
        long firstInvalid = limit - patternLength + 1; // 窗口起点循环上界（exclusive）
        if (firstInvalid <= offset) {
            return -1;
        }
        long s = offset;
        while (s < firstInvalid) {
            byte c = readByte(seg, s + anchorIdx);
            if (c == anchorByte && matchesAt(seg, s)) {
                return s;
            }
            s += skip[c & 0xFF];
        }
        return -1;
    }

    private boolean matchesAt(MemorySegment seg, long start) {
        for (int i = 0; i < patternLength; i++) {
            if (readByte(seg, start + i) != foldedPattern[i]) {
                return false;
            }
        }
        return true;
    }

    private byte readByte(MemorySegment seg, long pos) {
        byte b = seg.get(JAVA_BYTE, pos);
        return ignoreCase ? fold(b) : b;
    }

    private static byte fold(byte b) {
        return (b >= 'A' && b <= 'Z') ? (byte) (b + 32) : b;
    }
}
