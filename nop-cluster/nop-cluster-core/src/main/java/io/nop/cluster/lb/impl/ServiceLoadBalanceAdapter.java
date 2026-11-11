/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.lb.impl;

import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.lb.ILoadBalanceAdapter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 4.2-C8-001（plan 2559 裁定 G/R25-2）：per-instance 活跃计数注册表（Dubbo beginCount/endCount 型
 * LongAdder 形态）。移除原 {@code getActiveCount} 恒返回 0 的假保护面。
 * <p>
 * 键 = instanceId 字符串（裁定 G(b)）：ServiceInstance 在事件更新时会重建对象（C1-001 面），
 * 按对象身份计数会即刻孤儿化，故必须按业务键计数。
 */
public class ServiceLoadBalanceAdapter implements ILoadBalanceAdapter<ServiceInstance> {
    private final ConcurrentHashMap<String, LongAdder> activeCounts = new ConcurrentHashMap<>();

    @Override
    public int getWeight(ServiceInstance candidate) {
        return candidate.getWeight();
    }

    @Override
    public int getActiveCount(ServiceInstance candidate) {
        LongAdder counter = activeCounts.get(keyOf(candidate));
        return counter == null ? 0 : counter.intValue();
    }

    @Override
    public void begin(ServiceInstance candidate) {
        cellFor(candidate).increment();
    }

    @Override
    public void end(ServiceInstance candidate) {
        cellFor(candidate).decrement();
    }

    private LongAdder cellFor(ServiceInstance candidate) {
        return activeCounts.computeIfAbsent(keyOf(candidate), k -> new LongAdder());
    }

    private String keyOf(ServiceInstance candidate) {
        // instanceId 缺失时退化为共享桶 ""，不得让 ConcurrentHashMap 的 null 键 NPE 打断调用路径
        String id = candidate.getInstanceId();
        return id != null ? id : "";
    }
}
