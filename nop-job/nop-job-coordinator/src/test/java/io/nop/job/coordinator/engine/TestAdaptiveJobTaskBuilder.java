package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobScheduleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void testMissingLoadProviderThrowsException() {
        NopException ex = assertThrows(NopException.class, () -> new AdaptiveJobTaskBuilder().buildTasks(createFire("svc")));
        assertTrue(ex.getErrorCode().contains("worker-capacity-provider-required"));
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

    @Test
    void testAssignmentMetadataAndAssignmentCostAreMappedToTask() {
        builder.setLoadProvider(new MockLoadProvider(List.of()));
        builder.setStrategy((taskCost, workers) -> {
            Assignment assignment = new Assignment();
            assignment.setWorkerInstanceId("best-worker");
            assignment.setTargetHost("10.0.0.8:8080");
            assignment.setShardingIndex(2);
            assignment.setShardingTotal(5);
            assignment.setPartitionRange("100,50");
            assignment.setCost(new ResourceVector(1200, 2400));
            return new AssignmentPlan(List.of(assignment));
        });

        NopJobTask task = builder.buildTasks(createFire("svc")).get(0);
        assertEquals("best-worker", task.getWorkerInstanceId());
        assertEquals("10.0.0.8:8080", task.getTargetHost());
        assertEquals(2, task.getShardingIndex());
        assertEquals(5, task.getShardingTotal());
        assertEquals("100,50", task.getPartitionRange());
        assertEquals(1200, task.getCostCpu());
        assertEquals(2400, task.getCostMemory());
    }

    @Test
    void testAssignmentNullCostFallsBackToScheduleCost() {
        builder.setLoadProvider(new MockLoadProvider(List.of()));
        builder.setStrategy((taskCost, workers) -> {
            Assignment assignment = new Assignment();
            assignment.setWorkerInstanceId("best-worker");
            return new AssignmentPlan(List.of(assignment));
        });

        NopJobTask task = builder.buildTasks(createFire("svc")).get(0);
        assertEquals(500, task.getCostCpu());
        assertEquals(500, task.getCostMemory());
    }

    @Test
    void testMultipleAssignmentsFailFast() {
        builder.setLoadProvider(new MockLoadProvider(List.of()));
        builder.setStrategy((taskCost, workers) -> new AssignmentPlan(List.of(new Assignment(), new Assignment())));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> builder.buildTasks(createFire("svc")));
        assertTrue(ex.getMessage().contains("exactly one assignment"));
    }

    @Test
    void testNullAssignmentFailsFast() {
        builder.setLoadProvider(new MockLoadProvider(List.of()));
        builder.setStrategy((taskCost, workers) -> new AssignmentPlan(Collections.singletonList(null)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> builder.buildTasks(createFire("svc")));
        assertTrue(ex.getMessage().contains("null assignment"));
    }

    @Test
    void testBlankWorkerInstanceIdFailsFast() {
        builder.setLoadProvider(new MockLoadProvider(List.of()));
        builder.setStrategy((taskCost, workers) -> {
            Assignment assignment = new Assignment();
            assignment.setWorkerInstanceId("   ");
            return new AssignmentPlan(List.of(assignment));
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> builder.buildTasks(createFire("svc")));
        assertTrue(ex.getMessage().contains("workerInstanceId"));
    }

    @Test
    void testAssignmentPlanRetainsTypedAssignmentMetadata() {
        Assignment assignment = new Assignment();
        assignment.setWorkerInstanceId("worker-2");
        assignment.setTargetHost("10.0.0.2:8080");
        assignment.setShardingIndex(2);
        assignment.setShardingTotal(4);
        assignment.setPartitionRange("100,50");

        AssignmentPlan plan = new AssignmentPlan(new ArrayList<>(List.of(assignment)));
        Assignment actual = plan.getAssignments().get(0);

        assertEquals("worker-2", actual.getWorkerInstanceId());
        assertEquals("10.0.0.2:8080", actual.getTargetHost());
        assertEquals(2, actual.getShardingIndex());
        assertEquals(4, actual.getShardingTotal());
        assertEquals("100,50", actual.getPartitionRange());
    }

    @Test
    void testAssignmentJsonParsingRetainsTypedMetadata() {
        String json = "{"
                + "workerInstanceId:'worker-json',"
                + "targetHost:'10.0.0.9:8080',"
                + "shardingIndex:3,"
                + "shardingTotal:6,"
                + "partitionRange:'120,40',"
                + "cost:{cpu:700,memory:1400}"
                + "}";
        Assignment assignment = JsonTool.parseBeanFromText(json, Assignment.class);

        assertEquals("worker-json", assignment.getWorkerInstanceId());
        assertEquals("10.0.0.9:8080", assignment.getTargetHost());
        assertEquals(3, assignment.getShardingIndex());
        assertEquals(6, assignment.getShardingTotal());
        assertEquals("120,40", assignment.getPartitionRange());
        assertEquals(700, assignment.getCost().getCpu());
        assertEquals(1400, assignment.getCost().getMemory());
    }

    @Test
    void testAssignmentBeanToolBuildBeanRetainsTypedMetadata() {
        Assignment assignment = BeanTool.buildBean(Map.of(
                "workerInstanceId", "worker-map",
                "targetHost", "10.0.0.10:8080",
                "shardingIndex", 4,
                "shardingTotal", 8,
                "partitionRange", "160,20",
                "cost", Map.of("cpu", 900, "memory", 1800)
        ), Assignment.class);

        assertEquals("worker-map", assignment.getWorkerInstanceId());
        assertEquals("10.0.0.10:8080", assignment.getTargetHost());
        assertEquals(4, assignment.getShardingIndex());
        assertEquals(8, assignment.getShardingTotal());
        assertEquals("160,20", assignment.getPartitionRange());
        assertEquals(900, assignment.getCost().getCpu());
        assertEquals(1800, assignment.getCost().getMemory());
    }

    @Test
    void testAssignmentPlanEmptyOnNullList() {
        AssignmentPlan plan = new AssignmentPlan(null);
        assertTrue(plan.isEmpty());
    }

    @Test
    void testAssignmentInvalidMetadataTypeFailsLoudly() {
        Assignment assignment = new Assignment();
        assertThrows(ClassCastException.class, () -> assignment.setShardingIndex((Integer) (Object) Map.of("bad", true)));
    }

    /**
     * AR-99：serviceName 为非 String 类型（如数字）时不抛 ClassCastException，fallback 到 default builder。
     */
    @Test
    void testNonStringServiceNameDoesNotThrowCCE() {
        builder.setLoadProvider(new MockLoadProvider(List.of()));
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("f-ar99");
        fire.setJobScheduleId("s1");
        fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of("serviceName", 999)); // non-String

        List<NopJobTask> tasks = builder.buildTasks(fire);
        assertEquals(1, tasks.size(), "non-String serviceName must fallback to default builder (no CCE)");
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
