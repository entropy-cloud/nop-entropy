/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.bit;

import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * 对于RoaringBitmap, java BitSet, Redis BitSet的封装
 */
public interface IBitSet {
    void set(int bitIndex);

    void set(int fromIndex, int toIndex);

    default void assign(int bitIndex, boolean b) {
        if (b) {
            set(bitIndex);
        } else {
            clear(bitIndex);
        }
    }

    boolean get(int bitIndex);

    int count(int fromIndex, int toIndex);

    void clear(int bitIndex);

    void clear(int fromIndex, int toIndex);

    void clear();

    /**
     * 设置为true的bit数
     *
     * @return
     */
    int cardinality();

    /**
     * 总共占据的比特位个数
     *
     * @return
     */
    int size();

    boolean isEmpty();

    void flip(int bitIndex);

    void flip(int fromIndex, int toIndex);

    void and(IBitSet bs);

    void andNot(IBitSet bs);

    void or(IBitSet bs);

    void xor(IBitSet bs);

    boolean intersects(IBitSet bs);

    int nextSetBit(int fromIndex);

    int nextClearBit(int fromIndex);

    IBitSet cloneInstance();

    default IBitSetOp staticOp() {
        return DefaultBitSetOp.INSTANCE;
    }

    default PrimitiveIterator.OfInt iterator() {
        return new BitSetIterator(this);
    }

    default void forEach(IntConsumer consumer) {
        iterator().forEachRemaining(consumer);
    }

    default IntStream stream() {
        return StreamSupport.intStream(
                () -> Spliterators.spliterator(iterator(), cardinality(),
                        Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.SORTED),
                Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.ORDERED | Spliterator.DISTINCT
                        | Spliterator.SORTED,
                false);
    }
}