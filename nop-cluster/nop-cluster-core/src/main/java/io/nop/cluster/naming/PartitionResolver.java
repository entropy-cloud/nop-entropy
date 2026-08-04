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
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.time.CoreMetrics;
import io.nop.cluster.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 通用的集群分区解析器。将"当前服务负责哪些数据分区"的解析逻辑从业务模块（nop-job、nop-retry 等）
 * 上提至 nop-cluster-core，避免每个调度类重复实现。
 * <p>
 * 解析优先级：
 * <ol>
 *     <li>显式配置的 {@code assignedPartitions} 非空时直接返回（用于禁用集群模式或固定分区）；</li>
 *     <li>{@code enableCluster} 为 true 且 {@code namingService} 可用时，从 NamingService 获取实例列表，
 *         按 instanceId 排序，用 {@link PartitionAssignHelper#getMyRange} 计算本实例负责的分区区间，
 *         结果按 {@link IntRangeBean#toRangeSet()} 包装为 {@link IntRangeSet}；</li>
 *     <li>其余情况返回 null（调用方据此不追加分区过滤条件，扫描全表）。</li>
 * </ol>
 * <p>
 * 集群解析结果按 {@link #CACHE_TTL_MS} 缓存，避免高频扫描时反复查询 NamingService。
 * <p>
 * 子类（如 nop-job 的 {@code JobPartitionResolver}）只需通过 {@code @InjectValue} 绑定本模块的缺省配置。
 */
public class PartitionResolver {
    static final Logger LOG = LoggerFactory.getLogger(PartitionResolver.class);

    private static final long CACHE_TTL_MS = 10_000;

    private INamingService namingService;
    private String serviceName;
    private boolean enableCluster;
    private IntRangeSet assignedPartitions;

    private volatile long lastResolveTime;
    private volatile IntRangeSet cachedPartitions;

    public void setNamingService(INamingService namingService) {
        this.namingService = namingService;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setEnableCluster(boolean enableCluster) {
        this.enableCluster = enableCluster;
    }

    /**
     * 显式指定本实例负责的分区集合（{@link IntRangeSet#parse(String)} 格式，如 "0,100|200,50"）。
     * 非空时优先级高于集群动态分配。
     */
    public void setAssignedPartitions(String partitions) {
        if (partitions != null && !partitions.isEmpty()) {
            this.assignedPartitions = IntRangeSet.parse(partitions);
        }
    }

    /**
     * 解析当前实例负责的分区集合。返回 null 表示不做分区过滤。
     */
    public IntRangeSet resolvePartitions() {
        if (assignedPartitions != null && !assignedPartitions.isEmpty()) {
            return assignedPartitions;
        }

        if (!enableCluster || namingService == null) {
            return null;
        }

        long now = CoreMetrics.currentTimeMillis();
        if (cachedPartitions != null && (now - lastResolveTime) < CACHE_TTL_MS) {
            return cachedPartitions;
        }

        String svcName = serviceName != null && !serviceName.isEmpty()
                ? serviceName : AppConfig.appName();
        List<ServiceInstance> servers = namingService.getInstances(svcName);
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        List<ServiceInstance> sorted = new ArrayList<>(servers);
        sorted.sort(Comparator.comparing(ServiceInstance::getInstanceId));

        String myInstanceId = AppConfig.hostId();
        IntRangeBean myRange = PartitionAssignHelper.getMyRange(sorted, myInstanceId);
        if (myRange.isEmpty()) {
            LOG.warn("nop.cluster.partition.my-instance-not-found:instanceId={}", myInstanceId);
            return null;
        }

        LOG.debug("nop.cluster.partition.resolved:range={}", myRange);
        IntRangeSet result = myRange.toRangeSet();
        cachedPartitions = result;
        lastResolveTime = now;
        return result;
    }
}
