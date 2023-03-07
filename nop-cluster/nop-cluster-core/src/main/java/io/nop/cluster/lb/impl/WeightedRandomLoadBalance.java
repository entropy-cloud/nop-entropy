/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.lb.impl;

import io.nop.cluster.lb.ILoadBalance;
import io.nop.cluster.lb.ILoadBalanceAdapter;
import io.nop.commons.util.MathHelper;

import java.util.List;

public class WeightedRandomLoadBalance<T, R> implements ILoadBalance<T, R> {
    private final ILoadBalanceAdapter<T> adapter;

    public WeightedRandomLoadBalance(ILoadBalanceAdapter<T> adapter) {
        this.adapter = adapter;
    }

    @Override
    public T choose(List<T> items, R request) {
        // 生成的随机数范围在[0, ttlWeight)之间
        int[] weights = getWeights(items);
        int ttlWeight = sum(weights);
        int rnd = MathHelper.random().nextInt(ttlWeight);
        int ttl = 0;
        for (int i = 0, n = items.size(); i < n; i++) {
            ttl += weights[i];
            // 设置为>=是不正确的，会导致0的计数增加
            if (ttl > rnd) {
                return items.get(i);
            }
        }
        return items.get(items.size() - 1);
    }

    private int[] getWeights(List<T> items) {
        int[] weights = new int[items.size()];
        int i, n = items.size();
        for (i = 0; i < n; i++) {
            weights[i] = adapter.getWeight(items.get(i));
        }
        return weights;
    }

    private int sum(int[] weights) {
        int ttl = 0;
        for (int w : weights) {
            ttl += w;
        }
        return ttl;
    }
}