/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.bit;

import io.nop.api.core.util.Guard;

import java.util.BitSet;

public class DefaultBitSet implements IBitSet {
    private final BitSet bs;

    public DefaultBitSet(BitSet bs) {
        this.bs = bs;
    }

    public DefaultBitSet() {
        this(new BitSet());
    }

    @Override
    public void set(int bitIndex) {
        bs.set(bitIndex);
    }

    @Override
    public void set(int fromIndex, int toIndex) {
        bs.set(fromIndex, toIndex);
    }

    @Override
    public boolean get(int bitIndex) {
        return bs.get(bitIndex);
    }

    @Override
    public int count(int fromIndex, int toIndex) {
        int cnt = 0;
        for (int i = toIndex; (i = bs.previousSetBit(i - 1)) >= fromIndex; ) {
            cnt++;
        }
        return cnt;
    }

    @Override
    public void clear(int bitIndex) {
        bs.clear(bitIndex);
    }

    @Override
    public void clear(int fromIndex, int toIndex) {
        bs.clear(fromIndex, toIndex);
    }

    @Override
    public void clear() {
        bs.clear();
    }

    @Override
    public boolean isEmpty() {
        return bs.isEmpty();
    }

    @Override
    public int cardinality() {
        return bs.cardinality();
    }

    @Override
    public int size() {
        return bs.size();
    }

    @Override
    public void flip(int bitIndex) {
        bs.flip(bitIndex);
    }

    @Override
    public void flip(int fromIndex, int toIndex) {
        bs.flip(fromIndex, toIndex);
    }

    BitSet getBitSet(IBitSet bs) {
        Guard.checkArgument(bs instanceof DefaultBitSet, "not DefaultBitSet", bs.getClass());
        return ((DefaultBitSet) bs).bs;
    }

    @Override
    public void and(IBitSet bs) {
        this.bs.and(getBitSet(bs));
    }

    @Override
    public void andNot(IBitSet bs) {
        this.bs.andNot(getBitSet(bs));
    }

    @Override
    public void or(IBitSet bs) {
        this.bs.or(getBitSet(bs));
    }

    @Override
    public void xor(IBitSet bs) {
        this.bs.xor(getBitSet(bs));
    }

    @Override
    public boolean intersects(IBitSet bs) {
        return this.bs.intersects(getBitSet(bs));
    }

    @Override
    public int nextSetBit(int fromIndex) {
        return this.bs.nextSetBit(fromIndex);
    }

    @Override
    public int nextClearBit(int fromIndex) {
        return this.bs.nextClearBit(fromIndex);
    }

    @Override
    public IBitSet cloneInstance() {
        return new DefaultBitSet((BitSet) bs.clone());
    }
}