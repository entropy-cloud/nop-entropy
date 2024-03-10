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

public class RandomLoadBalance<T, R> implements ILoadBalance<T, R> {

    @Override
    public T choose(List<T> items, R request) {
        int idx = MathHelper.random().nextInt(items.size());
        return items.get(idx);
    }
}