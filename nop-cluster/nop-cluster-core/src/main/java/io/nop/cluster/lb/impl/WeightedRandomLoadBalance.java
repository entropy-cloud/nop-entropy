/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.lb.impl;

import io.nop.cluster.lb.ILoadBalance;
import io.nop.cluster.lb.ILoadBalanceAdapter;
import io.nop.commons.util.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WeightedRandomLoadBalance<T, R> implements ILoadBalance<T, R> {
    static final Logger LOG = LoggerFactory.getLogger(WeightedRandomLoadBalance.class);

    private final ILoadBalanceAdapter<T> adapter;

    public WeightedRandomLoadBalance(ILoadBalanceAdapter<T> adapter) {
        this.adapter = adapter;
    }

    @Override
    public T choose(List<T> items, R request) {
        // 4.2-MA-08（plan 2559 裁定 C/R25-1 零权重下限语义）：权重统一按 max(1, weight) 下限
        // 参与随机，杜绝 ttlWeight=0 时 nextInt(0) 抛 IllegalArgumentException（原零和崩溃面）。
        // 语义后果（R25-1 显式声明）：weight<=0 不再表示「不接流量」——控制台 weight=0 摘流后
        // 本 LB 仍按 weight=1 分配随机流量；摘流须改用 enabled/healthy 等替代手段。
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
            int weight = adapter.getWeight(items.get(i));
            if (weight <= 0) {
                // R25-1：下限 weight=1 参与分配 + WARN 留痕原始值（载体断言项）
                LOG.warn("nop.cluster.lb.non-positive-weight-floored:weight={} -> 1", weight);
                weight = 1;
            }
            weights[i] = weight;
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