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
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobScheduleStore;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds partitioned tasks using {@link WeightedPartitionAssigner} to split the
 * short-hash range [0, 32766] across selected workers by weight.
 * <p>
 * Each generated task carries a {@code partitionRange} string (IntRangeBean.toString()
 * format, e.g. "0,10922") that the business invoker can parse and use for SQL filtering:
 * {@code WHERE partition_index BETWEEN offset AND getLast()}.
 * <p>
 * Plan 339：不再内嵌 {@link DefaultJobTaskBuilder} fallback。serviceName 缺失 /
 * discoveryClient 未注入 / 无健康实例时由 {@link AbstractServiceTaskBuilder} 显式抛错
 * （ERR_JOB_SERVICE_NAME_REQUIRED / ERR_JOB_DISCOVERY_CLIENT_REQUIRED /
 * ERR_JOB_NO_AVAILABLE_INSTANCE），fire 留 DISPATCHING 等 timeout 回收。
 */
public class PartitionTaskBuilder extends AbstractServiceTaskBuilder {

    /**
     * AR-98: 覆盖完整 SMALLINT 哈希范围 [0, 32767]（含上界 32767）。{@code IntRangeBean.shortRange()}
     * 返回 [0, 32766]（off-by-one，丢哈希到 32767 的数据）；但 shortRange() 是被 nop-cluster
     * {@code PartitionAssignHelper.SHORT_HASH_RANGE} 等共享的方法，**禁止修改**（改它会跨模块漂移）。
     * 故在此局部定义覆盖全范围的常量。
     */
    private static final IntRangeBean PARTITION_HASH_RANGE = IntRangeBean.intRange(0, Short.MAX_VALUE + 1);

    private IPartitionAssigner partitionAssigner = new WeightedPartitionAssigner();
    private IJobScheduleStore scheduleStore;

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {
        this.scheduleStore = scheduleStore;
    }

    public void setPartitionAssigner(IPartitionAssigner partitionAssigner) {
        this.partitionAssigner = partitionAssigner;
    }

    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        String serviceName = requireServiceName(fire);
        List<ServiceInstance> healthyInstances = resolveHealthyInstances(serviceName);

        int partitionCount = resolvePartitionCount(fire);
        int n = partitionCount > 0 ? Math.min(partitionCount, healthyInstances.size()) : healthyInstances.size();
        List<ServiceInstance> selected = healthyInstances.subList(0, n);

        List<IntRangeBean> ranges = partitionAssigner.assignPartitions(
                PARTITION_HASH_RANGE, selected);

        List<NopJobTask> tasks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ServiceInstance instance = selected.get(i);
            IntRangeBean range = ranges.get(i);

            NopJobTask task = newTask(fire, i + 1);
            task.setWorkerInstanceId(instance.getInstanceId());
            task.setTargetHost(instance.getHost());
            task.setShardingIndex(i);
            task.setShardingTotal(n);
            task.setPartitionRange(range.toString());

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
