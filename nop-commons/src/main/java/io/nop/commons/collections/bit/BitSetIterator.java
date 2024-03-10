/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.bit;

import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

public class BitSetIterator implements PrimitiveIterator.OfInt {
    private final IBitSet bs;
    private int next;

    public BitSetIterator(IBitSet bs) {
        this.bs = bs;

        next = bs.nextSetBit(0);
    }

    @Override
    public boolean hasNext() {
        return next != -1;
    }

    @Override
    public int nextInt() {
        if (next != -1) {
            int ret = next;
            next = bs.nextSetBit(next + 1);
            return ret;
        } else {
            throw new NoSuchElementException();
        }
    }
}