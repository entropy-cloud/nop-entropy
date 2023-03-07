/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.assigner;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.cluster.discovery.ServiceInstance;

import java.util.List;

/**
 * 为多个服务实例分配分区工作
 */
public interface IPartitionAssigner {
    /**
     * 根据服务实例的权重信息等对分区任务进行加权分配
     *
     * @param range   待分配的分区范围
     * @param servers 服务实例列表。
     * @return 分配给每个服务器的分区范围
     */
    List<IntRangeBean> assignPartitions(IntRangeBean range, List<ServiceInstance> servers);
}