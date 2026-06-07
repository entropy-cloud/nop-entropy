package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.job.api.alarm.IJobAlarmHandler;
import io.nop.job.api.alarm.JobAlarmEvent;
import io.nop.job.api.retry.IJobRetryBridge;
import io.nop.job.api.retry.JobFireFailedEvent;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJobCompletionProcessor {

    private JobCompletionProcessorImpl processor;
    private MockFireStore fireStore;
    private MockScheduleStore scheduleStore;
    private MockTaskStore taskStore;
    private MockRetryBridge retryBridge;
    private MockAlarmHandler alarmHandler;
    private long currentTime;

    @BeforeEach
    void setUp() {
        processor = new JobCompletionProcessorImpl();
        fireStore = new MockFireStore();
        scheduleStore = new MockScheduleStore();
        taskStore = new MockTaskStore();
        retryBridge = new MockRetryBridge();
        alarmHandler = new MockAlarmHandler();

        processor.setFireStore(fireStore);
        processor.setScheduleStore(scheduleStore);
        processor.setTaskStore(taskStore);
        processor.setRetryBridge(retryBridge);
        processor.setAlarmHandler(alarmHandler);

        currentTime = System.currentTimeMillis();
        scheduleStore.setCurrentTime(currentTime);
    }

    @Test
    void testRetryBridge_calledWhenFireFailedWithRetryPolicy() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setRetryPolicyId("policy-1");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fire.setErrorCode("ERR_FAIL");
        fire.setErrorMessage("something failed");
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_FAILED);
        task.setErrorCode("ERR_FAIL");
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertEquals(1, retryBridge.getCallCount());
        assertEquals("f1", retryBridge.getLastEvent().getJobFireId());
        assertEquals("policy-1", retryBridge.getLastEvent().getRetryPolicyId());
    }

    @Test
    void testRetryBridge_notCalledWhenNoRetryPolicy() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_FAILED);
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertEquals(0, retryBridge.getCallCount());
    }

    @Test
    void testRetryBridge_usesFirePolicyOverSchedulePolicy() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setRetryPolicyId("schedule-policy");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fire.setRetryPolicyId("fire-policy");
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_FAILED);
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertEquals(1, retryBridge.getCallCount());
        assertEquals("fire-policy", retryBridge.getLastEvent().getRetryPolicyId());
    }

    @Test
    void testRetryBridge_notCalledOnTimeout() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setRetryPolicyId("policy-1");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_TIMEOUT);
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertEquals(0, retryBridge.getCallCount());
    }

    @Test
    void testRetryBridge_exceptionDoesNotBlockCompletion() {
        retryBridge.shouldThrow(true);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setRetryPolicyId("policy-1");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_FAILED);
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_FAILED, fire.getFireStatus());
    }

    @Test
    void testAlarm_calledOnFireFailed() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_FAILED);
        task.setErrorCode("ERR_1");
        task.setErrorMessage("fail msg");
        task.setStartTime(new Timestamp(currentTime - 5000));
        task.setEndTime(new Timestamp(currentTime));
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertEquals(1, alarmHandler.getFailedCount());
        assertEquals("f1", alarmHandler.getLastFailedEvent().getJobFireId());
        assertEquals("ERR_1", alarmHandler.getLastFailedEvent().getErrorCode());
    }

    @Test
    void testAlarm_calledOnFireTimeout() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_TIMEOUT);
        task.setStartTime(new Timestamp(currentTime - 5000));
        task.setEndTime(new Timestamp(currentTime));
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertEquals(1, alarmHandler.getTimeoutCount());
        assertEquals("f1", alarmHandler.getLastTimeoutEvent().getJobFireId());
    }

    @Test
    void testAlarm_notCalledOnSuccess() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_SUCCESS);
        task.setStartTime(new Timestamp(currentTime - 5000));
        task.setEndTime(new Timestamp(currentTime));
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertEquals(0, alarmHandler.getFailedCount());
        assertEquals(0, alarmHandler.getTimeoutCount());
    }

    @Test
    void testAlarm_exceptionDoesNotBlockCompletion() {
        alarmHandler.shouldThrowOnFailed(true);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_FAILED);
        task.setStartTime(new Timestamp(currentTime - 5000));
        task.setEndTime(new Timestamp(currentTime));
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_FAILED, fire.getFireStatus());
    }

    @Test
    void testFireNotCompletedWhenTasksPending() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask runningTask = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        NopJobTask successTask = createTask("t2", "f1", _NopJobCoreConstants.TASK_STATUS_SUCCESS);
        taskStore.addTask("f1", runningTask);
        taskStore.addTask("f1", successTask);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_RUNNING, fire.getFireStatus());
        assertEquals(0, retryBridge.getCallCount());
        assertEquals(0, alarmHandler.getFailedCount());
    }

    @Test
    void testSuspiciousWithRunningTask_staysPending() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask suspiciousTask = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS);
        NopJobTask runningTask = createTask("t2", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        taskStore.addTask("f1", suspiciousTask);
        taskStore.addTask("f1", runningTask);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_RUNNING, fire.getFireStatus());
    }

    @Test
    void testSuspiciousOnly_treatedAsTimeout() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask suspiciousTask = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS);
        taskStore.addTask("f1", suspiciousTask);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, fire.getFireStatus());
        assertEquals(1, alarmHandler.getTimeoutCount());
    }

    @Test
    void testSuspiciousWithSuccess_treatedAsTimeout() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask suspiciousTask = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS);
        NopJobTask successTask = createTask("t2", "f1", _NopJobCoreConstants.TASK_STATUS_SUCCESS);
        taskStore.addTask("f1", suspiciousTask);
        taskStore.addTask("f1", successTask);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, fire.getFireStatus());
    }

    @Test
    void testMixedTimeoutCanceledSuccess_timeoutWins() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask timeoutTask = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_TIMEOUT);
        NopJobTask canceledTask = createTask("t2", "f1", _NopJobCoreConstants.TASK_STATUS_CANCELED);
        NopJobTask successTask = createTask("t3", "f1", _NopJobCoreConstants.TASK_STATUS_SUCCESS);
        taskStore.addTask("f1", timeoutTask);
        taskStore.addTask("f1", canceledTask);
        taskStore.addTask("f1", successTask);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, fire.getFireStatus());
    }

    @Test
    void testSuspiciousWithFailed_treatedAsTimeout() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask suspiciousTask = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS);
        NopJobTask failedTask = createTask("t2", "f1", _NopJobCoreConstants.TASK_STATUS_FAILED);
        failedTask.setErrorCode("ERR_X");
        taskStore.addTask("f1", suspiciousTask);
        taskStore.addTask("f1", failedTask);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, fire.getFireStatus(),
                "SUSPICIOUS is treated as TIMEOUT which has higher priority than FAILED");
    }

    private NopJobFire createFire(String fireId, String scheduleId, int status) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(fireId);
        fire.setJobScheduleId(scheduleId);
        fire.setFireStatus(status);
        return fire;
    }

    private NopJobSchedule createSchedule(String scheduleId, String jobName) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(scheduleId);
        schedule.setJobName(jobName);
        return schedule;
    }

    private NopJobTask createTask(String taskId, String fireId, int status) {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(taskId);
        task.setJobFireId(fireId);
        task.setTaskStatus(status);
        return task;
    }

    static class MockRetryBridge implements IJobRetryBridge {
        private final AtomicInteger callCount = new AtomicInteger();
        private JobFireFailedEvent lastEvent;
        private boolean shouldThrow;

        void shouldThrow(boolean value) { this.shouldThrow = value; }

        int getCallCount() { return callCount.get(); }
        JobFireFailedEvent getLastEvent() { return lastEvent; }

        @Override
        public void onFireFailed(JobFireFailedEvent event) {
            callCount.incrementAndGet();
            this.lastEvent = event;
            if (shouldThrow) {
                throw NopException.adapt(new RuntimeException("bridge error"));
            }
        }
    }

    static class MockAlarmHandler implements IJobAlarmHandler {
        private final AtomicInteger failedCount = new AtomicInteger();
        private final AtomicInteger timeoutCount = new AtomicInteger();
        private JobAlarmEvent lastFailedEvent;
        private JobAlarmEvent lastTimeoutEvent;
        private boolean throwOnFailed;

        void shouldThrowOnFailed(boolean value) { this.throwOnFailed = value; }

        int getFailedCount() { return failedCount.get(); }
        int getTimeoutCount() { return timeoutCount.get(); }
        JobAlarmEvent getLastFailedEvent() { return lastFailedEvent; }
        JobAlarmEvent getLastTimeoutEvent() { return lastTimeoutEvent; }

        @Override
        public void onFireFailed(JobAlarmEvent event) {
            failedCount.incrementAndGet();
            this.lastFailedEvent = event;
            if (throwOnFailed) throw NopException.adapt(new RuntimeException("alarm error"));
        }

        @Override
        public void onFireTimeout(JobAlarmEvent event) {
            timeoutCount.incrementAndGet();
            this.lastTimeoutEvent = event;
        }
    }

    static class MockFireStore implements IJobFireStore {
        private final List<NopJobFire> runningFires = new ArrayList<>();
        private String failedFireId;
        private String failedErrorCode;
        private final java.util.concurrent.atomic.AtomicBoolean completeFireCalled = new java.util.concurrent.atomic.AtomicBoolean();

        void addRunningFire(NopJobFire fire) { runningFires.add(fire); }

        String getFailedFireId() { return failedFireId; }
        String getFailedErrorCode() { return failedErrorCode; }

        @Override
        public List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions) {
            return new ArrayList<>(runningFires);
        }

        @Override public List<NopJobFire> fetchWaitingFires(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobFire> fetchDispatchingFires(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public void updateRetryRecordId(String jobFireId, String retryRecordId) {}
        @Override public List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> fires, String dispatchInstanceId, long lockTimeoutMs) { return fires; }
        @Override public void insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks) {}
        @Override public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) { completeFireCalled.set(true); }
        @Override public boolean cancelFire(String jobFireId) { return false; }
        @Override public NopJobFire loadFire(String jobFireId) { return null; }
        @Override public Map<String, NopJobFire> batchLoadFires(Set<String> fireIds) { return Collections.emptyMap(); }
        @Override
        public void failFireWithoutSchedule(String jobFireId, String errorCode, String errorMessage) {
            this.failedFireId = jobFireId;
            this.failedErrorCode = errorCode;
        }
    }

    static class MockScheduleStore implements IJobScheduleStore {
        private final Map<String, NopJobSchedule> schedules = new HashMap<>();
        private long currentTime;

        void addSchedule(String id, NopJobSchedule s) { schedules.put(id, s); }
        void setCurrentTime(long t) { this.currentTime = t; }

        @Override public long getCurrentTime() { return currentTime; }
        @Override public NopJobSchedule loadSchedule(String id) { return schedules.get(id); }
        @Override public NopJobSchedule tryLoadSchedule(String id) { return schedules.get(id); }
        @Override public Map<String, NopJobSchedule> batchLoadSchedules(Set<String> ids) { return schedules; }
        @Override public List<NopJobSchedule> fetchDueSchedules(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> schedules, String plannerInstanceId, long lockTimeoutMs) { return schedules; }
        @Override public void advanceScheduleAfterSkip(NopJobSchedule schedule, Timestamp nextFireTime) {}
        @Override public void insertFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime, Integer lastFireStatus) {}
        @Override public void overlayFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime, Integer lastFireStatus) {}
        @Override public void recoveryFireAndAdvanceSchedule(NopJobSchedule schedule, Timestamp nextFireTime) {}
        @Override public boolean insertManualFire(NopJobSchedule schedule, NopJobFire fire) { return true; }
    }

    static class MockTaskStore implements IJobTaskStore {
        private final Map<String, List<NopJobTask>> tasksByFireId = new HashMap<>();

        void addTask(String fireId, NopJobTask task) {
            tasksByFireId.computeIfAbsent(fireId, k -> new ArrayList<>()).add(task);
        }

        @Override public List<NopJobTask> findTasksByFireId(String fireId) {
            return tasksByFireId.getOrDefault(fireId, Collections.emptyList());
        }
        @Override public boolean updateTask(NopJobTask task) { return true; }
        @Override public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) { return tasks; }
        @Override public List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public NopJobTask loadTask(String jobTaskId) { return null; }
        @Override public long countInFlightTasks(String workerInstanceId) { return 0; }
    }

    @Test
    void testResultDrivenCompletion_disabledByDefault() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_SUCCESS);
        task.setResultPayload("{\"completed\":true}");
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertNull(schedule.getScheduleStatus(),
                "Schedule should NOT be marked COMPLETED when allowResultCompletion is not set");
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_SUCCESS, fire.getFireStatus());
    }

    @Test
    void testResultDrivenCompletion_enabledWhenFlagSet() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.getJobParamsComponent().set_jsonValue(Map.of("allowResultCompletion", true));
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_SUCCESS);
        task.setResultPayload("{\"completed\":true}");
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED, schedule.getScheduleStatus(),
                "Schedule should be marked COMPLETED when allowResultCompletion=true and task returns completed:true");
    }

    @Test
    void testResultDrivenCompletion_nextScheduleTime_stillWorksWithoutFlag() {
        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_SUCCESS);
        long nextTime = currentTime + 60000;
        task.setResultPayload("{\"nextScheduleTime\":" + nextTime + "}");
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertNull(schedule.getScheduleStatus(),
                "Schedule should NOT be COMPLETED without allowResultCompletion flag");
    }

    @Test
    void testScheduleDeleted_fireMarkedFailed() {
        NopJobFire fire = createFire("f1", "deleted-schedule", _NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fireStore.addRunningFire(fire);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_FAILED);
        taskStore.addTask("f1", task);

        processor.scanOnce();

        assertEquals("f1", fireStore.getFailedFireId());
        assertNotNull(fireStore.getFailedErrorCode());
        assertTrue(fireStore.getFailedErrorCode().contains("schedule-deleted"),
                "Error code should indicate schedule was deleted");
    }
}
