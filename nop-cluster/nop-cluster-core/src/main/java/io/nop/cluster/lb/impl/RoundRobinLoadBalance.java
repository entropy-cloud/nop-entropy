/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.lb.impl;

import io.nop.cluster.lb.ILoadBalance;
import io.nop.commons.util.MathHelper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinLoadBalance<T, R> implements ILoadBalance<T, R> {
    private final AtomicInteger seq = new AtomicInteger(0);

    @Override
    public T choose(List<T> items, R request) {
        int idx = seq.getAndIncrement();
        idx = MathHelper.nonNegativeMod(idx, items.size());

        T item = items.get(idx);
        return item;
    }
}