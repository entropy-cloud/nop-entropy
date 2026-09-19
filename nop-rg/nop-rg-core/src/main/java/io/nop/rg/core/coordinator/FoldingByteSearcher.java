package io.nop.rg.core.coordinator;

import io.nop.rg.core.search.ByteSearchStrategy;
import io.nop.rg.core.search.ScalarByteSearcher;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

/**
 * ASCII 大小写折叠的字面量搜索（`-i` 语义，plan 2264 钉死：非 ASCII 字节精确匹配）。
 * 放在 coordinator 包以保持 Wave 1 search/ 包零修改（plan 2264 M4 裁定）。
 *
 * <p>实现：BMH 骨架复用（锚点选择/频率表），所有比较经 {@link #fold(byte)} 折叠，
 * 坏字符跳表建在折叠后的模式上。
 */
public class FoldingByteSearcher implements ByteSearchStrategy {

    public static final FoldingByteSearcher INSTANCE = new FoldingByteSearcher();

    private static byte fold(byte b) {
        return (b >= 'A' && b <= 'Z') ? (byte) (b + 32) : b;
    }

    @Override
    public long findPattern(MemorySegment seg, long offset, long limit, byte[] pattern) {
        if (pattern.length == 0) {
            throw new IllegalArgumentException("search pattern must not be empty");
        }
        byte[] folded = new byte[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            folded[i] = fold(pattern[i]);
        }

        long firstInvalid = limit - pattern.length + 1;
        if (firstInvalid <= offset) {
            return -1;
        }

        int anchorIdx = ScalarByteSearcher.selectAnchorIndex(folded);
        byte anchorByte = folded[anchorIdx];
        long[] skip = buildFoldedSkipTable(folded, anchorIdx);

        long s = offset;
        while (s < firstInvalid) {
            byte c = fold(seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, s + anchorIdx));
            if (c == anchorByte && foldMatches(seg, s, folded)) {
                return s;
            }
            s += skip[c & 0xFF];
        }
        return -1;
    }

    @Override
    public long findFirstByte(MemorySegment seg, long offset, long limit, byte target) {
        byte foldedTarget = fold(target);
        for (long i = offset; i < limit; i++) {
            if (fold(seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, i)) == foldedTarget) {
                return i;
            }
        }
        return -1;
    }

    static long[] buildFoldedSkipTable(byte[] folded, int anchorIdx) {
        long[] skip = new long[256];
        Arrays.fill(skip, anchorIdx + 1L);
        for (int p = 0; p < anchorIdx; p++) {
            skip[folded[p] & 0xFF] = anchorIdx - p;
        }
        return skip;
    }

    private boolean foldMatches(MemorySegment seg, long start, byte[] folded) {
        for (int i = 0; i < folded.length; i++) {
            if (fold(seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, start + i)) != folded[i]) {
                return false;
            }
        }
        return true;
    }
}
