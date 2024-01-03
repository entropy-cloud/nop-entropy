/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.iterator;

import io.nop.api.core.util.Guard;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class IntRangeIterator implements Iterator<Integer> {
    private int current;
    private final int end;
    private final int step;

    public IntRangeIterator(int begin, int end, int step) {
        Guard.checkArgument(step != 0, "step must not be zero");
        this.current = begin;
        this.end = end;
        this.step = step;
    }

    @Override
    public boolean hasNext() {
        if (step > 0)
            return current <= end;
        return current >= end;
    }

    @Override
    public Integer next() {
        if (!hasNext())
            throw new NoSuchElementException();

        int ret = current;
        current += step;
        return ret;
    }
}