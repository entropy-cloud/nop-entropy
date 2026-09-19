package io.nop.rg.core.search;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

/**
 * 标量 Boyer-Moore-Horspool 搜索（nop-rg design 决策 3）。
 *
 * <p>核心优化：不以模式末字节为锚，而是选择模式中按启发式频率表最罕见的字节作为锚点
 * （ripgrep 的核心思路——罕见字节失配概率高，坏字符跳跃更长）。
 * 锚点字节失配时按坏字符表跳跃；锚点命中但整体校验失败时，锚点字节本身即坏字符，
 * 用同一张坏字符表推进（Horspool 论证保证不漏配）。
 */
public class ScalarByteSearcher implements ByteSearchStrategy {

    public static final ScalarByteSearcher INSTANCE = new ScalarByteSearcher();

    private static final ValueLayout.OfByte JAVA_BYTE = ValueLayout.JAVA_BYTE;

    // 启发式字节频率：常见文本字节（字母/数字/空格/常见标点）出现频率高，其余默认低
    private static final int[] BYTE_FREQUENCY = buildFrequencyTable();

    private static int[] buildFrequencyTable() {
        int[] freq = new int[256];
        for (int b = 0; b < 256; b++) {
            freq[b] = 1;
        }
        int common = 100;
        freq[' '] = common;
        freq['\n'] = common;
        freq['\r'] = common;
        freq['\t'] = 60;
        for (char c = 'a'; c <= 'z'; c++) {
            freq[c] = common;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            freq[c] = 80;
        }
        for (char c = '0'; c <= '9'; c++) {
            freq[c] = 70;
        }
        // 高频标点与常见符号
        freq['/'] = 90;
        freq['.'] = 85;
        freq['-'] = 80;
        freq['_'] = 75;
        freq['"'] = 70;
        freq['\''] = 65;
        freq['='] = 60;
        freq['('] = 55;
        freq[')'] = 55;
        freq[':'] = 55;
        freq[','] = 65;
        freq[';'] = 45;
        freq['<'] = 50;
        freq['>'] = 50;
        freq['{'] = 50;
        freq['}'] = 50;
        freq['#'] = 45;
        freq['*'] = 40;
        freq['&'] = 40;
        freq['|'] = 35;
        freq['+'] = 35;
        return freq;
    }

    @Override
    public long findPattern(MemorySegment seg, long offset, long limit, byte[] pattern) {
        if (pattern.length == 0) {
            throw new IllegalArgumentException("search pattern must not be empty");
        }
        long firstInvalid = limit - pattern.length + 1; // 窗口起点循环上界（exclusive），窗口需满足 s+len <= limit
        if (firstInvalid <= offset) {
            return -1;
        }

        int anchorIdx = selectAnchorIndex(pattern);
        byte anchorByte = pattern[anchorIdx];
        long[] skip = buildSkipTable(pattern, anchorIdx);

        long s = offset;
        while (s < firstInvalid) {
            byte c = seg.get(JAVA_BYTE, s + anchorIdx);
            if (c == anchorByte && matches(seg, s, pattern)) {
                return s;
            }
            s += skip[c & 0xFF];
        }
        return -1;
    }

    @Override
    public long findFirstByte(MemorySegment seg, long offset, long limit, byte target) {
        for (long i = offset; i < limit; i++) {
            if (seg.get(JAVA_BYTE, i) == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 选锚点：取频率最低的字节，并列时取其最后一次出现（靠后的锚点让更多模式字节参与坏字符表）。
     */
    static int selectAnchorIndex(byte[] pattern) {
        int best = -1;
        int bestFreq = Integer.MAX_VALUE;
        for (int i = 0; i < pattern.length; i++) {
            int f = BYTE_FREQUENCY[pattern[i] & 0xFF];
            if (f <= bestFreq) {
                bestFreq = f;
                best = i;
            }
        }
        return best;
    }

    /**
     * 坏字符跳跃表：mismatch 字节 c 时，把模式中锚点之前最后一次出现的 c 对齐到当前窗口位置；
     * c 不在锚点之前（含不在模式中）时跳跃 anchorIdx + 1（锚点越过当前窗口，Horspool 论证保证不漏配）。
     */
    static long[] buildSkipTable(byte[] pattern, int anchorIdx) {
        long[] skip = new long[256];
        Arrays.fill(skip, anchorIdx + 1L);
        for (int p = 0; p < anchorIdx; p++) {
            skip[pattern[p] & 0xFF] = anchorIdx - p;
        }
        return skip;
    }

    private boolean matches(MemorySegment seg, long start, byte[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            if (seg.get(JAVA_BYTE, start + i) != pattern[i]) {
                return false;
            }
        }
        return true;
    }
}
