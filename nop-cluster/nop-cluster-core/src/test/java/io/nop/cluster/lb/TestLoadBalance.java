/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.lb;

import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.lb.impl.RandomLoadBalance;
import io.nop.cluster.lb.impl.RoundRobinLoadBalance;
import io.nop.cluster.lb.impl.ServiceLoadBalanceAdapter;
import io.nop.cluster.lb.impl.WeightedRandomLoadBalance;
import io.nop.commons.util.ArrayHelper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLoadBalance {

    private List<ServiceInstance> getItems(int n) {
        List<ServiceInstance> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ServiceInstance item = new ServiceInstance();
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
}