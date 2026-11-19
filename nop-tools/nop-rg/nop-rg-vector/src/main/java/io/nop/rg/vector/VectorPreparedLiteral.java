package io.nop.rg.vector;

import io.nop.rg.core.search.PreparedFinder;
import io.nop.rg.core.search.ScalarByteSearcher;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Arrays;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Vector API（SIMD）预编译字面量查找器（plan 2266 VEC-02/03）。
 *
 * <p>算法：模式锚点字节（最罕见字节启发式，与标量一致）向量化扫描——
 * 按 SPECIES 宽度加载、EQ 比较得候选掩码，候选处逐字节校验；校验失败推进 1（保守正确）。
 * 语义与标量 PreparedLiteral 完全一致：offset 绝对、limit exclusive、未找到 -1、空模式抛异常。
 */
final class VectorPreparedLiteral implements PreparedFinder {

    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;

    private final int patternLength;
    private final int anchorIdx;
    private final byte anchorByte;
    private final byte anchorByte2; // ignoreCase 时的另一大小写变体（无差异时等于 anchorByte）
    private final int pinIdx2;      // 第二锚点下标（<0 = 不可用：模式太短或全部字节相同）
    private final int pinDelta;     // 两锚点距离（pinIdx2 - anchorIdx）
    private final byte pinByte2;    // 第二锚点字节
    private final long[] skip;
    private final byte[] foldedPattern;
    private final boolean ignoreCase;

    VectorPreparedLiteral(byte[] pattern, boolean ignoreCase) {
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
        this.anchorByte2 = ignoreCase ? flipCase(anchorByte) : anchorByte;
        // Wave 5 item 18（roadmap，2026-11-19，ugrep pattern.cpp:415-560 思路）：
        // 选第二罕见字节作定点，两锚点同向量窗口内定距双比较，显著降低候选率
        this.pinIdx2 = ScalarByteSearcher.selectSecondAnchorIndex(foldedPattern, anchorIdx);
        this.pinDelta = pinIdx2 >= 0 ? pinIdx2 - anchorIdx : -1;
        this.pinByte2 = pinIdx2 >= 0 ? foldedPattern[pinIdx2] : anchorByte;
        this.skip = new long[256];
        Arrays.fill(skip, anchorIdx + 1L);
        for (int p = 0; p < anchorIdx; p++) {
            skip[foldedPattern[p] & 0xFF] = anchorIdx - p;
        }
    }

    @Override
    public long find(MemorySegment seg, long offset, long limit) {
        long firstInvalid = limit - patternLength + 1; // 窗口起点循环上界（exclusive）
        if (firstInvalid <= offset) {
            return -1;
        }

        // 向量化锚点扫描上界：锚点窗口 [s + anchorIdx] 需落在 [offset, limit) 内，
        // 且向量加载不得越过 limit - 1（lastValid = limit - 1 为最后一个可读字节）
        long vectorEnd = limit - SPECIES.length();
        boolean dualPin = pinDelta >= 1 && pinDelta < SPECIES.length();
        long dualEnd = vectorEnd - (dualPin ? pinDelta : 0);
        long s = offset;
        while (s < firstInvalid) {
            long probe = s + anchorIdx;
            if (dualPin && probe <= dualEnd) {
                // 双锚点定距比较（ugrep 思路）：两掩码 AND 后命中才产生候选
                ByteVector word = ByteVector.fromMemorySegment(SPECIES, seg, probe, ByteOrder.nativeOrder());
                ByteVector word2 = ByteVector.fromMemorySegment(SPECIES, seg, probe + pinDelta, ByteOrder.nativeOrder());
                VectorMask<Byte> mask = word.compare(VectorOperators.EQ, anchorByte);
                if (anchorByte2 != anchorByte) {
                    mask = mask.or(word.compare(VectorOperators.EQ, anchorByte2));
                }
                VectorMask<Byte> mask2 = word2.compare(VectorOperators.EQ, pinByte2);
                if (ignoreCase) {
                    byte flip = flipCase(pinByte2);
                    if (flip != pinByte2) {
                        mask2 = mask2.or(word2.compare(VectorOperators.EQ, flip));
                    }
                }
                mask = mask.and(mask2);
                if (mask.anyTrue()) {
                    int lane = mask.firstTrue();
                    long candidate = probe + lane - anchorIdx;
                    if (candidate >= s && candidate < firstInvalid && matchesAt(seg, candidate)) {
                        return candidate;
                    }
                    // 候选无效：用坏字符表保守推进（从锚点位置起算，语义与标量一致）
                    byte c = readByte(seg, probe + lane);
                    long step = skip[c & 0xFF];
                    s = Math.max(s + 1, probe + lane - anchorIdx + step);
                    continue;
                }
                // 本向量窗口无双锚点同时命中：整窗口跳过
                s = probe + SPECIES.length() - anchorIdx;
                continue;
            }
            if (probe <= vectorEnd) {
                ByteVector word = ByteVector.fromMemorySegment(SPECIES, seg, probe, ByteOrder.nativeOrder());
                VectorMask<Byte> mask = word.compare(VectorOperators.EQ, anchorByte);
                if (anchorByte2 != anchorByte) {
                    mask = mask.or(word.compare(VectorOperators.EQ, anchorByte2));
                }
                if (mask.anyTrue()) {
                    int lane = mask.firstTrue();
                    long candidate = probe + lane - anchorIdx;
                    if (candidate >= s && candidate < firstInvalid && matchesAt(seg, candidate)) {
                        return candidate;
                    }
                    // 候选无效：用坏字符表保守推进（从锚点位置起算，语义与标量一致）
                    byte c = readByte(seg, probe + lane);
                    long step = skip[c & 0xFF];
                    s = Math.max(s + 1, probe + lane - anchorIdx + step);
                    continue;
                }
                // 本向量窗口无锚点：整窗口跳过
                s = probe + SPECIES.length() - anchorIdx;
                continue;
            }
            // 尾部（不足一个向量宽度）：标量扫描
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
        byte b = seg.get(ValueLayout.JAVA_BYTE, pos);
        return ignoreCase ? fold(b) : b;
    }

    private static byte fold(byte b) {
        return (b >= 'A' && b <= 'Z') ? (byte) (b + 32) : b;
    }

    private static byte flipCase(byte b) {
        if (b >= 'A' && b <= 'Z') return (byte) (b + 32);
        if (b >= 'a' && b <= 'z') return (byte) (b - 32);
        return b;
    }

    @Override
    public int patternLength() {
        return patternLength;
    }
}
