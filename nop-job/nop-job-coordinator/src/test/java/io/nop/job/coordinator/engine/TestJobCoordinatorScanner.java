package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
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

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobCoordinatorScanner extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int SCHEDULE_STATUS_COMPLETED = 30;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int FIRE_STATUS_SUCCESS = 30;
    private static final int FIRE_STATUS_TIMEOUT = 50;
    private static final int FIRE_STATUS_CANCELED = 60;
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_SUCCESS = 30;
    private static final int TASK_STATUS_TIMEOUT = 50;
    private static final int TASK_STATUS_CANCELED = 60;
    private static final String EXECUTOR_KIND_TEST = "test";
    private static final int TRIGGER_TYPE_FIXED_RATE = 2;
    private static final int TRIGGER_TYPE_FIXED_DELAY = 3;
    private static final int BLOCK_STRATEGY_DISCARD = 1;
    private static final int BLOCK_STRATEGY_OVERLAY = 2;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJobScheduleStore scheduleStore;

    @Inject
    IJobFireStore fireStore;

    @Inject
    IJobTaskStore taskStore;

    @Test
    public void testScheduleToFireToTask() {
        NopJobSchedule schedule = newSchedule("schedule-1", "job-1");
        Timestamp dueFireTime = new Timestamp(schedule.getNextFireTime().getTime());
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
        planner.setScheduleStore(scheduleStore);
        planner.setBatchSize(10);
        planner.setPlanningTimeoutMs(1000);
        planner.setAssignedPartitions("1");
        planner.scanOnce();

        List<NopJobFire> fires = daoProvider.daoFor(NopJobFire.class).findAll();
        assertEquals(1, fires.size());

        NopJobFire fire = fires.get(0);
        assertEquals(schedule.getJobScheduleId(), fire.getJobScheduleId());
        assertEquals(dueFireTime, fire.getScheduledFireTime());
        assertEquals(schedule.getJobName(), fire.getJobName());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(1L, savedSchedule.getFireCount());
        assertEquals(1, savedSchedule.getActiveFireCount());
        assertNotNull(savedSchedule.getNextFireTime());
        assertEquals(dueFireTime, savedSchedule.getLastFireTime());

        JobDispatcherScannerImpl dispatcher = new JobDispatcherScannerImpl();
        dispatcher.setFireStore(fireStore);
        dispatcher.setDefaultTaskBuilder(new DefaultJobTaskBuilder());
        dispatcher.setBatchSize(10);
        dispatcher.setLockTimeoutMs(1000);
        dispatcher.setAssignedPartitions("1");
        dispatcher.scanOnce();

        List<NopJobTask> tasks = daoProvider.daoFor(NopJobTask.class).findAll();
        assertEquals(1, tasks.size());

        NopJobTask task = tasks.get(0);
        assertEquals(fire.getJobFireId(), task.getJobFireId());
        assertEquals(TASK_STATUS_WAITING, task.getTaskStatus());
        assertEquals(1, task.getTaskNo());

        NopJobFire savedFire = fireStore.loadFire(fire.getJobFireId());
        assertEquals(FIRE_STATUS_RUNNING, savedFire.getFireStatus());
    }

    @Test
    public void testCompletionUpdatesFireAndSchedule() {
        PreparedChain chain = prepareChain(newSchedule("schedule-2", "job-2"));
        Timestamp startTime = new Timestamp(System.currentTimeMillis());
        Timestamp endTime = new Timestamp(startTime.getTime() + 2000);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_SUCCESS);
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setDurationMs(2000L);
        task.setUpdatedBy("test");
        task.setUpdateTime(endTime);
        taskStore.updateTask(task);

        JobCompletionProcessorImpl completion = new JobCompletionProcessorImpl();
        completion.setFireStore(fireStore);
        completion.setScheduleStore(scheduleStore);
        completion.setTaskStore(taskStore);
        completion.setBatchSize(10);
        completion.setAssignedPartitions("1");
        completion.scanOnce();

        NopJobFire savedFire = fireStore.loadFire(chain.fire.getJobFireId());
        assertEquals(FIRE_STATUS_SUCCESS, savedFire.getFireStatus());
        assertEquals(startTime, savedFire.getStartTime());
        assertEquals(endTime, savedFire.getEndTime());
        assertEquals(2000L, savedFire.getDurationMs());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertEquals(0, savedSchedule.getActiveFireCount());
        assertEquals(endTime, savedSchedule.getLastEndTime());
        assertEquals(FIRE_STATUS_SUCCESS, savedSchedule.getLastFireStatus());
    }

    @Test
    public void testFixedDelayCompletionAdvancesNextFireTime() {
        NopJobSchedule schedule = newSchedule("schedule-3", "job-3");
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_DELAY);
        PreparedChain chain = prepareChain(schedule);
        Timestamp endTime = new Timestamp(System.currentTimeMillis() + 3000);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_SUCCESS);
        task.setStartTime(new Timestamp(endTime.getTime() - 1000));
        task.setEndTime(endTime);
        task.setDurationMs(1000L);
        task.setUpdatedBy("test");
        task.setUpdateTime(endTime);
        taskStore.updateTask(task);

        JobCompletionProcessorImpl completion = new JobCompletionProcessorImpl();
        completion.setFireStore(fireStore);
        completion.setScheduleStore(scheduleStore);
        completion.setTaskStore(taskStore);
        completion.setBatchSize(10);
        completion.setAssignedPartitions("1");
        completion.scanOnce();

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertNotNull(savedSchedule.getNextFireTime());
        assertEquals(endTime.getTime() + schedule.getRepeatIntervalMs(), savedSchedule.getNextFireTime().getTime());
        assertEquals(0, savedSchedule.getActiveFireCount());
    }

    @Test
    public void testCompletionMarksScheduleCompletedWhenTaskRequestsCompletion() {
        PreparedChain chain = prepareChain(newSchedule("schedule-8", "job-8"));
        Timestamp endTime = new Timestamp(System.currentTimeMillis() + 2000);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_SUCCESS);
        task.setStartTime(new Timestamp(endTime.getTime() - 1000));
        task.setEndTime(endTime);
        task.setDurationMs(1000L);
        task.setResultPayload(JsonTool.stringify(Map.of("completed", true)));
        task.setUpdatedBy("test");
        task.setUpdateTime(endTime);
        taskStore.updateTask(task);

        JobCompletionProcessorImpl completion = newCompletionProcessor();
        completion.scanOnce();

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_COMPLETED, savedSchedule.getScheduleStatus());
        assertEquals(FIRE_STATUS_SUCCESS, savedSchedule.getLastFireStatus());
        assertEquals(0, savedSchedule.getActiveFireCount());
        assertEquals(endTime, savedSchedule.getLastEndTime());
        assertEquals(null, savedSchedule.getNextFireTime());
    }

    @Test
    public void testCompletionUsesTaskNextScheduleTime() {
        PreparedChain chain = prepareChain(newSchedule("schedule-9", "job-9"));
        Timestamp endTime = new Timestamp(System.currentTimeMillis() + 2000);
        Timestamp nextScheduleTime = new Timestamp(endTime.getTime() + 5000);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_SUCCESS);
        task.setStartTime(new Timestamp(endTime.getTime() - 1000));
        task.setEndTime(endTime);
        task.setDurationMs(1000L);
        task.setResultPayload(JsonTool.stringify(Map.of("nextScheduleTime", nextScheduleTime.getTime())));
        task.setUpdatedBy("test");
        task.setUpdateTime(endTime);
        taskStore.updateTask(task);

        JobCompletionProcessorImpl completion = newCompletionProcessor();
        completion.scanOnce();

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_ENABLED, savedSchedule.getScheduleStatus());
        assertEquals(nextScheduleTime, savedSchedule.getNextFireTime());
        assertEquals(0, savedSchedule.getActiveFireCount());
    }

    @Test
    public void testFixedDelayCompletionUsesTaskNextScheduleTimeOverride() {
        NopJobSchedule schedule = newSchedule("schedule-10", "job-10");
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_DELAY);
        PreparedChain chain = prepareChain(schedule);
        Timestamp endTime = new Timestamp(System.currentTimeMillis() + 3000);
        Timestamp overriddenNextFireTime = new Timestamp(endTime.getTime() + 9000);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_SUCCESS);
        task.setStartTime(new Timestamp(endTime.getTime() - 1000));
        task.setEndTime(endTime);
        task.setDurationMs(1000L);
        task.setResultPayload(JsonTool.stringify(Map.of("nextScheduleTime", overriddenNextFireTime.getTime())));
        task.setUpdatedBy("test");
        task.setUpdateTime(endTime);
        taskStore.updateTask(task);

        JobCompletionProcessorImpl completion = newCompletionProcessor();
        completion.scanOnce();

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertEquals(overriddenNextFireTime, savedSchedule.getNextFireTime());
        assertEquals(0, savedSchedule.getActiveFireCount());
    }

    @Test
    public void testTimeoutCheckerMarksTaskAndCompletionFinalizesFire() {
        PreparedChain chain = prepareChain(newSchedule("schedule-4", "job-4"));
        Timestamp startTime = new Timestamp(System.currentTimeMillis() - 4000);

        NopJobSchedule runningSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        runningSchedule.setTimeoutSeconds(1);
        daoProvider.daoFor(NopJobSchedule.class).updateEntityDirectly(runningSchedule);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_RUNNING);
        task.setStartTime(startTime);
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        taskStore.updateTask(task);

        JobTimeoutCheckerImpl timeoutChecker = new JobTimeoutCheckerImpl();
        timeoutChecker.setTaskStore(taskStore);
        timeoutChecker.setFireStore(fireStore);
        timeoutChecker.setScheduleStore(scheduleStore);
        timeoutChecker.setBatchSize(10);
        timeoutChecker.setAssignedPartitions("1");
        timeoutChecker.scanOnce();

        NopJobTask timedOutTask = taskStore.loadTask(chain.task.getJobTaskId());
        assertEquals(TASK_STATUS_TIMEOUT, timedOutTask.getTaskStatus());
        assertEquals("JOB_TIMEOUT", timedOutTask.getErrorCode());
        assertNotNull(timedOutTask.getEndTime());

        JobCompletionProcessorImpl completion = new JobCompletionProcessorImpl();
        completion.setFireStore(fireStore);
        completion.setScheduleStore(scheduleStore);
        completion.setTaskStore(taskStore);
        completion.setBatchSize(10);
        completion.setAssignedPartitions("1");
        completion.scanOnce();

        NopJobFire savedFire = fireStore.loadFire(chain.fire.getJobFireId());
        assertEquals(FIRE_STATUS_TIMEOUT, savedFire.getFireStatus());
        assertEquals("JOB_TIMEOUT", savedFire.getErrorCode());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        assertEquals(FIRE_STATUS_TIMEOUT, savedSchedule.getLastFireStatus());
        assertEquals(0, savedSchedule.getActiveFireCount());
    }

    @Test
    public void testTimeoutCheckerInvokesCancelHandler() {
        PreparedChain chain = prepareChain(newSchedule("schedule-7", "job-7"));
        Timestamp startTime = new Timestamp(System.currentTimeMillis() - 4000);

        NopJobSchedule runningSchedule = scheduleStore.loadSchedule(chain.schedule.getJobScheduleId());
        runningSchedule.setTimeoutSeconds(1);
        daoProvider.daoFor(NopJobSchedule.class).updateEntityDirectly(runningSchedule);

        NopJobTask task = taskStore.loadTask(chain.task.getJobTaskId());
        task.setTaskStatus(TASK_STATUS_RUNNING);
        task.setStartTime(startTime);
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        taskStore.updateTask(task);

        RecordingCancelHandler cancelHandler = new RecordingCancelHandler();

        JobTimeoutCheckerImpl timeoutChecker = new JobTimeoutCheckerImpl();
        timeoutChecker.setTaskStore(taskStore);
        timeoutChecker.setFireStore(fireStore);
        timeoutChecker.setScheduleStore(scheduleStore);
        timeoutChecker.setCancelHandler(cancelHandler);
        timeoutChecker.setBatchSize(10);
        timeoutChecker.setAssignedPartitions("1");
        timeoutChecker.scanOnce();

        assertEquals(chain.schedule.getJobScheduleId(), cancelHandler.scheduleId);
        assertEquals(chain.fire.getJobFireId(), cancelHandler.fireId);
        assertEquals(chain.task.getJobTaskId(), cancelHandler.taskId);
    }

    @Test
    public void testDiscardBlockStrategySkipsNewFire() {
        NopJobSchedule schedule = newSchedule("schedule-5", "job-5");
        schedule.setBlockStrategy(BLOCK_STRATEGY_DISCARD);
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        Timestamp runningFireTime = new Timestamp(schedule.getNextFireTime().getTime() - 1000);
        schedule.setLastFireTime(runningFireTime);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire activeFire = newActiveFire(schedule, runningFireTime);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(activeFire);

        JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
        planner.setScheduleStore(scheduleStore);
        planner.setBatchSize(10);
        planner.setPlanningTimeoutMs(1000);
        planner.setAssignedPartitions("1");
        planner.scanOnce();

        List<NopJobFire> fires = daoProvider.daoFor(NopJobFire.class).findAll().stream()
                .filter(item -> schedule.getJobScheduleId().equals(item.getJobScheduleId()))
                .collect(Collectors.toList());
        assertEquals(1, fires.size());
        assertEquals(FIRE_STATUS_RUNNING, fires.get(0).getFireStatus());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(1L, savedSchedule.getFireCount());
        assertEquals(1, savedSchedule.getActiveFireCount());
        assertNotNull(savedSchedule.getNextFireTime());
        assertEquals(runningFireTime, savedSchedule.getLastFireTime());
    }

    @Test
    public void testOverlayBlockStrategyCancelsActiveFireAndCreatesReplacement() {
        NopJobSchedule schedule = newSchedule("schedule-6", "job-6");
        schedule.setBlockStrategy(BLOCK_STRATEGY_OVERLAY);
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        Timestamp runningFireTime = new Timestamp(schedule.getNextFireTime().getTime() - 1000);
        schedule.setLastFireTime(runningFireTime);
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire activeFire = newActiveFire(schedule, runningFireTime);
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(activeFire);

        NopJobTask activeTask = newActiveTask(activeFire, TASK_STATUS_RUNNING,
                new Timestamp(System.currentTimeMillis() - 500));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(activeTask);

        JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
        planner.setScheduleStore(scheduleStore);
        planner.setBatchSize(10);
        planner.setPlanningTimeoutMs(1000);
        planner.setAssignedPartitions("1");
        planner.scanOnce();

        List<NopJobFire> fires = daoProvider.daoFor(NopJobFire.class).findAll().stream()
                .filter(item -> schedule.getJobScheduleId().equals(item.getJobScheduleId()))
                .collect(Collectors.toList());
        assertEquals(2, fires.size());

        NopJobFire canceledFire = fires.stream()
                .filter(item -> FIRE_STATUS_CANCELED == item.getFireStatus())
                .findFirst()
                .orElseThrow();
        NopJobFire replacementFire = fires.stream()
                .filter(item -> FIRE_STATUS_CANCELED != item.getFireStatus())
                .findFirst()
                .orElseThrow();
        assertNotNull(canceledFire.getEndTime());
        assertEquals("JOB_OVERLAID", canceledFire.getErrorCode());
        assertEquals(schedule.getNextFireTime(), replacementFire.getScheduledFireTime());

        List<NopJobTask> tasks = daoProvider.daoFor(NopJobTask.class).findAll().stream()
                .filter(item -> canceledFire.getJobFireId().equals(item.getJobFireId()))
                .collect(Collectors.toList());
        assertEquals(1, tasks.size());
        assertEquals(TASK_STATUS_CANCELED, tasks.get(0).getTaskStatus());
        assertEquals("JOB_OVERLAID", tasks.get(0).getErrorCode());

        NopJobSchedule savedSchedule = scheduleStore.loadSchedule(schedule.getJobScheduleId());
        assertEquals(2L, savedSchedule.getFireCount());
        assertEquals(1, savedSchedule.getActiveFireCount());
        assertEquals(FIRE_STATUS_WAITING, savedSchedule.getLastFireStatus());
        assertNotNull(savedSchedule.getLastEndTime());
        assertNotNull(savedSchedule.getNextFireTime());
    }

    private PreparedChain prepareChain(NopJobSchedule schedule) {
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        JobPlannerScannerImpl planner = new JobPlannerScannerImpl();
        planner.setScheduleStore(scheduleStore);
        planner.setBatchSize(10);
        planner.setPlanningTimeoutMs(1000);
        planner.setAssignedPartitions("1");
        planner.scanOnce();

        NopJobFire fire = daoProvider.daoFor(NopJobFire.class).findAll().stream()
                .filter(item -> schedule.getJobScheduleId().equals(item.getJobScheduleId()))
                .findFirst()
                .orElseThrow();

        JobDispatcherScannerImpl dispatcher = new JobDispatcherScannerImpl();
        dispatcher.setFireStore(fireStore);
        dispatcher.setDefaultTaskBuilder(new DefaultJobTaskBuilder());
        dispatcher.setBatchSize(10);
        dispatcher.setLockTimeoutMs(1000);
        dispatcher.setAssignedPartitions("1");
        dispatcher.scanOnce();

        NopJobTask task = daoProvider.daoFor(NopJobTask.class).findAll().stream()
                .filter(item -> fire.getJobFireId().equals(item.getJobFireId()))
                .findFirst()
                .orElseThrow();

        return new PreparedChain(schedule, fire, task);
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

    private NopJobFire newActiveFire(NopJobSchedule schedule, Timestamp scheduledFireTime) {
        long now = System.currentTimeMillis();

        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(1);
        fire.setScheduledFireTime(scheduledFireTime);
        fire.setFireStatus(FIRE_STATUS_RUNNING);
        fire.setStartTime(scheduledFireTime);
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(now));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(now));
        return fire;
    }

    private NopJobTask newActiveTask(NopJobFire fire, int taskStatus, Timestamp startTime) {
        long now = System.currentTimeMillis();

        NopJobTask task = new NopJobTask();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(taskStatus);
        task.setWorkerInstanceId("worker-1");
        task.setStartTime(startTime);
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(now));
        return task;
    }

    private static final class PreparedChain {
        private final NopJobSchedule schedule;
        private final NopJobFire fire;
        private final NopJobTask task;

        private PreparedChain(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            this.schedule = schedule;
            this.fire = fire;
            this.task = task;
        }
    }

    private static final class RecordingCancelHandler implements IJobCancelHandler {
        private String scheduleId;
        private String fireId;
        private String taskId;

        @Override
        public void cancelRunningTask(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            this.scheduleId = schedule.getJobScheduleId();
            this.fireId = fire.getJobFireId();
            this.taskId = task.getJobTaskId();
        }
    }
}
