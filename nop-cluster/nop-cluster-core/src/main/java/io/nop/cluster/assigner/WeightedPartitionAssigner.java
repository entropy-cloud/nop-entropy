/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.assigner;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.cluster.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WeightedPartitionAssigner implements IPartitionAssigner {
    static final Logger LOG = LoggerFactory.getLogger(WeightedPartitionAssigner.class);

    /** 非 0 权重 WARN 的 per-instance 频控窗口（毫秒）。 */
    static final long NON_POSITIVE_WEIGHT_WARN_INTERVAL_MS = 1000L;

    private static final ConcurrentHashMap<String, Long> NON_POSITIVE_WEIGHT_WARN_AT =
            new ConcurrentHashMap<>();

    @Override
    public List<IntRangeBean> assignPartitions(IntRangeBean range, List<ServiceInstance> servers) {
        if (servers.isEmpty()) {
            return Collections.emptyList();
        }

        if (servers.size() == 1) {
            return Collections.singletonList(range);
        }

        if (range.isEmpty()) {
            return servers.stream().map(s -> range).collect(Collectors.toList());
        }

        // 如果只有单个分区，则不能再进行分配
        if (range.isSingle()) {
            List<IntRangeBean> ret = new ArrayList<>();
            ret.add(range);
            for (int i = 1, n = servers.size(); i < n; i++) {
                ret.add(IntRangeBean.intRange(range.getEnd() - 1, 0));
            }
            return ret;
        }

        return assignWeighted(range, servers);
    }

    private List<IntRangeBean> assignWeighted(IntRangeBean range, List<ServiceInstance> servers) {
        int ttlWeight = getTotalWeight(servers);
        if(ttlWeight == 0)
            ttlWeight = 1;

        int offset = range.getOffset();
        int limit = range.getLimit();
        int end = range.getEnd();

        List<IntRangeBean> ret = new ArrayList<>(servers.size());
        for (int i = 0, n = servers.size(); i < n; i++) {
            ServiceInstance server = servers.get(i);
            if (offset >= end) {
                ret.add(IntRangeBean.intRange(offset, 0));
            } else {
                int subLimit = (int) Math.round((getWeight(server) * 1.0 / ttlWeight) * limit);
                // 确保至少分配 1 个（如果还有剩余）
                if (subLimit == 0 && offset < end) {
                    subLimit = 1;
                }
                if (i == n - 1) {
                    subLimit = end - offset;
                }
                ret.add(IntRangeBean.intRange(offset, subLimit));
                offset += subLimit;
            }
        }
        return ret;
    }

    private int getTotalWeight(List<ServiceInstance> servers) {
        int ttlWeight = 0;
        for (ServiceInstance server : servers) {
            ttlWeight += getWeight(server);
        }
        return ttlWeight;
    }

    private int getWeight(ServiceInstance server) {
        // 4.2-MA-08（plan 2559 裁定 C/R25-1 零权重下限语义）：weight<=0 一律按 max(1, weight)=1
        // 下限参与分配（原「≤0 改写 defaultWeight=100」的假保护即本缺陷根因）。
        // 语义后果（R25-1 显式声明）：weight<=0 不再表示「不接流量」——控制台 weight=0 摘流后
        // 该实例仍按 weight=1 获得比例份额；摘流须改用 enabled/healthy 等替代手段。
        int weight = server.getWeight();
        if (weight <= 0) {
            // R25-1：WARN 留痕原始值（载体断言项）。getWeight 处于分区分配循环内
            //（getTotalWeight + assignWeighted 每轮各调一次），per-server 1s 窗口频控，
            // 防止 zero-weight 实例在周期性分配下刷屏。
            throttleWarnNonPositiveWeight(server.getInstanceId(), weight);
            weight = 1;
        }
        return weight;
    }

    /**
     * per-instanceId 1s 窗口 WARN 频控：每实例首个窗口内首次与后续每窗口各告警一次，
     * 保留原始值留痕语义同时消除分区循环内的日志刷屏。
     */
    private void throttleWarnNonPositiveWeight(String instanceId, int weight) {
        long now = System.currentTimeMillis();
        String key = instanceId != null ? instanceId : "";
        Long lastWarnAt = NON_POSITIVE_WEIGHT_WARN_AT.get(key);
        if (lastWarnAt != null && now - lastWarnAt < NON_POSITIVE_WEIGHT_WARN_INTERVAL_MS) {
            return;
        }
        // get/put 竞态仅可能丢/多一条 WARN（频控面，可接受）
        NON_POSITIVE_WEIGHT_WARN_AT.put(key, now);
        LOG.warn("nop.cluster.assigner.non-positive-weight-floored:instanceId={},weight={} -> 1",
                instanceId, weight);
    }
}
