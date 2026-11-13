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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WeightedPartitionAssigner implements IPartitionAssigner {
    static final Logger LOG = LoggerFactory.getLogger(WeightedPartitionAssigner.class);

    /**
     * 上一轮分配中出现非法权重的实例 id -> 原始权重值。每轮整体替换（旧 map 随之消亡），
     * 条目有界于「本轮 server 集合」，实例下线后自然回收——无独立清理路径，
     * 也不残留时间窗频控方案中下线实例的时间戳条目。
     */
    private static volatile Map<String, Integer> nonPositiveWeightOffenders = Collections.emptyMap();

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
        Map<String, Integer> offenders = new HashMap<>();
        int ttlWeight = getTotalWeight(servers, offenders);
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
                int subLimit = (int) Math.round((getWeight(server, offenders) * 1.0 / ttlWeight) * limit);
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
        // 每轮收口：仅对上一轮未出现的非法权重实例 WARN（diff 告警），然后整体替换 map。
        // R25-1 载体断言项：WARN 消息留痕原始权重值。
        Map<String, Integer> prev = nonPositiveWeightOffenders;
        for (Map.Entry<String, Integer> entry : offenders.entrySet()) {
            if (!prev.containsKey(entry.getKey())) {
                LOG.warn("nop.cluster.assigner.non-positive-weight-floored:instanceId={},weight={} -> 1",
                        entry.getKey(), entry.getValue());
            }
        }
        nonPositiveWeightOffenders = offenders.isEmpty() ? Collections.emptyMap() : offenders;
        return ret;
    }

    private int getTotalWeight(List<ServiceInstance> servers, Map<String, Integer> offenders) {
        int ttlWeight = 0;
        for (ServiceInstance server : servers) {
            ttlWeight += getWeight(server, offenders);
        }
        return ttlWeight;
    }

    private int getWeight(ServiceInstance server, Map<String, Integer> offenders) {
        // 4.2-MA-08（plan 2559 裁定 C/R25-1 零权重下限语义）：weight<=0 一律按 max(1, weight)=1
        // 下限参与分配（原「≤0 改写 defaultWeight=100」的假保护即本缺陷根因）。
        // 语义后果（R25-1 显式声明）：weight<=0 不再表示「不接流量」——控制台 weight=0 摘流后
        // 该实例仍按 weight=1 获得比例份额；摘流须改用 enabled/healthy 等替代手段。
        int weight = server.getWeight();
        if (weight <= 0) {
            // R25-1：WARN 留痕原始值（载体断言项）。getWeight 处于分区分配循环内
            //（getTotalWeight + assignWeighted 每轮各调一次），offender map 轮内按 key 去重，
            // 消除 zero-weight 实例在周期性分配下的日志刷屏。
            String key = server.getInstanceId() != null ? server.getInstanceId() : "";
            offenders.putIfAbsent(key, weight);
            weight = 1;
        }
        return weight;
    }
}
