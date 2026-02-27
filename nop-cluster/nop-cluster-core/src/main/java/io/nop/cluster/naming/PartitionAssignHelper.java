/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.naming;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.cluster.assigner.IPartitionAssigner;
import io.nop.cluster.assigner.WeightedPartitionAssigner;
import io.nop.cluster.discovery.ServiceInstance;

import java.util.List;

/**
 * 分区分配帮助类。用于在集群环境中计算当前服务负责的分区范围。
 * <p>
 * 与 StringHelper.shortHash() 配对使用。
 * shortHash 返回 [0, Short.MAX_VALUE-1] 即 [0, 32766]。
 */
public class PartitionAssignHelper {

    /**
     * shortHash 的范围：[0, 32766]，共 32767 个值
     * 对应 MathHelper.toShortHash: Math.abs(hash) % Short.MAX_VALUE
     */
    public static final IntRangeBean SHORT_HASH_RANGE = IntRangeBean.shortRange();

    private static final IPartitionAssigner DEFAULT_ASSIGNER = new WeightedPartitionAssigner();

    /**
     * 使用默认分配器计算当前服务在 short 区间中负责的分区范围
     *
     * @param sortedServers 已按 instanceId 排序的服务实例列表
     * @param myInstanceId  当前服务的实例ID
     * @return 当前服务负责的分区范围，如果当前服务不在列表中则返回空
     */
    public static IntRangeBean getMyRange(List<ServiceInstance> sortedServers, String myInstanceId) {
        return getMyRange(sortedServers, myInstanceId, SHORT_HASH_RANGE, DEFAULT_ASSIGNER);
    }

    /**
     * 使用指定分配器计算当前服务在指定区间中负责的分区范围
     *
     * @param sortedServers 已按 instanceId 排序的服务实例列表
     * @param myInstanceId  当前服务的实例ID
     * @param range         分区范围
     * @param assigner      分区分配器
     * @return 当前服务负责的分区范围，如果当前服务不在列表中则返回空
     */
    public static IntRangeBean getMyRange(List<ServiceInstance> sortedServers, String myInstanceId,
                                          IntRangeBean range, IPartitionAssigner assigner) {
        if (sortedServers == null || sortedServers.isEmpty() || myInstanceId == null) {
            return IntRangeBean.intRange(0, 0);
        }

        // 找到当前服务的排名
        int myIndex = -1;
        for (int i = 0; i < sortedServers.size(); i++) {
            if (sortedServers.get(i).getInstanceId().equals(myInstanceId)) {
                myIndex = i;
                break;
            }
        }

        if (myIndex < 0) {
            return IntRangeBean.intRange(0, 0);
        }

        // 分配分区
        List<IntRangeBean> ranges = assigner.assignPartitions(range, sortedServers);
        return ranges.get(myIndex);
    }
}
