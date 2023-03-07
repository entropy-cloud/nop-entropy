/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.bit;

import io.nop.api.core.util.Guard;

public class SmallBitSet implements IBitSet {
    private static final long WORD_MASK = 0xffffffffffffffffL;
    private final static int ADDRESS_BITS_PER_WORD = 6;
    private final static int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;

    private long word;

    private SmallBitSet(long word) {
        this.word = word;
    }

    public SmallBitSet() {
        word = 0;
    }

    @Override
    public void set(int bitIndex) {
        Guard.checkPositionIndex(bitIndex, Long.SIZE);
        word |= 1L << bitIndex;
    }

    @Override
    public void set(int fromIndex, int toIndex) {
        Guard.checkPositionIndex(fromIndex, Long.SIZE, "invalid fromIndex");
        Guard.checkPositionIndex(toIndex, Long.SIZE, "invalid toIndex");

        long firstWordMask = WORD_MASK << fromIndex;
        long lastWordMask = WORD_MASK >>> -toIndex;
        word |= (firstWordMask & lastWordMask);
    }

    @Override
    public boolean get(int bitIndex) {
        return (word & (1L << bitIndex)) != 0;
    }

    @Override
    public int count(int fromIndex, int toIndex) {
        int cnt = 0;
        for (int i = toIndex; (i = previousSetBit(i - 1)) >= fromIndex; ) {
            cnt++;
        }
        return cnt;
    }

    @Override
    public void clear(int bitIndex) {
        Guard.checkPositionIndex(bitIndex, Long.SIZE);
        word &= ~(1L << bitIndex);
    }

    @Override
    public void clear(int fromIndex, int toIndex) {
        Guard.checkPositionIndex(fromIndex, Long.SIZE, "invalid fromIndex");
        Guard.checkPositionIndex(toIndex, Long.SIZE, "invalid toIndex");

        long firstWordMask = WORD_MASK << fromIndex;
        long lastWordMask = WORD_MASK >>> -toIndex;
        word &= ~(firstWordMask & lastWordMask);
    }

    @Override
    public void clear() {
        word = 0;
    }

    @Override
    public int cardinality() {
        return Long.bitCount(word);
    }

    @Override
    public int size() {
        return Long.SIZE;
    }

    @Override
    public boolean isEmpty() {
        return word == 0;
    }

    @Override
    public void flip(int bitIndex) {
        Guard.checkPositionIndex(bitIndex, Long.SIZE);
        word ^= (1L << bitIndex);
    }

    @Override
    public void flip(int fromIndex, int toIndex) {
        Guard.checkPositionIndex(fromIndex, Long.SIZE, "invalid fromIndex");
        long firstWordMask = WORD_MASK << fromIndex;
        long lastWordMask = WORD_MASK >>> -toIndex;
        word ^= (firstWordMask & lastWordMask);
    }

    SmallBitSet toSmallBitSet(IBitSet bs) {
        return (SmallBitSet) bs;
    }

    @Override
    public void and(IBitSet bs) {
        SmallBitSet sbs = toSmallBitSet(bs);
        word &= sbs.word;
    }

    @Override
    public void andNot(IBitSet bs) {
        SmallBitSet sbs = toSmallBitSet(bs);
        word &= ~sbs.word;
    }

    @Override
    public void or(IBitSet bs) {
        SmallBitSet sbs = toSmallBitSet(bs);
        word |= sbs.word;
    }

    @Override
    public void xor(IBitSet bs) {
        SmallBitSet sbs = toSmallBitSet(bs);
        word ^= sbs.word;
    }

    @Override
    public boolean intersects(IBitSet bs) {
        SmallBitSet sbs = toSmallBitSet(bs);
        return (word & sbs.word) != 0;
    }

    public int previousSetBit(int fromIndex) {
        if (fromIndex < 0) {
            return -1;
        }

        long w = word & (WORD_MASK >>> -(fromIndex + 1));
        if (w == 0)
            return -1;

        return BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(w);
    }

    @Override
    public int nextSetBit(int fromIndex) {
        long w = word & (WORD_MASK << fromIndex);

        if (w == 0)
            return -1;
        return Long.numberOfTrailingZeros(w);
    }

    @Override
    public int nextClearBit(int fromIndex) {
        long w = ~word & (WORD_MASK << fromIndex);
        if (w == 0)
            return -1;
        return Long.numberOfTrailingZeros(w);
    }

    @Override
    public IBitSet cloneInstance() {
        return new SmallBitSet(word);
    }
}