/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.coordinator.engine;

import io.nop.api.core.config.AppConfig;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds one NopJobTask per registered service instance for broadcast RPC.
 * Each task carries the target host, shardingIndex, and shardingTotal in its payload
 * so that the worker's execution context can inject the {@code nop-svc-target-host} header
 * for per-instance routing.
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

        String serviceName = (String) jobParams.get("serviceName");
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

        long now = System.currentTimeMillis();
        Map<String, Object> baseJobParamsSnapshot = emptyIfNull(jobParams);

        List<NopJobTask> tasks = new ArrayList<>();
        int total = instances.size();
        for (int i = 0; i < total; i++) {
            ServiceInstance instance = instances.get(i);

            NopJobTask task = new NopJobTask();
            task.setJobFireId(fire.getJobFireId());
            task.setTaskNo(i + 1);
            task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_WAITING);
            task.setWorkerInstanceId(AppConfig.hostId());
            task.setPartitionIndex(fire.getPartitionIndex());

            Map<String, Object> payload = new HashMap<>();
            payload.put("jobFireId", fire.getJobFireId());
            payload.put("jobParamsSnapshot", baseJobParamsSnapshot);
            payload.put("targetHost", instance.getAddr() + ":" + instance.getPort());
            payload.put("shardingIndex", i);
            payload.put("shardingTotal", total);
            task.getTaskPayloadComponent().set_jsonValue(payload);

            task.setCreatedBy("system");
            task.setCreateTime(new Timestamp(now));
            task.setUpdatedBy("system");
            task.setUpdateTime(new Timestamp(now));

            tasks.add(task);
        }
        return tasks;
    }

    private Map<String, Object> emptyIfNull(Map<String, Object> map) {
        return map == null ? Map.of() : map;
    }
}
