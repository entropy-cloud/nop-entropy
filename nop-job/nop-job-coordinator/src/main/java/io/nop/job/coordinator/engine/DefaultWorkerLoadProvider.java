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
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.core.NopJobCoreConstants;
import io.nop.job.dao.store.IJobTaskStore;
import io.nop.job.dao.store.WorkerReservedCost;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 从服务发现 + task 表聚合派生 WorkerLoad。
 * <p>
 * reserved 使用一条跨 worker GROUP BY SQL（{@link IJobTaskStore#sumReservedCostByWorker()}），
 * 按 instanceId join 到 discovery 返回的实例列表。
 * capacity 从 instance.metadata 读（复用 Plan 212 的 metadata key 约定）。
 */
public class DefaultWorkerLoadProvider implements IWorkerLoadProvider {

    private IDiscoveryClient discoveryClient;
    private IJobTaskStore taskStore;

    @Inject
    public void setDiscoveryClient(@Nullable IDiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Inject
    public void setTaskStore(IJobTaskStore taskStore) {
        this.taskStore = taskStore;
    }

    @Override
    public List<WorkerLoad> getWorkerLoads(String serviceName) {
        if (discoveryClient == null || serviceName == null || serviceName.isBlank()) {
            return Collections.emptyList();
        }

        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            return Collections.emptyList();
        }

        List<ServiceInstance> healthy = instances.stream()
                .filter(i -> i.isHealthy() && i.isEnabled())
                .collect(Collectors.toList());
        if (healthy.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, WorkerReservedCost> reservedMap = loadReservedByWorker();

        List<WorkerLoad> loads = new ArrayList<>(healthy.size());
        for (ServiceInstance instance : healthy) {
            WorkerLoad load = new WorkerLoad();
            load.setInstance(instance);
            load.setCapacity(resolveCapacity(instance));
            load.setReserved(resolveReserved(reservedMap, instance.getInstanceId()));
            loads.add(load);
        }
        return loads;
    }

    private Map<String, WorkerReservedCost> loadReservedByWorker() {
        List<WorkerReservedCost> rows = taskStore.sumReservedCostByWorker();
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, WorkerReservedCost> map = new HashMap<>(rows.size());
        for (WorkerReservedCost row : rows) {
            map.put(row.getWorkerInstanceId(), row);
        }
        return map;
    }

    private static ResourceVector resolveCapacity(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        if (metadata == null) {
            return ResourceVector.MAX_VALUE;
        }
        String cpuStr = metadata.get(NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU);
        String memStr = metadata.get(NopJobCoreConstants.METADATA_KEY_CAPACITY_MEMORY);
        if (cpuStr == null && memStr == null) {
            return ResourceVector.MAX_VALUE;
        }
        int cpu = parseCapacity(cpuStr);
        int memory = parseCapacity(memStr);
        return new ResourceVector(cpu, memory);
    }

    private static int parseCapacity(String value) {
        if (value == null || value.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static ResourceVector resolveReserved(Map<String, WorkerReservedCost> reservedMap, String workerInstanceId) {
        WorkerReservedCost row = reservedMap.get(workerInstanceId);
        if (row == null) {
            return ResourceVector.ZERO;
        }
        int cpu = row.getCpu() != null ? row.getCpu() : 0;
        int memory = row.getMemory() != null ? row.getMemory() : 0;
        return new ResourceVector(cpu, memory);
    }
}
