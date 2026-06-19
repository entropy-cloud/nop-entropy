package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobScheduleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPartitionTaskBuilder {

    private PartitionTaskBuilder builder;
    private MockScheduleStore scheduleStore;

    @BeforeEach
    void setUp() {
        builder = new PartitionTaskBuilder();
        scheduleStore = new MockScheduleStore();
        builder.setScheduleStore(scheduleStore);
    }

    private NopJobFire createFire(String serviceName) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("f1");
        fire.setJobScheduleId("s1");
        fire.getJobParamsSnapshotComponent().set_jsonValue(
                serviceName != null ? Map.of("serviceName", serviceName) : Map.of());
        return fire;
    }

    private ServiceInstance createInstance(String instanceId, boolean healthy, boolean enabled) {
        ServiceInstance inst = new ServiceInstance();
        inst.setInstanceId(instanceId);
        inst.setAddr(instanceId + "-host");
        inst.setPort(8080);
        inst.setHealthy(healthy);
        inst.setEnabled(enabled);
        return inst;
    }

    private IDiscoveryClient mockClient(List<ServiceInstance> instances) {
        return new IDiscoveryClient() {
            @Override
            public List<ServiceInstance> getInstances(String serviceName) {
                return instances;
            }

            @Override
            public List<String> getServices() {
                return Collections.emptyList();
            }
        };
    }

    @Test
    void testMissingServiceNameFallsBack() {
        builder.setDiscoveryClient(mockClient(Collections.emptyList()));
        NopJobFire fire = createFire(null);
        List<NopJobTask> tasks = builder.buildTasks(fire);
        assertEquals(1, tasks.size(), "Should fallback to DefaultJobTaskBuilder when serviceName is missing");
    }

    @Test
    void testNullDiscoveryClientFallsBack() {
        builder.setDiscoveryClient(null);
        NopJobFire fire = createFire("test-svc");
        List<NopJobTask> tasks = builder.buildTasks(fire);
        assertEquals(1, tasks.size(), "Should fallback when discoveryClient is null");
    }

    @Test
    void testAllUnhealthyFallsBack() {
        builder.setDiscoveryClient(mockClient(List.of(
                createInstance("w1", false, true),
                createInstance("w2", true, false)
        )));
        NopJobFire fire = createFire("test-svc");
        List<NopJobTask> tasks = builder.buildTasks(fire);
        assertEquals(1, tasks.size(), "Should fallback when no healthy+enabled instances");
    }

    @Test
    void testThreeInstancesPartitionCount3() {
        List<ServiceInstance> instances = List.of(
                createInstance("w1", true, true),
                createInstance("w2", true, true),
                createInstance("w3", true, true)
        );
        builder.setDiscoveryClient(mockClient(instances));
        scheduleStore.partitionCount = 3;

        NopJobFire fire = createFire("test-svc");
        List<NopJobTask> tasks = builder.buildTasks(fire);

        assertEquals(3, tasks.size(), "partitionCount=3 → 3 tasks");

        int totalLimit = 0;
        int maxLast = -1;
        for (int i = 0; i < tasks.size(); i++) {
            NopJobTask task = tasks.get(i);
            assertEquals(i + 1, task.getTaskNo());
            assertEquals("w" + (i + 1), task.getWorkerInstanceId());
            assertEquals(3, task.getShardingTotal());
            assertEquals(i, task.getShardingIndex());
            assertNotNull(task.getPartitionRange());

            IntRangeBean range = IntRangeBean.parse(task.getPartitionRange());
            totalLimit += range.getLimit();
            maxLast = Math.max(maxLast, range.getLast());
        }
        assertEquals(Short.MAX_VALUE + 1, totalLimit,
                "AR-98: union of partition ranges must cover the full SMALLINT range [0, 32767]");
        assertEquals(Short.MAX_VALUE, maxLast,
                "AR-98: the upper boundary 32767 must be covered (was dropped before fix)");
    }

    /**
     * AR-98：覆盖完整 SMALLINT 范围（含 32767），且未修改共享 {@code IntRangeBean.shortRange()}。
     */
    @Test
    void testPartitionRangeCoversFullSmallintBoundary() {
        List<ServiceInstance> instances = List.of(createInstance("w1", true, true));
        builder.setDiscoveryClient(mockClient(instances));
        scheduleStore.partitionCount = 1;

        NopJobFire fire = createFire("test-svc");
        List<NopJobTask> tasks = builder.buildTasks(fire);

        IntRangeBean range = IntRangeBean.parse(tasks.get(0).getPartitionRange());
        assertEquals(0, range.getOffset());
        assertEquals(Short.MAX_VALUE, range.getLast(),
                "single partition must cover [0, 32767] inclusive (AR-98 boundary fix)");
        // guard: the shared shortRange() is unchanged ([0, 32766]); only PartitionTaskBuilder's local
        // constant was widened.
        assertEquals(32766, IntRangeBean.shortRange().getLast(),
                "shared IntRangeBean.shortRange() must remain [0, 32766] (not modified, no cross-module drift)");
    }

    /**
     * AR-99：serviceName 为非 String 类型（如 Integer）时不抛 ClassCastException，优雅 fallback。
     */
    @Test
    void testNonStringServiceNameDoesNotThrowCCE() {
        builder.setDiscoveryClient(mockClient(List.of(createInstance("w1", true, true))));
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("f-ar99");
        fire.setJobScheduleId("s1");
        fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of("serviceName", 12345)); // non-String

        List<NopJobTask> tasks = builder.buildTasks(fire);
        assertEquals(1, tasks.size(), "non-String serviceName must fallback (no CCE)");
    }

    @Test
    void testPartitionCount2With3Instances() {
        List<ServiceInstance> instances = List.of(
                createInstance("w1", true, true),
                createInstance("w2", true, true),
                createInstance("w3", true, true)
        );
        builder.setDiscoveryClient(mockClient(instances));
        scheduleStore.partitionCount = 2;

        NopJobFire fire = createFire("test-svc");
        List<NopJobTask> tasks = builder.buildTasks(fire);

        assertEquals(2, tasks.size(), "partitionCount=2 with 3 instances → take first 2");
        assertEquals("w1", tasks.get(0).getWorkerInstanceId());
        assertEquals("w2", tasks.get(1).getWorkerInstanceId());
    }

    @Test
    void testPartitionCountUnsetUsesAllHealthy() {
        List<ServiceInstance> instances = new ArrayList<>();
        instances.add(createInstance("w1", true, true));
        instances.add(createInstance("w2", true, true));
        builder.setDiscoveryClient(mockClient(instances));
        scheduleStore.partitionCount = 0;

        NopJobFire fire = createFire("test-svc");
        List<NopJobTask> tasks = builder.buildTasks(fire);

        assertEquals(2, tasks.size(), "partitionCount=0 → N = healthy instance count");
    }

    @Test
    void testPartitionRangeRoundTrip() {
        List<ServiceInstance> instances = List.of(
                createInstance("w1", true, true),
                createInstance("w2", true, true)
        );
        builder.setDiscoveryClient(mockClient(instances));
        scheduleStore.partitionCount = 2;

        NopJobFire fire = createFire("test-svc");
        List<NopJobTask> tasks = builder.buildTasks(fire);

        for (NopJobTask task : tasks) {
            String rangeStr = task.getPartitionRange();
            IntRangeBean parsed = IntRangeBean.parse(rangeStr);
            assertNotNull(parsed, "partitionRange must be parseable by IntRangeBean.parse()");
            assertEquals(rangeStr, parsed.toString(), "parse(toString(x)) == x must hold");
        }
    }

    private static class MockScheduleStore implements IJobScheduleStore {
        int partitionCount = 0;

        @Override
        public NopJobSchedule loadSchedule(String jobScheduleId) {
            NopJobSchedule schedule = new NopJobSchedule();
            schedule.setJobScheduleId(jobScheduleId);
            schedule.setPartitionCount(partitionCount);
            return schedule;
        }

        @Override public List<NopJobSchedule> fetchDueSchedules(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> schedules, String plannerInstanceId, long lockTimeoutMs) { return Collections.emptyList(); }
        @Override public void advanceScheduleAfterSkip(NopJobSchedule schedule, Timestamp nextFireTime) { }
        @Override public void insertFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime, Integer lastFireStatus) { }
        @Override public void overlayFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime, Integer lastFireStatus) { }
        @Override public void recoveryFireAndAdvanceSchedule(NopJobSchedule schedule, Timestamp nextFireTime) { }
        @Override public boolean insertManualFire(NopJobSchedule schedule, NopJobFire fire) { return false; }
        @Override public NopJobSchedule tryLoadSchedule(String jobScheduleId) { return loadSchedule(jobScheduleId); }
        @Override public Map<String, NopJobSchedule> batchLoadSchedules(Set<String> scheduleIds) { return Collections.emptyMap(); }
        @Override public long getCurrentTime() { return System.currentTimeMillis(); }
    }
}
