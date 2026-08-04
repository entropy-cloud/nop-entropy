package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.cluster.naming.PartitionResolver;

/**
 * nop-job 的分区解析器，仅负责把 {@code nop.job.cluster.*} 配置绑定到通用的
 * {@link PartitionResolver}。解析逻辑（assignedPartitions 优先 / NamingService 动态分配 /
 * 10s 缓存）全部继承自 {@link PartitionResolver}，避免与 nop-retry 等模块重复。
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
}
