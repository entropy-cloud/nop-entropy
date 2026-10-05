/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 读写分离（plan 2319）负载均衡实现：round-robin / random / weight（weight缺省等权）。
 */
public final class ReadWriteLoadBalancers {

    private ReadWriteLoadBalancers() {
    }

    /**
     * 按配置名构造负载均衡器。未知名称抛出非法参数异常（快速失败，不静默回落round-robin）
     */
    public static IReadWriteLoadBalancer of(String type, Map<String, Integer> weights) {
        if (type == null)
            return roundRobin();
        switch (type) {
            case "round-robin":
                return roundRobin();
            case "random":
                return random();
            case "weight":
                return weight(weights);
            default:
                throw new IllegalArgumentException("unknown readwrite load-balancer type: " + type);
        }
    }

    /**
     * 轮转：以进程内计数器为起点旋转候选列表，连续选择得到a,b,c,a,b,c...序列
     */
    public static IReadWriteLoadBalancer roundRobin() {
        return new RoundRobinLoadBalancer();
    }

    public static IReadWriteLoadBalancer random() {
        return RandomLoadBalancer.INSTANCE;
    }

    /**
     * 加权随机：按权重（缺省1，等权）有放回降采样排序，首个元素按权重比例分布
     */
    public static IReadWriteLoadBalancer weight(Map<String, Integer> weights) {
        return new WeightLoadBalancer(weights);
    }

    static class RoundRobinLoadBalancer implements IReadWriteLoadBalancer {
        private final AtomicLong counter = new AtomicLong();

        @Override
        public List<ReplicaChannel> order(List<ReplicaChannel> candidates) {
            int n = candidates.size();
            int offset = (int) (counter.getAndIncrement() % n);
            List<ReplicaChannel> result = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                result.add(candidates.get((offset + i) % n));
            }
            return result;
        }
    }

    static class RandomLoadBalancer implements IReadWriteLoadBalancer {
        static final RandomLoadBalancer INSTANCE = new RandomLoadBalancer();

        @Override
        public List<ReplicaChannel> order(List<ReplicaChannel> candidates) {
            List<ReplicaChannel> result = new ArrayList<>(candidates);
            Collections.shuffle(result);
            return result;
        }
    }

    static class WeightLoadBalancer implements IReadWriteLoadBalancer {
        private final Map<String, Integer> weights;

        WeightLoadBalancer(Map<String, Integer> weights) {
            this.weights = weights == null ? Collections.emptyMap() : weights;
        }

        int weightOf(ReplicaChannel channel) {
            Integer w = weights.get(channel.getName());
            // 未配置权重的从库缺省等权（权重1）；非法权重（<=0）同样按1处理
            if (w == null || w <= 0)
                return 1;
            return w;
        }

        @Override
        public List<ReplicaChannel> order(List<ReplicaChannel> candidates) {
            List<ReplicaChannel> remaining = new ArrayList<>(candidates);
            List<ReplicaChannel> result = new ArrayList<>(candidates.size());
            ThreadLocalRandom random = ThreadLocalRandom.current();
            while (!remaining.isEmpty()) {
                long total = 0;
                for (ReplicaChannel channel : remaining) {
                    total += weightOf(channel);
                }
                long r = (long) (random.nextDouble() * total);
                ReplicaChannel picked = remaining.get(remaining.size() - 1);
                for (ReplicaChannel channel : remaining) {
                    r -= weightOf(channel);
                    if (r < 0) {
                        picked = channel;
                        break;
                    }
                }
                result.add(picked);
                remaining.remove(picked);
            }
            return result;
        }
    }
}
