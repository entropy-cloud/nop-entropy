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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 4.2-C8-001（plan 2559 裁定 G/R25-2）：per-instance 活跃计数注册表（Dubbo beginCount/endCount 型
 * LongAdder 形态）。移除原 {@code getActiveCount} 恒返回 0 的假保护面。
 * <p>
 * 键 = instanceId 字符串（裁定 G(b)）：ServiceInstance 在事件更新时会重建对象（C1-001 面），
 * 按对象身份计数会即刻孤儿化，故必须按业务键计数。
 */
public class ServiceLoadBalanceAdapter implements ILoadBalanceAdapter<ServiceInstance> {
    private final ConcurrentHashMap<String, AtomicInteger> activeCounts = new ConcurrentHashMap<>();

    @Override
    public int getWeight(ServiceInstance candidate) {
        return candidate.getWeight();
    }

    @Override
    public int getActiveCount(ServiceInstance candidate) {
        AtomicInteger counter = activeCounts.get(keyOf(candidate));
        return counter == null ? 0 : counter.get();
    }

    @Override
    public void begin(ServiceInstance candidate) {
        // compute 与 end 的 computeIfPresent 同 key 串行化，避免「新建计数器 vs 摘除归零条目」竞态
        activeCounts.compute(keyOf(candidate), (k, counter) -> {
            if (counter == null) {
                counter = new AtomicInteger();
            }
            counter.incrementAndGet();
            return counter;
        });
    }

    @Override
    public void end(ServiceInstance candidate) {
        // 实例注销/事件重建路径的最小清理面（Minor-3）：cluster-core 无实例下线监听点，
        // 故在 end 处原子摘除已归零的条目——实例下线后其 in-flight 调用全部 end 时计数表
        // 自清，不再只增不减。computeIfPresent 保证同一 key 的 begin/end 串行化，无丢计数竞态。
        activeCounts.computeIfPresent(keyOf(candidate), (k, counter) ->
                counter.decrementAndGet() <= 0 ? null : counter);
    }

    private String keyOf(ServiceInstance candidate) {
        // instanceId 缺失时退化为共享桶 ""，不得让 ConcurrentHashMap 的 null 键 NPE 打断调用路径
        String id = candidate.getInstanceId();
        return id != null ? id : "";
    }
}
