package io.nop.rg.vector;

import io.nop.rg.core.search.ByteSearchStrategy;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Vector API（SIMD）字节搜索策略（plan 2266 VEC-03，契约对齐实现——
 * 生产 coordinator 走 {@link VectorPreparedLiteral} 预编译形态，本类承载
 * {@code findFirstByte}（向量化 memchr）与逐调用 {@code findPattern} 契约）。
 *
 * <p>语义与标量一致：offset 绝对、limit exclusive、未找到 -1、空模式抛 IllegalArgumentException。
 */
public final class VectorByteSearcher implements ByteSearchStrategy {

    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;

    public static final VectorByteSearcher INSTANCE = new VectorByteSearcher();

    @Override
    public long findPattern(MemorySegment seg, long offset, long limit, byte[] pattern) {
        return new VectorPreparedLiteral(pattern, false).find(seg, offset, limit);
    }

    @Override
    public long findFirstByte(MemorySegment seg, long offset, long limit, byte target) {
        long vectorEnd = limit - SPECIES.length();
        long i = offset;
        while (i <= vectorEnd) {
            ByteVector word = ByteVector.fromMemorySegment(SPECIES, seg, i, ByteOrder.nativeOrder());
            VectorMask<Byte> mask = word.compare(VectorOperators.EQ, target);
            if (mask.anyTrue()) {
                return i + mask.firstTrue();
            }
            i += SPECIES.length();
        }
        for (; i < limit; i++) {
            if (seg.get(ValueLayout.JAVA_BYTE, i) == target) {
                return i;
            }
        }
        return -1;
    }
}
