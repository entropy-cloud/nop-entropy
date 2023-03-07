/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.record.impl;

import io.nop.api.core.util.Guard;
import io.nop.commons.record.IRecordInput;

public class LimitRecordInput<T> extends DelegateRecordInput<T> {
    private final long maxCount;

    public LimitRecordInput(IRecordInput<T> input, long maxCount) {
        super(input);
        this.maxCount = Guard.nonNegativeLong(maxCount, "maxCount");
    }

    public long getMaxCount() {
        return maxCount;
    }

    @Override
    public long getTotalCount() {
        long count = input.getTotalCount();
        if (count <= 0)
            return count;
        return Math.min(maxCount, count);
    }

    @Override
    public IRecordInput<T> limit(long maxCount) {
        if (this.maxCount <= maxCount)
            return this;

        return new LimitRecordInput<>(input, maxCount);
    }

    @Override
    public boolean hasNext() {
        return getReadCount() < maxCount && super.hasNext();
    }

    @Override
    public T next() {
        if (getReadCount() >= maxCount)
            throw new IllegalStateException("exceed limit");
        return input.next();
    }
}