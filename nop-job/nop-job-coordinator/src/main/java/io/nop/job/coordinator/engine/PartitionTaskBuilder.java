/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.cluster.assigner.IPartitionAssigner;
import io.nop.cluster.assigner.WeightedPartitionAssigner;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobScheduleStore;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds partitioned tasks using {@link WeightedPartitionAssigner} to split the
 * short-hash range [0, 32766] across selected workers by weight.
 * <p>
 * Each generated task carries a {@code partitionRange} string (IntRangeBean.toString()
 * format, e.g. "0,10922") that the business invoker can parse and use for SQL filtering:
 * {@code WHERE partition_index BETWEEN offset AND getLast()}.
 * <p>
 * Falls back to {@link DefaultJobTaskBuilder} when no discovery client is available,
 * no service name is configured, or no healthy instances are found.
 */
public class PartitionTaskBuilder implements IJobTaskBuilder {

    /**
     * AR-98: 覆盖完整 SMALLINT 哈希范围 [0, 32767]（含上界 32767）。{@code IntRangeBean.shortRange()}
     * 返回 [0, 32766]（off-by-one，丢哈希到 32767 的数据）；但 shortRange() 是被 nop-cluster
     * {@code PartitionAssignHelper.SHORT_HASH_RANGE} 等共享的方法，**禁止修改**（改它会跨模块漂移）。
     * 故在此局部定义覆盖全范围的常量。
     */
    private static final IntRangeBean PARTITION_HASH_RANGE = IntRangeBean.intRange(0, Short.MAX_VALUE + 1);

    private IDiscoveryClient discoveryClient;
    private IPartitionAssigner partitionAssigner = new WeightedPartitionAssigner();
    private IJobScheduleStore scheduleStore;
    private final IJobTaskBuilder fallback = new DefaultJobTaskBuilder();

    @Inject
    public void setDiscoveryClient(@Nullable IDiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {
        this.scheduleStore = scheduleStore;
    }

    public void setPartitionAssigner(IPartitionAssigner partitionAssigner) {
        this.partitionAssigner = partitionAssigner;
    }

    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        Map<String, Object> jobParams = fire.getJobParamsSnapshotComponent().get_jsonMap();
        if (jobParams == null) {
            return fallback.buildTasks(fire);
        }

        String serviceName = IJobTaskBuilder.resolveServiceName(jobParams);
        if (serviceName == null || serviceName.isBlank()) {
            return fallback.buildTasks(fire);
        }

        if (discoveryClient == null) {
            return fallback.buildTasks(fire);
        }

        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            return fallback.buildTasks(fire);
        }

        List<ServiceInstance> healthyInstances = instances.stream()
                .filter(instance -> instance.isHealthy() && instance.isEnabled())
                .collect(Collectors.toList());
        if (healthyInstances.isEmpty()) {
            return fallback.buildTasks(fire);
        }

        int partitionCount = resolvePartitionCount(fire);
        int n = partitionCount > 0 ? Math.min(partitionCount, healthyInstances.size()) : healthyInstances.size();
        List<ServiceInstance> selected = healthyInstances.subList(0, n);

        List<IntRangeBean> ranges = partitionAssigner.assignPartitions(
                PARTITION_HASH_RANGE, selected);

        long now = System.currentTimeMillis();
        List<NopJobTask> tasks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ServiceInstance instance = selected.get(i);
            IntRangeBean range = ranges.get(i);

            NopJobTask task = new NopJobTask();
            task.setJobFireId(fire.getJobFireId());
            task.setTaskNo(i + 1);
            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_WAITING);
            task.setWorkerInstanceId(instance.getInstanceId());
            task.setPartitionIndex(fire.getPartitionIndex());
            task.setShardingIndex(i);
            task.setShardingTotal(n);
            task.setPartitionRange(range.toString());

            task.setCreatedBy("system");
            task.setCreateTime(new Timestamp(now));
            task.setUpdatedBy("system");
            task.setUpdateTime(new Timestamp(now));

            tasks.add(task);
        }
        return tasks;
    }

    private int resolvePartitionCount(NopJobFire fire) {
        if (scheduleStore == null) {
            return 0;
        }
        NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
        if (schedule == null || schedule.getPartitionCount() == null) {
            return 0;
        }
        return schedule.getPartitionCount();
    }
}
