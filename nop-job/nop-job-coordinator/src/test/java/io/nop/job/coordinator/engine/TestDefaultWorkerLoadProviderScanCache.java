package io.nop.job.coordinator.engine;

import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.dao.store.IJobTaskStore;
import io.nop.job.dao.store.WorkerReservedCost;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.dao.entity.NopJobTask;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * AR-96：验证 {@link DefaultWorkerLoadProvider} 的 per-scan 缓存——在 beginScan..endScan 作用域内，
 * 对同一 serviceName 的多次 {@code getWorkerLoads} 调用只触发一次服务发现 + 一次聚合查询，
 * 不随 fire 数线性增长（batchSize=100 时避免 100 次发现 + 100 次 GROUP BY）。
 */
public class TestDefaultWorkerLoadProviderScanCache {

    private static ServiceInstance healthyInstance(String instanceId) {
        ServiceInstance inst = new ServiceInstance();
        inst.setInstanceId(instanceId);
        inst.setAddr(instanceId + "-host");
        inst.setPort(8080);
        inst.setHealthy(true);
        inst.setEnabled(true);
        return inst;
    }

    /** 计数服务发现调用次数。 */
    private static class CountingDiscoveryClient implements IDiscoveryClient {
        int getInstancesCount;
        private final List<ServiceInstance> instances;

        CountingDiscoveryClient(List<ServiceInstance> instances) {
            this.instances = instances;
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            getInstancesCount++;
            return instances;
        }

        @Override
        public List<String> getServices() {
            return Collections.emptyList();
        }
    }

    /** 计数 sumReservedCostByWorker 聚合查询次数。 */
    private static class CountingTaskStore implements IJobTaskStore {
        int sumReservedByWorkerCount;

        @Override
        public List<WorkerReservedCost> sumReservedCostByWorker() {
            sumReservedByWorkerCount++;
            WorkerReservedCost row = new WorkerReservedCost();
            row.setWorkerInstanceId("w1");
            row.setCpu(100);
            row.setMemory(200);
            return List.of(row);
        }

        // 以下方法本测试不关心
        @Override public boolean updateTask(NopJobTask task) { return false; }
        @Override public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions, String workerInstanceId, boolean enforceAttribution) { return Collections.emptyList(); }
        @Override public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) { return Collections.emptyList(); }
        @Override public List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobTask> findTasksByFireId(String jobFireId) { return Collections.emptyList(); }
        @Override public NopJobTask loadTask(String jobTaskId) { return null; }
        @Override public long countInFlightTasks(String workerInstanceId) { return 0; }
        @Override public ResourceVector sumReservedCost(String workerInstanceId) { return ResourceVector.ZERO; }
        @Override public int resetStaleWaitingTasks(int batchSize, IntRangeSet partitions, long deadlineMs) { return 0; }
    }

    /**
     * 一次 scan 内，对同一 serviceName 调用 getWorkerLoads N 次：发现 + 聚合各只执行一次
     *（不随 N 线性增长）。
     */
    @Test
    void perScanCacheReusesDiscoveryAndAggregationAcrossFires() {
        CountingDiscoveryClient discovery = new CountingDiscoveryClient(
                List.of(healthyInstance("w1"), healthyInstance("w2")));
        CountingTaskStore taskStore = new CountingTaskStore();
        DefaultWorkerLoadProvider provider = new DefaultWorkerLoadProvider();
        provider.setDiscoveryClient(discovery);
        provider.setTaskStore(taskStore);

        provider.beginScan();
        int fires = 10;
        List<WorkerLoad> first = null;
        for (int i = 0; i < fires; i++) {
            List<WorkerLoad> loads = provider.getWorkerLoads("svc");
            if (first == null) {
                first = loads;
            }
        }
        provider.endScan();

        assertEquals(1, discovery.getInstancesCount,
                "AR-96: discovery getInstances runs once per serviceName per scan, not once per fire");
        assertEquals(1, taskStore.sumReservedByWorkerCount,
                "AR-96: sumReservedCostByWorker aggregation runs once per scan, not once per fire");
        assertEquals(2, first.size(), "both healthy workers returned");
    }

    /**
     * 缓存仅作用于同一 scan：endScan 后再次调用应重新计算（缓存已被清空）。
     */
    @Test
    void cacheClearedAfterEndScanTriggersRecomputation() {
        CountingDiscoveryClient discovery = new CountingDiscoveryClient(
                List.of(healthyInstance("w1")));
        CountingTaskStore taskStore = new CountingTaskStore();
        DefaultWorkerLoadProvider provider = new DefaultWorkerLoadProvider();
        provider.setDiscoveryClient(discovery);
        provider.setTaskStore(taskStore);

        provider.beginScan();
        provider.getWorkerLoads("svc");
        provider.getWorkerLoads("svc"); // cached
        provider.endScan();

        assertEquals(1, discovery.getInstancesCount, "first scan: one discovery call");

        // new scan: cache cleared -> recomputation
        provider.beginScan();
        provider.getWorkerLoads("svc");
        provider.endScan();

        assertEquals(2, discovery.getInstancesCount,
                "AR-96: endScan clears cache, so a new scan recomputes (2 calls total)");
    }

    /**
     * 同一 scan 内不同 serviceName 各自独立计算一次（不互相缓存）。
     */
    @Test
    void distinctServiceNamesEachComputedOncePerScan() {
        CountingDiscoveryClient discovery = new CountingDiscoveryClient(
                List.of(healthyInstance("w1")));
        CountingTaskStore taskStore = new CountingTaskStore();
        DefaultWorkerLoadProvider provider = new DefaultWorkerLoadProvider();
        provider.setDiscoveryClient(discovery);
        provider.setTaskStore(taskStore);

        provider.beginScan();
        provider.getWorkerLoads("svc-a");
        provider.getWorkerLoads("svc-b");
        provider.getWorkerLoads("svc-a"); // cached
        provider.getWorkerLoads("svc-b"); // cached
        provider.endScan();

        assertEquals(2, discovery.getInstancesCount,
                "AR-96: 2 distinct service names -> 2 discovery calls (cached repeats reuse)");
        assertNotEquals(0, taskStore.sumReservedByWorkerCount);
    }
}
