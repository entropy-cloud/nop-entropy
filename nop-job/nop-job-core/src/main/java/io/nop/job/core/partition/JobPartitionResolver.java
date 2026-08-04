/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.core.partition;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.cluster.naming.PartitionResolver;

/**
 * nop-job 的分区解析器，仅负责把 {@code nop.job.cluster.*} 配置绑定到通用的
 * {@link PartitionResolver}。解析逻辑（assignedPartitions 优先 / NamingService 动态分配 /
 * 10s 缓存）全部继承自 {@link PartitionResolver}。
 * <p>
 * 下沉到 nop-job-core，使 job-local / job-worker / job-coordinator 在 classpath 上均可见，
 * 并在 {@code app-job-core.beans.xml} 注册为 {@code jobPartitionResolver} bean 供全局按 id 引用。
 */
public class JobPartitionResolver extends PartitionResolver {

    @InjectValue("@cfg:nop.job.cluster.service-name|")
    @Override
    public void setServiceName(String serviceName) {
        super.setServiceName(serviceName);
    }

    @InjectValue("@cfg:nop.job.cluster.enable-cluster|false")
    @Override
    public void setEnableCluster(boolean enableCluster) {
        super.setEnableCluster(enableCluster);
    }

    @InjectValue("@cfg:nop.job.cluster.assigned-partitions|")
    @Override
    public void setAssignedPartitions(String partitions) {
        super.setAssignedPartitions(partitions);
    }
}
