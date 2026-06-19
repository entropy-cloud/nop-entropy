/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds one NopJobTask per registered service instance for broadcast RPC.
 * Each task carries the target host, shardingIndex, and shardingTotal as
 * entity columns so that the worker can read them via typed getters and
 * inject the {@code nop-svc-target-host} header for per-instance routing.
 * <p>
 * Falls back to {@link DefaultJobTaskBuilder} when no discovery client is available,
 * no service name is configured, or no instances are found.
 */
public class RpcBroadcastTaskBuilder implements IJobTaskBuilder {

    private IDiscoveryClient discoveryClient;
    private final IJobTaskBuilder fallback = new DefaultJobTaskBuilder();

    @Inject
    public void setDiscoveryClient(@Nullable IDiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
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

        long now = System.currentTimeMillis();

        List<NopJobTask> tasks = new ArrayList<>();
        int total = healthyInstances.size();
        for (int i = 0; i < total; i++) {
            ServiceInstance instance = healthyInstances.get(i);

            NopJobTask task = new NopJobTask();
            task.setJobFireId(fire.getJobFireId());
            task.setTaskNo(i + 1);
            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_WAITING);
            task.setWorkerInstanceId(instance.getInstanceId());
            task.setPartitionIndex(fire.getPartitionIndex());

            // Dispatch routing: columns instead of JSON payload
            task.setTargetHost(instance.getAddr() + ":" + instance.getPort());
            task.setShardingIndex(i);
            task.setShardingTotal(total);

            task.setCreatedBy("system");
            task.setCreateTime(new Timestamp(now));
            task.setUpdatedBy("system");
            task.setUpdateTime(new Timestamp(now));

            tasks.add(task);
        }
        return tasks;
    }
}
