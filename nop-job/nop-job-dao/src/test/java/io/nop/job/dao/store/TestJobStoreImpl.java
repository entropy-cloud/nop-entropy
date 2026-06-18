package io.nop.job.dao.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobStoreImpl extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int FIRE_STATUS_CANCELED = 60;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_CANCELED = 60;
    private static final int TRIGGER_TYPE_FIXED_RATE = 2;
    private static final int TRIGGER_SOURCE_SCHEDULE = 1;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJobScheduleStore scheduleStore;

    @Inject
    IJobFireStore fireStore;

    @Inject
    IJobTaskStore taskStore;

    @Test
    public void testFetchAndLockSchedules() {
        NopJobSchedule schedule = newSchedule("schedule-1", "job-1");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        List<NopJobSchedule> dueSchedules = scheduleStore.fetchDueSchedules(10, IntRangeSet.parse("1"));
        assertEquals(1, dueSchedules.size());

        List<NopJobSchedule> locked = scheduleStore.tryLockSchedulesForPlan(dueSchedules, "planner-1", 1000);
        assertEquals(1, locked.size());
        assertNotNull(locked.get(0).getNextFireTime());
    }

    @Test
    public void testInsertFireAndTaskFlow() {
        NopJobSchedule schedule = newSchedule("schedule-2", "job-2");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("fire-1", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire, new Timestamp(System.currentTimeMillis() + 60000),
                FIRE_STATUS_WAITING);

        List<NopJobFire> waitingFires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        assertEquals(1, waitingFires.size());

        List<NopJobFire> lockedFires = fireStore.tryLockFiresForDispatch(waitingFires, "dispatcher-1", 1000);
        assertEquals(1, lockedFires.size());
        assertEquals("dispatcher-1", lockedFires.get(0).getDispatchInstanceId());

        NopJobTask task = newTask("task-1", fire);
        fireStore.insertTasksAndMarkFireDispatching(lockedFires.get(0), Collections.singletonList(task));

        NopJobTask savedTask = taskStore.loadTask("task-1");
        assertNotNull(savedTask);
        assertEquals(TASK_STATUS_WAITING, savedTask.getTaskStatus());

        NopJobFire savedFire = fireStore.loadFire("fire-1");
        assertEquals(FIRE_STATUS_RUNNING, savedFire.getFireStatus());
    }

    @Test
    public void testCanceledDispatchingFireDoesNotCreateTask() {
        NopJobSchedule schedule = newSchedule("schedule-3", "job-3");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("fire-2", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire, new Timestamp(System.currentTimeMillis() + 60000),
                FIRE_STATUS_WAITING);

        List<NopJobFire> waitingFires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        List<NopJobFire> lockedFires = fireStore.tryLockFiresForDispatch(waitingFires, "dispatcher-2", 1000);
        assertEquals(1, lockedFires.size());

        assertEquals(true, fireStore.cancelFire(fire.getJobFireId()));

        NopJobTask task = newTask("task-2", fire);
        fireStore.insertTasksAndMarkFireDispatching(lockedFires.get(0), Collections.singletonList(task));

        List<NopJobTask> tasks = taskStore.findTasksByFireId(fire.getJobFireId());
        assertEquals(0, tasks.size());

        NopJobFire savedFire = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_CANCELED, savedFire.getFireStatus());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(0, savedSchedule.getActiveFireCount());
        assertEquals(FIRE_STATUS_CANCELED, savedSchedule.getLastFireStatus());
    }

    @Test
    public void testDispatchStartTimeIsCurrentTime() {
        NopJobSchedule schedule = newSchedule("schedule-ar1", "job-ar1");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newFire("fire-ar1", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire,
                new Timestamp(System.currentTimeMillis() + 60000), FIRE_STATUS_WAITING);

        long lockTimeoutMs = 5000L;
        List<NopJobFire> waitingFires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        List<NopJobFire> lockedFires = fireStore.tryLockFiresForDispatch(waitingFires, "dispatcher-ar1", lockTimeoutMs);

        assertEquals(1, lockedFires.size());
        NopJobFire dispatched = lockedFires.get(0);

        long startTimeMs = dispatched.getStartTime().getTime();
        long approxNow = System.currentTimeMillis();

        assertTrue(Math.abs(startTimeMs - approxNow) < 5000,
                "startTime should be close to current time (dispatch time), not now + lockTimeoutMs");
    }

    @Test
    public void testRecoveryFireNoFailedFiresContainsAllFields() {
        NopJobSchedule schedule = newSchedule("schedule-ar2", "job-ar2");
        schedule.setRetryPolicyId("retry-abc");
        schedule.setJobParams("{\"p1\":\"v1\"}");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        scheduleStore.recoveryFireAndAdvanceSchedule(schedule,
                new Timestamp(System.currentTimeMillis() + 60000));

        List<NopJobFire> fires = fireStore.fetchWaitingFires(10, IntRangeSet.parse("1"));
        assertEquals(1, fires.size());

        NopJobFire recoveryFire = fires.get(0);
        assertEquals(_NopJobCoreConstants.TRIGGER_SOURCE_RECOVERY, recoveryFire.getTriggerSource());
        assertEquals("retry-abc", recoveryFire.getRetryPolicyId());
        assertNotNull(recoveryFire.getJobParamsSnapshot());
        assertTrue(recoveryFire.getJobParamsSnapshot().contains("p1"),
                "recovery fire should have jobParamsSnapshot from schedule");
        assertEquals(schedule.getExecutorKind(), recoveryFire.getExecutorKind());
    }

    // ============== sumReservedCost（Plan 212 Phase 2）==============

    /**
     * 无匹配 worker 时返回 ZERO（不抛异常，不返回 null）。
     */
    @Test
    public void testSumReservedCostEmptyReturnsZero() {
        ResourceVector result = taskStore.sumReservedCost("worker-empty");
        assertEquals(ResourceVector.ZERO, result);
    }

    /**
     * 单个 WAITING task：cost 完整计入 reserved。
     */
    @Test
    public void testSumReservedCostSingleWaitingTask() {
        NopJobSchedule schedule = newSchedule("schedule-src-1", "job-src-1");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);
        NopJobFire fire = newFire("fire-src-1", schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire,
                new Timestamp(System.currentTimeMillis() + 60000), FIRE_STATUS_WAITING);

        NopJobTask task = newTask("task-src-1", fire);
        task.setWorkerInstanceId("worker-src");
        task.setCostCpu(500);
        task.setCostMemory(1024);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);

        ResourceVector result = taskStore.sumReservedCost("worker-src");
        assertEquals(500, result.getCpu());
        assertEquals(1024, result.getMemory());
    }

    /**
     * 多个非终态 task 求和（WAITING + CLAIMED + SUSPICIOUS + RUNNING 全部计入）。
     */
    @Test
    public void testSumReservedCostMultipleActiveStatuses() {
        NopJobSchedule schedule = newSchedule("schedule-src-2", "job-src-2");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        String workerId = "worker-src-multi";

        saveCostTask("task-src-multi-w", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_WAITING, 100, 200);
        saveCostTask("task-src-multi-c", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_CLAIMED, 200, 300);
        saveCostTask("task-src-multi-s", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS, 300, 400);
        saveCostTask("task-src-multi-r", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_RUNNING, 400, 500);

        ResourceVector result = taskStore.sumReservedCost(workerId);
        // SUM = 100+200+300+400=1000 cpu, 200+300+400+500=1400 memory
        assertEquals(1000, result.getCpu());
        assertEquals(1400, result.getMemory());
    }

    /**
     * 终态 task（SUCCESS / FAILED / TIMEOUT / CANCELED）不计入 reserved。
     */
    @Test
    public void testSumReservedCostExcludesTerminalStatuses() {
        NopJobSchedule schedule = newSchedule("schedule-src-3", "job-src-3");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        String workerId = "worker-src-terminal";

        saveCostTask("task-src-term-r", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_RUNNING, 500, 1024);
        // 这些终态不应计入
        saveCostTask("task-src-term-s", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_SUCCESS, 9999, 9999);
        saveCostTask("task-src-term-f", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_FAILED, 9999, 9999);
        saveCostTask("task-src-term-t", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_TIMEOUT, 9999, 9999);
        saveCostTask("task-src-term-c", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_CANCELED, 9999, 9999);

        ResourceVector result = taskStore.sumReservedCost(workerId);
        assertEquals(500, result.getCpu());
        assertEquals(1024, result.getMemory());
    }

    /**
     * SUSPICIOUS(15) 必须计入 reserved（design §3.3.4 关键约定）。
     */
    @Test
    public void testSumReservedCostSuspiciousCounted() {
        NopJobSchedule schedule = newSchedule("schedule-src-4", "job-src-4");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        String workerId = "worker-src-suspicious";
        saveCostTask("task-src-susp", schedule, workerId, _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS, 700, 2048);

        ResourceVector result = taskStore.sumReservedCost(workerId);
        assertEquals(700, result.getCpu());
        assertEquals(2048, result.getMemory());
    }

    /**
     * 其它 worker 的 task 不应被计入本 worker 的 reserved。
     */
    @Test
    public void testSumReservedCostIsolatesByWorkerId() {
        NopJobSchedule schedule = newSchedule("schedule-src-5", "job-src-5");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        saveCostTask("task-src-iso-mine", schedule, "worker-mine", _NopJobCoreConstants.TASK_STATUS_RUNNING, 300, 600);
        saveCostTask("task-src-iso-other", schedule, "worker-other", _NopJobCoreConstants.TASK_STATUS_RUNNING, 9999, 9999);

        ResourceVector mine = taskStore.sumReservedCost("worker-mine");
        assertEquals(300, mine.getCpu());
        assertEquals(600, mine.getMemory());

        ResourceVector other = taskStore.sumReservedCost("worker-other");
        assertEquals(9999, other.getCpu());
        assertEquals(9999, other.getMemory());

        ResourceVector absent = taskStore.sumReservedCost("worker-absent");
        assertEquals(ResourceVector.ZERO, absent);
    }

    // ========== Plan 213 Phase 3: enforceAttribution filter tests ==========

    @Test
    void testFetchWaitingTasksEnforceAttributionFiltersByWorker() {
        NopJobSchedule schedule = newSchedule("sched-attrib", "job-attrib");

        NopJobTask myTask = newTask("task-mine", newFire("fire-mine", schedule));
        myTask.setWorkerInstanceId("worker-A");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(myTask);

        NopJobTask otherTask = newTask("task-other", newFire("fire-other", schedule));
        otherTask.setWorkerInstanceId("worker-B");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(otherTask);

        NopJobTask nullTask = newTask("task-null", newFire("fire-null", schedule));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(nullTask);

        List<NopJobTask> all = taskStore.fetchWaitingTasks(10, IntRangeSet.parse("1"));
        assertEquals(3, all.size(), "Without filter, all 3 WAITING tasks visible");

        List<NopJobTask> mine = taskStore.fetchWaitingTasks(
                10, IntRangeSet.parse("1"), "worker-A", true);
        assertEquals(2, mine.size(), "With enforceAttribution, only own + null-worker tasks visible");
        assertTrue(mine.stream().anyMatch(t -> "task-mine".equals(t.getJobTaskId())));
        assertTrue(mine.stream().anyMatch(t -> "task-null".equals(t.getJobTaskId())));
        assertTrue(mine.stream().noneMatch(t -> "task-other".equals(t.getJobTaskId())));
    }

    @Test
    void testFetchWaitingTasksEnforceAttributionFalseShowsAll() {
        NopJobSchedule schedule = newSchedule("sched-attrib2", "job-attrib2");

        NopJobTask myTask = newTask("task-mine2", newFire("fire-mine2", schedule));
        myTask.setWorkerInstanceId("worker-A");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(myTask);

        NopJobTask otherTask = newTask("task-other2", newFire("fire-other2", schedule));
        otherTask.setWorkerInstanceId("worker-B");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(otherTask);

        List<NopJobTask> result = taskStore.fetchWaitingTasks(
                10, IntRangeSet.parse("1"), "worker-A", false);
        assertEquals(2, result.size(), "enforceAttribution=false → competing-consumer, all tasks visible");
    }

    /**
     * Plan 213 Phase 3 scenario C: dedicated pool isolation.
     * A worker with enforceAttribution=true must NOT see single-mode tasks
     * (attributed to coordinator) — only its own partition tasks + unattributed tasks.
     */
    @Test
    void testDedicatedPoolIsolationScenarioC() {
        NopJobSchedule schedule = newSchedule("sched-pool-c", "job-pool-c");

        // Single-mode task attributed to coordinator (not to any worker)
        NopJobTask coordinatorTask = newTask("task-coordinator", newFire("fire-coord", schedule));
        coordinatorTask.setWorkerInstanceId("coordinator-host-1");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(coordinatorTask);

        // Partition-mode task attributed to worker-A
        NopJobTask workerATask = newTask("task-worker-a", newFire("fire-wa", schedule));
        workerATask.setWorkerInstanceId("worker-A");
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(workerATask);

        // Unattributed task (no workerInstanceId)
        NopJobTask nullTask = newTask("task-unattributed", newFire("fire-null-c", schedule));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(nullTask);

        // Worker-A with enforceAttribution=true: should see own + null, NOT coordinator's
        List<NopJobTask> visible = taskStore.fetchWaitingTasks(
                10, IntRangeSet.parse("1"), "worker-A", true);
        assertEquals(2, visible.size(), "Dedicated worker sees own partition task + unattributed task");
        assertTrue(visible.stream().anyMatch(t -> "task-worker-a".equals(t.getJobTaskId())));
        assertTrue(visible.stream().anyMatch(t -> "task-unattributed".equals(t.getJobTaskId())));
        assertTrue(visible.stream().noneMatch(t -> "task-coordinator".equals(t.getJobTaskId())),
                "Dedicated worker must NOT see single-mode coordinator-attributed task");
    }

    /**
     * Plan 214: priority-based ordering.
     * fetchWaitingTasks should return tasks ordered by priority DESC, then createTime ASC.
     */
    @Test
    void testFetchWaitingTasksOrderByPriority() {
        NopJobSchedule schedule = newSchedule("sched-prio", "job-prio");

        NopJobTask lowPrio = newTask("task-low", newFire("fire-low", schedule));
        lowPrio.setPriority(-5);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(lowPrio);

        NopJobTask normalPrio = newTask("task-normal", newFire("fire-normal", schedule));
        normalPrio.setPriority(0);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(normalPrio);

        NopJobTask highPrio = newTask("task-high", newFire("fire-high", schedule));
        highPrio.setPriority(10);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(highPrio);

        List<NopJobTask> result = taskStore.fetchWaitingTasks(10, IntRangeSet.parse("1"));
        assertEquals(3, result.size());
        assertEquals("task-high", result.get(0).getJobTaskId(), "Highest priority task should come first");
        assertEquals("task-normal", result.get(1).getJobTaskId(), "Normal priority (0) second");
        assertEquals("task-low", result.get(2).getJobTaskId(), "Low priority (-5) last");
    }

    /**
     * Plan 214: backward compat — all priority=0 should maintain FIFO (createTime ASC).
     */
    @Test
    void testFetchWaitingTasksPriorityZeroMaintainsFIFO() {
        NopJobSchedule schedule = newSchedule("sched-fifo", "job-fifo");

        NopJobTask task1 = newTask("task-fifo-1", newFire("fire-fifo-1", schedule));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task1);

        NopJobTask task2 = newTask("task-fifo-2", newFire("fire-fifo-2", schedule));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task2);

        List<NopJobTask> result = taskStore.fetchWaitingTasks(10, IntRangeSet.parse("1"));
        assertEquals(2, result.size());
        assertEquals("task-fifo-1", result.get(0).getJobTaskId(), "Same priority → FIFO by createTime");
        assertEquals("task-fifo-2", result.get(1).getJobTaskId());
    }

    private void saveCostTask(String taskId, NopJobSchedule schedule, String workerId,
                              int taskStatus, int cpu, int memory) {
        NopJobFire fire = newFire("fire-" + taskId, schedule);
        scheduleStore.insertFireAndAdvanceSchedule(schedule, fire,
                new Timestamp(System.currentTimeMillis() + 60000), FIRE_STATUS_WAITING);

        NopJobTask task = newTask(taskId, fire);
        task.setWorkerInstanceId(workerId);
        task.setTaskStatus(taskStatus);
        task.setCostCpu(cpu);
        task.setCostMemory(memory);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);
    }

    private NopJobSchedule newSchedule(String id, String jobName) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(id);
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName(jobName);
        schedule.setDisplayName(jobName);
        schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
        schedule.setExecutorKind("testInvoker");
        schedule.getJobParamsComponent().set_jsonValue(Map.of("k", "v"));
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
        schedule.setRepeatIntervalMs(1000L);
        schedule.setPartitionIndex((short) 1);
        schedule.setFireCount(0L);
        schedule.setActiveFireCount(0);
        schedule.setNextFireTime(new Timestamp(System.currentTimeMillis() - 1000));
        schedule.setVersion(0L);
        schedule.setCreatedBy("test");
        schedule.setCreateTime(new Timestamp(System.currentTimeMillis()));
        schedule.setUpdatedBy("test");
        schedule.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        return schedule;
    }

    private NopJobFire newFire(String id, NopJobSchedule schedule) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(id);
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(TRIGGER_SOURCE_SCHEDULE);
        fire.setScheduledFireTime(new Timestamp(System.currentTimeMillis()));
        fire.setFireStatus(FIRE_STATUS_WAITING);
        fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of("k", "v"));
        fire.setExecutorKind(schedule.getExecutorKind());
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(System.currentTimeMillis()));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        return fire;
    }

    private NopJobTask newTask(String id, NopJobFire fire) {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(id);
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(TASK_STATUS_WAITING);
        task.getTaskPayloadComponent().set_jsonValue(Map.of("jobFireId", fire.getJobFireId()));
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setVersion(0L);
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(System.currentTimeMillis()));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        return task;
    }
}
