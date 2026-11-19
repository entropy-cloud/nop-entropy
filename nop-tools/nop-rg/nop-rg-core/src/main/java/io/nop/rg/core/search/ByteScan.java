package io.nop.rg.core.search;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * SWAR（SIMD Within A Register）字节扫描原语（roadmap Wave 5 item 16）。
 *
 * <p>以 8 字节 long 为单位扫描 MemorySegment，替代逐字节循环。语义与逐字节扫描完全一致：
 * offset 绝对、limit exclusive、未找到 -1。对齐处理：头部/尾部不足 8 字节时标量退化，
 * 中段用非对齐 8 字节读（JAVA_LONG_UNALIGNED，x86/arm64 均为单指令，无跨页额外代价）。
 *
 * <p>SWAR zero-byte 判定：{@code (v - 0x01..01) & ~v & 0x80..80}；任意目标字节先整体
 * XOR 归零再判定。命中 lane 用 {@link Long#numberOfTrailingZeros} 定位。
 */
public final class ByteScan {

    private ByteScan() {
    }

    private static final ValueLayout.OfLong JAVA_LONG_UNALIGNED = ValueLayout.JAVA_LONG_UNALIGNED;
    private static final long ONES = 0x0101010101010101L;
    private static final long HIGH = 0x8080808080808080L;

    /**
     * 在 [offset, limit) 内查找 target 字节首次出现位置；未找到 -1。
     */
    public static long findFirstByte(MemorySegment seg, long offset, long limit, byte target) {
        if (offset >= limit) {
            return -1;
        }
        long i = offset;
        // 头部：对齐到 8 字节边界前的标量段（最多 7 字节，避免读到 segment 末尾之外）
        long headEnd = Math.min(limit, ((offset + 7) & ~7L));
        for (; i < headEnd; i++) {
            if (seg.get(ValueLayout.JAVA_BYTE, i) == target) {
                return i;
            }
        }
        long xorMask = ONES * (target & 0xFFL);
        long vectorEnd = limit - 8;
        for (; i <= vectorEnd; i += 8) {
            long v = seg.get(JAVA_LONG_UNALIGNED, i) ^ xorMask;
            long hits = (v - ONES) & ~v & HIGH;
            if (hits != 0) {
                return i + Long.numberOfTrailingZeros(hits) / 8;
            }
        }
        // 尾部标量
        for (; i < limit; i++) {
            if (seg.get(ValueLayout.JAVA_BYTE, i) == target) {
                return i;
            }
        }
        return -1;
    }
}
