/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.cluster.lb.impl;

// refactor from dubbo LeastActiveLoadBalance

import io.nop.cluster.lb.ILoadBalance;
import io.nop.cluster.lb.ILoadBalanceAdapter;
import io.nop.commons.util.MathHelper;

import java.util.List;

/**
 * 选择当前活动连接数最少的服务器
 *
 * @param <T> Server对象类型
 * @param <R> Request对象类型
 */
public class LeastActiveLoadBalance<T, R> implements ILoadBalance<T, R> {
    private final ILoadBalanceAdapter<T> adapter;

    private int maxCandidateCount = 10;

    public LeastActiveLoadBalance(ILoadBalanceAdapter<T> adapter) {
        this.adapter = adapter;
    }

    public void setMaxCandidateCount(int maxCandidateCount) {
        this.maxCandidateCount = maxCandidateCount;
    }

    @Override
    public T choose(List<T> items, R request) {
        int n = items.size();
        int startIndex = MathHelper.random().nextInt(n);

        int currentCursor = 0;

        int max = Math.min(n, maxCandidateCount);

        int leastActive = -1; // 最小的活跃数
        int leastCount = 0; // 相同最小活跃数的个数
        int[] leastIndexs = new int[max]; // 相同最小活跃数的下标

        int totalWeight = 0; // 总权重
        int firstWeight = 0; // 第一个权重，用于于计算是否相同
        boolean sameWeight = true; // 是否所有权重相同

        while (currentCursor < max) {
            int i = startIndex + currentCursor;
            T item = items.get(i % n);
            currentCursor++;

            int active = adapter.getActiveCount(item);// 活跃数
            int weight = adapter.getWeight(item); // 权重
            if (leastActive == -1 || active < leastActive) { // 发现更小的活跃数，重新开始
                leastActive = active; // 记录最小活跃数
                leastCount = 1; // 重新统计相同最小活跃数的个数
                leastIndexs[0] = i; // 重新记录最小活跃数下标
                totalWeight = weight; // 重新累计总权重
                firstWeight = weight; // 记录第一个权重
                sameWeight = true; // 还原权重相同标识
            } else if (active == leastActive) { // 累计相同最小的活跃数
                leastIndexs[leastCount++] = i; // 累计相同最小活跃数下标
                totalWeight += weight; // 累计总权重
                // 判断所有权重是否一样
                if (sameWeight && i > 0 && weight != firstWeight) {
                    sameWeight = false;
                }
            }
        }

        // assert(leastCount > 0)
        if (leastCount == 1) {
            // 如果只有一个最小则直接返回
            return items.get(leastIndexs[0]);
        }

        if (!sameWeight && totalWeight > 0) {
            // 如果权重不相同且权重大于0则按总权重数随机
            int offsetWeight = MathHelper.random().nextInt(totalWeight);
            // 并确定随机值落在哪个片断上
            for (int i = 0; i < leastCount; i++) {
                int leastIndex = leastIndexs[i];
                T item = items.get(leastIndex);
                offsetWeight -= adapter.getWeight(item);
                if (offsetWeight <= 0)
                    return items.get(leastIndex);
            }
        }

        // 如果权重相同或权重为0则均等随机
        return items.get(leastIndexs[MathHelper.random().nextInt(leastCount)]);
    }
}
