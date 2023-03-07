/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.lb.impl;

import io.nop.cluster.lb.ILoadBalance;

import java.util.List;

public class FirstLoadBalance<T, R> implements ILoadBalance<T, R> {
    @Override
    public T choose(List<T> list, R request) {
        return list.get(0);
    }
}
