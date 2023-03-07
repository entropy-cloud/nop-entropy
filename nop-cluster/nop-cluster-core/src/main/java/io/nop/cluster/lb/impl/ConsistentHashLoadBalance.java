/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.lb.impl;

import io.nop.cluster.lb.ILoadBalance;
import io.nop.cluster.lb.ILoadBalanceHash;

import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistentHashLoadBalance<T, R> implements ILoadBalance<T, R> {
    private final ILoadBalanceHash<T, R> hashFunc;

    private volatile int replicaNumber = 160;

    public ConsistentHashLoadBalance(ILoadBalanceHash<T, R> hashFunc) {
        this.hashFunc = hashFunc;
    }

    public void setReplicaNumber(int replicaNumber) {
        this.replicaNumber = replicaNumber;
    }

    @Override
    public T choose(List<T> items, R request) {
        int hash = hashFunc.hashRequest(request);

        TreeMap<Integer, T> map = new TreeMap<>();
        int i, n = items.size();
        for (i = 0; i < n; i++) {
            T item = items.get(i);
            hashFunc.hashCandidate(item, replicaNumber, map);
        }

        SortedMap<Integer, T> tailMap = map.tailMap(hash);
        Iterator<T> it = tailMap.values().iterator();
        if (!it.hasNext()) {
            it = map.values().iterator();
        }
        T item = it.next();
        return item;
    }
}