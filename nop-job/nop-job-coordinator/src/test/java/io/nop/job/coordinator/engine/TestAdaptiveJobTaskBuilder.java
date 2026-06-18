package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobScheduleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestAdaptiveJobTaskBuilder {

    private AdaptiveJobTaskBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new AdaptiveJobTaskBuilder();
        builder.setScheduleStore(new MockScheduleStore());
    }

    private NopJobFire createFire(String serviceName) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("f1");
        fire.setJobScheduleId("s1");
        fire.getJobParamsSnapshotComponent().set_jsonValue(
                serviceName != null ? Map.of("serviceName", serviceName) : Map.of());
        return fire;
    }

    @Test
    void testMissingServiceNameFallsBack() {
        builder.setLoadProvider(new MockLoadProvider(List.of()));
        NopJobFire fire = createFire(null);
        List<NopJobTask> tasks = builder.buildTasks(fire);
        assertEquals(1, tasks.size(), "Missing serviceName → fallback to DefaultJobTaskBuilder");
    }

    @Test
    void testNoFittingWorkerThrowsException() {
        builder.setLoadProvider(new MockLoadProvider(List.of())); // empty workers
        NopJobFire fire = createFire("svc");
        assertThrows(NopException.class, () -> builder.buildTasks(fire),
                "No fitting worker → NopException, not silent fallback");
    }

    @Test
    void testNormalAssignmentWritesWorkerInstanceId() {
        WorkerLoad load = new WorkerLoad();
        ServiceInstance inst = new ServiceInstance();
        inst.setInstanceId("best-worker");
        load.setInstance(inst);
        load.setCapacity(new ResourceVector(4000, 8000));
        load.setReserved(ResourceVector.ZERO);
        builder.setLoadProvider(new MockLoadProvider(List.of(load)));

        NopJobFire fire = createFire("svc");
        List<NopJobTask> tasks = builder.buildTasks(fire);

        assertEquals(1, tasks.size());
        assertEquals("best-worker", tasks.get(0).getWorkerInstanceId(),
                "Task workerInstanceId must be set to the assigned worker");
    }

    // === Mocks ===

    private static class MockLoadProvider implements IWorkerLoadProvider {
        private final List<WorkerLoad> loads;
        MockLoadProvider(List<WorkerLoad> loads) { this.loads = loads; }
        @Override public List<WorkerLoad> getWorkerLoads(String serviceName) { return loads; }
    }

    private static class MockScheduleStore implements IJobScheduleStore {
        @Override public NopJobSchedule loadSchedule(String jobScheduleId) {
            NopJobSchedule s = new NopJobSchedule();
            s.setJobScheduleId(jobScheduleId);
            s.setTaskCostCpu(500);
            s.setTaskCostMemory(500);
            s.setPriority(0);
            return s;
        }
        @Override public List<NopJobSchedule> fetchDueSchedules(int limit, io.nop.api.core.beans.IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> s, String p, long t) { return Collections.emptyList(); }
        @Override public void advanceScheduleAfterSkip(NopJobSchedule s, java.sql.Timestamp n) { }
        @Override public void insertFireAndAdvanceSchedule(NopJobSchedule s, NopJobFire f, java.sql.Timestamp n, Integer l) { }
        @Override public void overlayFireAndAdvanceSchedule(NopJobSchedule s, NopJobFire f, java.sql.Timestamp n, Integer l) { }
        @Override public void recoveryFireAndAdvanceSchedule(NopJobSchedule s, java.sql.Timestamp n) { }
        @Override public boolean insertManualFire(NopJobSchedule s, NopJobFire f) { return false; }
        @Override public NopJobSchedule tryLoadSchedule(String id) { return loadSchedule(id); }
        @Override public Map<String, NopJobSchedule> batchLoadSchedules(Set<String> ids) { return Collections.emptyMap(); }
        @Override public long getCurrentTime() { return System.currentTimeMillis(); }
    }
}
