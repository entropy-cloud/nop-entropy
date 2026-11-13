/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.lb;

import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.lb.impl.LeastActiveLoadBalance;
import io.nop.cluster.lb.impl.RandomLoadBalance;
import io.nop.cluster.lb.impl.RoundRobinLoadBalance;
import io.nop.cluster.lb.impl.ServiceLoadBalanceAdapter;
import io.nop.cluster.lb.impl.WeightedRandomLoadBalance;
import io.nop.commons.util.ArrayHelper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLoadBalance {

    private List<ServiceInstance> getItems(int n) {
        List<ServiceInstance> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ServiceInstance item = new ServiceInstance();
            item.setInstanceId(String.valueOf(i));
            item.setWeight(1);
            item.setPort(i);
            item.setServiceName(String.valueOf(i));
            items.add(item);
        }
        return items;
    }

    @Test
    public void testWeightedRR() {
        WeightedRandomLoadBalance<ServiceInstance, String> lb = new WeightedRandomLoadBalance<>(
                new ServiceLoadBalanceAdapter());
        int n = 3;

        List<ServiceInstance> items = getItems(n);

        int[] counts = new int[n];
        for (int i = 0; i <= 10000; i++) {
            int idx = lb.choose(items, "a").getPort();
            counts[idx]++;
        }

        System.out.println("weightedRandom=" + ArrayHelper.toList(counts));
        for (int count : counts) {
            assertTrue(count > 3000);
        }
    }

    @Test
    public void testWeightedRandom2() {
        WeightedRandomLoadBalance<ServiceInstance, String> lb = new WeightedRandomLoadBalance<>(
                new ServiceLoadBalanceAdapter());
        int n = 3;

        List<ServiceInstance> items = getItems(3);
        items.get(2).setWeight(2);

        int[] counts = new int[n];
        for (int i = 0; i <= 1000; i++) {
            int idx = lb.choose(items, "a").getPort();
            counts[idx]++;
        }

        System.out.println(ArrayHelper.toList(counts));
        for (int count : counts) {
            assertTrue(count > 200);
        }
        assertTrue(counts[2] > 400);
    }

    @Test
    public void testRandom() {
        RandomLoadBalance<ServiceInstance, String> lb = new RandomLoadBalance<>();
        int n = 3;

        List<ServiceInstance> items = getItems(3);

        int[] counts = new int[n];
        int[] list = new int[1000];
        int k = 0;
        for (int i = 0; i < 1000; i++) {
            int idx = lb.choose(items, "a").getPort();
            counts[idx]++;
            list[k++] = idx;
        }

        System.out.println("random=" + ArrayHelper.toList(counts));
        for (int count : counts) {
            assertTrue(count > 280);
        }
        System.out.println(ArrayHelper.toList(list));
    }

    @Test
    public void testRoundRobin() {
        RoundRobinLoadBalance<ServiceInstance, String> lb = new RoundRobinLoadBalance<>();
        int n = 3;

        List<ServiceInstance> items = getItems(3);

        int[] counts = new int[n];
        for (int i = 0; i <= 1000; i++) {
            int idx = lb.choose(items, "a").getPort();
            counts[idx]++;
        }

        System.out.println(ArrayHelper.toList(counts));
        for (int count : counts) {
            assertTrue(count > 300);
        }
    }

    /**
     * P1-4.4-C2（plan 2395）：LeastActive 窗口回绕取模——startIndex>0 时
     * startIndex+currentCursor 越过 n，未取模下标 items.get 直接越界
     * （修复前 IndexOutOfBoundsException 高概率复现）。
     */
    @Test
    public void testLeastActiveWrapsWithoutOverflow() {
        LeastActiveLoadBalance<ServiceInstance, String> lb = new LeastActiveLoadBalance<>(
                new ServiceLoadBalanceAdapter());
        int n = 3;
        List<ServiceInstance> items = getItems(n);

        // 活跃数恒 0（adapter 默认）→ 全体并列最小 → 等权随机分支，多轮必触发回绕窗口
        for (int round = 0; round < 1000; round++) {
            ServiceInstance picked = lb.choose(items, "a");
            assertTrue(items.contains(picked), "picked instance must be one of the candidates");
        }
    }

    /**
     * 4.2-MA-08（plan 2559 裁定 C/R25-1）：WeightedRandomLoadBalance 零和守护钉——
     * 全体 weight<=0 时按 max(1,weight) 下限参与随机，修复前 ttlWeight=0 →
     * nextInt(0) 抛 IllegalArgumentException（零和崩溃面）。
     */
    @Test
    public void testAllNonPositiveWeightsDoesNotCrash() {
        WeightedRandomLoadBalance<ServiceInstance, String> lb = new WeightedRandomLoadBalance<>(
                new ServiceLoadBalanceAdapter());
        List<ServiceInstance> items = getItems(3);
        items.get(0).setWeight(0);
        items.get(1).setWeight(-7);
        items.get(2).setWeight(0);

        // 修复前此处抛 IllegalArgumentException；修复后各实例按下限 weight=1 等概率
        int[] counts = new int[3];
        for (int i = 0; i < 3000; i++) {
            counts[lb.choose(items, "a").getPort()]++;
        }
        for (int count : counts) {
            assertTrue(count > 600, "floored weights must be equal-probability, counts="
                    + ArrayHelper.toList(counts));
        }
    }

    /**
     * 4.2-MA-08（plan 2559 裁定 C/R25-1）：0/-50/100 下限权重比 1:1:100 → 比例份额（非等分）。
     */
    @Test
    public void testNonPositiveWeightFlooredProportional() {
        WeightedRandomLoadBalance<ServiceInstance, String> lb = new WeightedRandomLoadBalance<>(
                new ServiceLoadBalanceAdapter());
        List<ServiceInstance> items = getItems(3);
        items.get(0).setWeight(0);
        items.get(1).setWeight(-50);
        items.get(2).setWeight(100);

        int[] counts = new int[3];
        for (int i = 0; i < 20000; i++) {
            counts[lb.choose(items, "a").getPort()]++;
        }
        // 下限 1:1:100 → ~98% 流量落 index2
        assertTrue(counts[2] > 18000, "proportional share must follow floored 1:1:100, counts="
                + ArrayHelper.toList(counts));
        assertTrue(counts[0] > 0, "floored instance must still receive traffic (R25-1 semantic)");
        assertTrue(counts[1] > 0, "floored instance must still receive traffic (R25-1 semantic)");
    }

    /**
     * 4.2-C8-001（plan 2559 裁定 G/R25-2）：LeastActive 使用真实活跃计数——
     * min-active 选择（可变活跃计数 stub）。
     */
    @Test
    public void testLeastActiveUsesRealActiveCount() {
        MutableActiveAdapter adapter = new MutableActiveAdapter();
        LeastActiveLoadBalance<ServiceInstance, String> lb = new LeastActiveLoadBalance<>(adapter);
        List<ServiceInstance> items = getItems(3);
        adapter.setActive("0", 5);
        adapter.setActive("1", 0);
        adapter.setActive("2", 0);

        int[] counts = new int[3];
        for (int i = 0; i < 2000; i++) {
            counts[lb.choose(items, "a").getPort()]++;
        }
        // 活跃数 5 的实例不得被选中；并列最小 0/1 之间等权随机
        assertEquals(0, counts[0], "instance with highest active count must never be chosen");
        assertTrue(counts[1] > 500, "min-active tie must be random, counts="
                + ArrayHelper.toList(counts));
        assertTrue(counts[2] > 500, "min-active tie must be random, counts="
                + ArrayHelper.toList(counts));
    }

    /**
     * 4.2-C8-001（plan 2559 裁定 G）子注记勘正：LeastActive sameWeight 判定原 `i > 0`
     * 中 i 是候选下标——当首个最小活跃候选下标非 0、后续候选恰为下标 0 时漏判。
     * 权重 [5,1,1,1] 全并列活跃：修复后走权重随机分支，下标 0 得 ~5/8 流量；
     * 缺陷态（i>0 漏判）下 ~43%。
     */
    @Test
    public void testLeastActiveSameWeightDetectionFix() {
        MutableActiveAdapter adapter = new MutableActiveAdapter();
        LeastActiveLoadBalance<ServiceInstance, String> lb = new LeastActiveLoadBalance<>(adapter);
        List<ServiceInstance> items = getItems(4);
        items.get(0).setWeight(5);
        items.get(1).setWeight(1);
        items.get(2).setWeight(1);
        items.get(3).setWeight(1);

        int[] counts = new int[4];
        for (int i = 0; i < 20000; i++) {
            counts[lb.choose(items, "a").getPort()]++;
        }
        assertTrue(counts[0] > 11000, "weighted random branch must favor weight=5 (~62.5%), counts="
                + ArrayHelper.toList(counts));
    }

    /**
     * 可变活跃计数 stub（裁定 G 载体）：按 instanceId 维护活跃数。
     */
    static class MutableActiveAdapter implements ILoadBalanceAdapter<ServiceInstance> {
        private final Map<String, Integer> active = new HashMap<>();

        void setActive(String instanceId, int count) {
            active.put(instanceId, count);
        }

        @Override
        public int getWeight(ServiceInstance candidate) {
            return candidate.getWeight();
        }

        @Override
        public int getActiveCount(ServiceInstance candidate) {
            return active.getOrDefault(candidate.getInstanceId(), 0);
        }
    }
}