package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests verifying multi-instance concurrent competition scenarios
 * in the distributed job scheduler. Tests simulate multiple planner/dispatcher/worker
 * instances competing for the same DB rows through optimistic version checks.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobConcurrency extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int FIRE_STATUS_SUCCESS = 30;
    private static final int FIRE_STATUS_TIMEOUT = 50;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_CLAIMED = 10;
    private static final int TASK_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_SUCCESS = 30;
    private static final int TASK_STATUS_TIMEOUT = 50;
    private static final String EXECUTOR_KIND_TEST = "test";
    private static final int TRIGGER_TYPE_FIXED_RATE = 2;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJobScheduleStore scheduleStore;

    @Inject
    IJobFireStore fireStore;

    @Inject
    IJobTaskStore taskStore;

    /**
     * Two planner instances compete for the same due schedule.
     * The first planner locks the schedule and creates a fire;
     * the second planner finds nothing available because the schedule's
     * nextFireTime has been advanced past now.
     */
    @Test
    public void testTwoPlannersCompeteForSameSchedule() {
        NopJobSchedule schedule = newSchedule("concurrent-schedule-1", "concurrent-job-1");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        JobPlannerScannerImpl plannerA = newPlanner();
        JobPlannerScannerImpl plannerB = newPlanner();

        plannerA.scanOnce();
        plannerB.scanOnce();

        List<NopJobFire> fires = fireStore.fetchWaitingFires(100, null);
        assertEquals(1, fires.size());
        assertEquals(schedule.getJobScheduleId(), fires.get(0).getJobScheduleId());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(1L, savedSchedule.getFireCount());
        assertEquals(1, savedSchedule.getActiveFireCount());
    }

    /**
     * Two dispatcher instances compete for the same waiting fire.
     * The first dispatcher locks the fire and creates a task;
     * the second dispatcher finds nothing to dispatch because the fire
     * is no longer in WAITING status.
     */
    @Test
    public void testTwoDispatchersCompeteForSameFire() {
        NopJobSchedule schedule = newSchedule("concurrent-schedule-2", "concurrent-job-2");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        JobPlannerScannerImpl planner = newPlanner();
        planner.scanOnce();

        NopJobFire fire = daoProvider.daoFor(NopJobFire.class).findAll().stream()
                .filter(item -> schedule.getJobScheduleId().equals(item.getJobScheduleId()))
                .findFirst()
                .orElseThrow();

        JobDispatcherScannerImpl dispatcherA = newDispatcher();
        JobDispatcherScannerImpl dispatcherB = newDispatcher();

        dispatcherA.scanOnce();
        dispatcherB.scanOnce();

        List<NopJobTask> tasks = daoProvider.daoFor(NopJobTask.class).findAll().stream()
                .filter(item -> fire.getJobFireId().equals(item.getJobFireId()))
                .collect(Collectors.toList());
        assertEquals(1, tasks.size());

        NopJobFire savedFire = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_RUNNING, savedFire.getFireStatus());
    }

    /**
     * Two worker instances compete for the same waiting task through
     * tryLockTasksForExecute. The first worker claims the task (optimistic
     * version check succeeds); the second worker's lock attempt returns
     * empty because the version has already been bumped.
     */
    @Test
    public void testTwoWorkersCompeteForSameTask() {
        NopJobSchedule schedule = newSchedule("concurrent-schedule-3", "concurrent-job-3");
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newRunningFire(schedule);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        NopJobTask task = newWaitingTask(fire);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);

        List<NopJobTask> tasksA = taskStore.fetchWaitingTasks(10, null);
        List<NopJobTask> tasksB = taskStore.fetchWaitingTasks(10, null);
        assertEquals(1, tasksA.size());
        assertEquals(1, tasksB.size());

        // Worker A locks first — version check succeeds
        List<NopJobTask> lockedByA = taskStore.tryLockTasksForExecute(tasksA, "worker-A", 60000);
        assertEquals(1, lockedByA.size());
        assertEquals(TASK_STATUS_CLAIMED, lockedByA.get(0).getTaskStatus());
        assertEquals("worker-A", lockedByA.get(0).getWorkerInstanceId());

        // Worker B tries to lock same task — version check fails (stale version)
        List<NopJobTask> lockedByB = taskStore.tryLockTasksForExecute(tasksB, "worker-B", 60000);
        assertEquals(0, lockedByB.size());

        NopJobTask savedTask = taskStore.loadTask(task.getJobTaskId());
        assertEquals(TASK_STATUS_CLAIMED, savedTask.getTaskStatus());
        assertEquals("worker-A", savedTask.getWorkerInstanceId());
    }

    /**
     * Timeout checker marks a running task as TIMEOUT when it exceeds the
     * schedule's timeout threshold. Verifies task status and error code.
     */
    @Test
    public void testTimeoutCheckerMarksTaskWhileRunning() {
        NopJobSchedule schedule = newSchedule("concurrent-schedule-4", "concurrent-job-4");
        schedule.setTimeoutSeconds(1);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newRunningFire(schedule);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        NopJobTask task = newRunningTask(fire, new Timestamp(System.currentTimeMillis() - 5000));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);

        JobTimeoutCheckerImpl checker = new JobTimeoutCheckerImpl();
        checker.setTaskStore(taskStore);
        checker.setFireStore(fireStore);
        checker.setScheduleStore(scheduleStore);
        checker.setBatchSize(10);
        checker.setAssignedPartitions("1");
        checker.scanOnce();

        NopJobTask savedTask = taskStore.loadTask(task.getJobTaskId());
        assertEquals(TASK_STATUS_TIMEOUT, savedTask.getTaskStatus());
        assertEquals("JOB_TIMEOUT", savedTask.getErrorCode());
        assertNotNull(savedTask.getEndTime());
    }

    /**
     * Completion processor processes a fire after a task has been marked
     * successful. Verifies fire status transitions to SUCCESS and schedule's
     * activeFireCount is decremented.
     */
    @Test
    public void testCompletionProcessorAfterSuccessfulTask() {
        NopJobSchedule schedule = newSchedule("concurrent-schedule-5", "concurrent-job-5");
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = newRunningFire(schedule);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        NopJobTask task = newWaitingTask(fire);
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);

        Timestamp startTime = new Timestamp(System.currentTimeMillis() - 2000);
        Timestamp endTime = new Timestamp(System.currentTimeMillis());
        NopJobTask loadedTask = taskStore.loadTask(task.getJobTaskId());
        loadedTask.setTaskStatus(TASK_STATUS_SUCCESS);
        loadedTask.setStartTime(startTime);
        loadedTask.setEndTime(endTime);
        loadedTask.setDurationMs(2000L);
        loadedTask.setUpdatedBy("test");
        loadedTask.setUpdateTime(endTime);
        taskStore.updateTask(loadedTask);

        JobCompletionProcessorImpl completion = newCompletionProcessor();
        completion.scanOnce();

        NopJobFire savedFire = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_SUCCESS, savedFire.getFireStatus());
        assertEquals(startTime, savedFire.getStartTime());
        assertEquals(endTime, savedFire.getEndTime());
        assertEquals(2000L, savedFire.getDurationMs());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(0, savedSchedule.getActiveFireCount());
        assertEquals(endTime, savedSchedule.getLastEndTime());
        assertEquals(FIRE_STATUS_SUCCESS, savedSchedule.getLastFireStatus());
    }

    /**
     * Full pipeline test with two coordinator instances processing 3 schedules.
     * Two planners compete for schedules, two dispatchers compete for fires,
     * tasks are locked via the task store, then completion finalizes everything.
     */
    @Test
    public void testFullPipelineTwoCoordinators() {
        NopJobSchedule schedule1 = newSchedule("pipeline-schedule-1", "pipeline-job-1");
        NopJobSchedule schedule2 = newSchedule("pipeline-schedule-2", "pipeline-job-2");
        NopJobSchedule schedule3 = newSchedule("pipeline-schedule-3", "pipeline-job-3");
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule1);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule2);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule3);

        // Phase 1: Two planners compete for 3 schedules
        JobPlannerScannerImpl plannerA = newPlanner();
        JobPlannerScannerImpl plannerB = newPlanner();
        plannerA.scanOnce();
        plannerB.scanOnce();

        List<NopJobFire> allFires = fireStore.fetchWaitingFires(100, null);
        assertEquals(3, allFires.size());

        // Phase 2: Two dispatchers compete for 3 waiting fires
        JobDispatcherScannerImpl dispatcherA = newDispatcher();
        JobDispatcherScannerImpl dispatcherB = newDispatcher();
        dispatcherA.scanOnce();
        dispatcherB.scanOnce();

        List<NopJobTask> allTasks = daoProvider.daoFor(NopJobTask.class).findAll();
        assertEquals(3, allTasks.size());

        List<NopJobFire> waitingFires = fireStore.fetchWaitingFires(100, null);
        assertEquals(0, waitingFires.size());

        // Phase 3: Simulate worker execution — complete all tasks
        for (NopJobTask t : allTasks) {
            NopJobTask fresh = taskStore.loadTask(t.getJobTaskId());
            fresh.setTaskStatus(TASK_STATUS_SUCCESS);
            fresh.setStartTime(new Timestamp(System.currentTimeMillis() - 1000));
            fresh.setEndTime(new Timestamp(System.currentTimeMillis()));
            fresh.setDurationMs(1000L);
            fresh.setUpdatedBy("test");
            fresh.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            taskStore.updateTask(fresh);
        }

        // Phase 4: Completion processor finalizes all fires
        JobCompletionProcessorImpl completion = newCompletionProcessor();
        completion.scanOnce();

        NopJobSchedule saved1 = scheduleStore.loadSchedule(schedule1.getJobScheduleId());
        NopJobSchedule saved2 = scheduleStore.loadSchedule(schedule2.getJobScheduleId());
        NopJobSchedule saved3 = scheduleStore.loadSchedule(schedule3.getJobScheduleId());
        assertEquals(0, saved1.getActiveFireCount());
        assertEquals(0, saved2.getActiveFireCount());
        assertEquals(0, saved3.getActiveFireCount());
        assertEquals(FIRE_STATUS_SUCCESS, saved1.getLastFireStatus());
        assertEquals(FIRE_STATUS_SUCCESS, saved2.getLastFireStatus());
        assertEquals(FIRE_STATUS_SUCCESS, saved3.getLastFireStatus());
    }

    // ---- Helper methods ----

    private JobPlannerScannerImpl newPlanner() {
        JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
        planner.setScheduleStore(scheduleStore);
        planner.setBatchSize(10);
        planner.setPlanningTimeoutMs(60000);
        planner.setAssignedPartitions("1");
        return planner;
    }

    private JobDispatcherScannerImpl newDispatcher() {
        JobDispatcherScannerImpl dispatcher = new JobDispatcherScannerImpl();
        dispatcher.setFireStore(fireStore);
        dispatcher.setDefaultTaskBuilder(new DefaultJobTaskBuilder());
        dispatcher.setBatchSize(10);
        dispatcher.setLockTimeoutMs(1000);
        dispatcher.setAssignedPartitions("1");
        return dispatcher;
    }

    private JobCompletionProcessorImpl newCompletionProcessor() {
        JobCompletionProcessorImpl completion = new JobCompletionProcessorImpl();
        completion.setFireStore(fireStore);
        completion.setScheduleStore(scheduleStore);
        completion.setTaskStore(taskStore);
        completion.setBatchSize(10);
        completion.setAssignedPartitions("1");
        return completion;
    }

    private NopJobSchedule newSchedule(String id, String jobName) {
        long now = System.currentTimeMillis();

        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(id);
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName(jobName);
        schedule.setDisplayName(jobName);
        schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
        schedule.setExecutorKind(EXECUTOR_KIND_TEST);
        schedule.setExecutorKind("testInvoker");
        schedule.getJobParamsComponent().set_jsonValue(Map.of("k", "v"));
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
        schedule.setRepeatIntervalMs(1000L);
        schedule.setPartitionIndex((short) 1);
        schedule.setFireCount(0L);
        schedule.setActiveFireCount(0);
        schedule.setNextFireTime(new Timestamp(now - 1000));
        schedule.setVersion(0L);
        schedule.setCreatedBy("test");
        schedule.setCreateTime(new Timestamp(now));
        schedule.setUpdatedBy("test");
        schedule.setUpdateTime(new Timestamp(now));
        return schedule;
    }

    private NopJobFire newRunningFire(NopJobSchedule schedule) {
        long now = System.currentTimeMillis();

        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(1);
        fire.setScheduledFireTime(new Timestamp(now - 1000));
        fire.setFireStatus(FIRE_STATUS_RUNNING);
        fire.setStartTime(new Timestamp(now - 1000));
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of("k", "v"));
        fire.setExecutorKind(schedule.getExecutorKind());
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(now));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(now));
        return fire;
    }

    private NopJobTask newWaitingTask(NopJobFire fire) {
        long now = System.currentTimeMillis();

        NopJobTask task = new NopJobTask();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(TASK_STATUS_WAITING);
        task.setPartitionIndex(fire.getPartitionIndex());
        task.getTaskPayloadComponent().set_jsonValue(
                Map.of("jobFireId", fire.getJobFireId(), "jobParamsSnapshot", Map.of()));
        task.setVersion(0L);
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(now));
        return task;
    }

    private NopJobTask newRunningTask(NopJobFire fire, Timestamp startTime) {
        long now = System.currentTimeMillis();

        NopJobTask task = new NopJobTask();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(TASK_STATUS_RUNNING);
        task.setStartTime(startTime);
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setVersion(0L);
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(now));
        return task;
    }
}
