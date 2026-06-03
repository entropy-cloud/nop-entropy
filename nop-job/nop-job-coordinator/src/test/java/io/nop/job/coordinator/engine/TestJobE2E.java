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
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestJobE2E {

    private long currentTime;

    @BeforeEach
    void setUp() {
        currentTime = System.currentTimeMillis();
    }

    @Test
    void testE2E_happyPath_plannerToCompletion() {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId("s1");
        schedule.setJobName("testJob");
        schedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED);
        schedule.setTriggerType(_NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY);
        schedule.setNextFireTime(new Timestamp(currentTime));

        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("f1");
        fire.setJobScheduleId("s1");
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fire.setStartTime(new Timestamp(currentTime - 1000));

        NopJobTask task = new NopJobTask();
        task.setJobTaskId("t1");
        task.setJobFireId("f1");
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_SUCCESS);
        task.setStartTime(new Timestamp(currentTime - 1000));
        task.setEndTime(new Timestamp(currentTime));

        SimpleScheduleStore scheduleStore = new SimpleScheduleStore();
        scheduleStore.schedules.put("s1", schedule);
        scheduleStore.currentTime = currentTime;

        SimpleFireStore fireStore = new SimpleFireStore();
        fireStore.runningFires.add(fire);

        SimpleTaskStore taskStore = new SimpleTaskStore();
        taskStore.tasksByFire.put("f1", List.of(task));

        TrackingAlarmHandler alarmHandler = new TrackingAlarmHandler();

        JobCompletionProcessorImpl processor = new JobCompletionProcessorImpl();
        processor.setFireStore(fireStore);
        processor.setScheduleStore(scheduleStore);
        processor.setTaskStore(taskStore);
        processor.setAlarmHandler(alarmHandler);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_SUCCESS, fire.getFireStatus());
        assertEquals(1L, schedule.getTotalFireCount());
        assertEquals(1L, schedule.getSuccessFireCount());
        assertEquals(0, schedule.getActiveFireCount());
        assertEquals(0, alarmHandler.failedCount.get());
        assertEquals(0, alarmHandler.timeoutCount.get());
    }

    @Test
    void testE2E_failurePath_triggersRetryAndAlarm() {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId("s1");
        schedule.setJobName("testJob");
        schedule.setRetryPolicyId("retry-1");

        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("f1");
        fire.setJobScheduleId("s1");
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fire.setStartTime(new Timestamp(currentTime - 1000));

        NopJobTask task = new NopJobTask();
        task.setJobTaskId("t1");
        task.setJobFireId("f1");
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_FAILED);
        task.setStartTime(new Timestamp(currentTime - 1000));
        task.setEndTime(new Timestamp(currentTime));
        task.setErrorCode("ERR_FAIL");
        task.setErrorMessage("task failed");

        SimpleScheduleStore scheduleStore = new SimpleScheduleStore();
        scheduleStore.schedules.put("s1", schedule);
        scheduleStore.currentTime = currentTime;

        SimpleFireStore fireStore = new SimpleFireStore();
        fireStore.runningFires.add(fire);

        SimpleTaskStore taskStore = new SimpleTaskStore();
        taskStore.tasksByFire.put("f1", List.of(task));

        TrackingRetryBridge retryBridge = new TrackingRetryBridge();
        TrackingAlarmHandler alarmHandler = new TrackingAlarmHandler();

        JobCompletionProcessorImpl processor = new JobCompletionProcessorImpl();
        processor.setFireStore(fireStore);
        processor.setScheduleStore(scheduleStore);
        processor.setTaskStore(taskStore);
        processor.setRetryBridge(retryBridge);
        processor.setAlarmHandler(alarmHandler);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_FAILED, fire.getFireStatus());
        assertEquals(1, retryBridge.callCount.get());
        assertEquals("retry-1", retryBridge.lastPolicyId);
        assertEquals(1, alarmHandler.failedCount.get());
    }

    @Test
    void testE2E_timeoutPath_triggersAlarmOnly() {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId("s1");
        schedule.setJobName("testJob");
        schedule.setRetryPolicyId("retry-1");

        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("f1");
        fire.setJobScheduleId("s1");
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_RUNNING);
        fire.setStartTime(new Timestamp(currentTime - 5000));

        NopJobTask task = new NopJobTask();
        task.setJobTaskId("t1");
        task.setJobFireId("f1");
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_TIMEOUT);
        task.setStartTime(new Timestamp(currentTime - 5000));
        task.setEndTime(new Timestamp(currentTime));

        SimpleScheduleStore scheduleStore = new SimpleScheduleStore();
        scheduleStore.schedules.put("s1", schedule);
        scheduleStore.currentTime = currentTime;

        SimpleFireStore fireStore = new SimpleFireStore();
        fireStore.runningFires.add(fire);

        SimpleTaskStore taskStore = new SimpleTaskStore();
        taskStore.tasksByFire.put("f1", List.of(task));

        TrackingRetryBridge retryBridge = new TrackingRetryBridge();
        TrackingAlarmHandler alarmHandler = new TrackingAlarmHandler();

        JobCompletionProcessorImpl processor = new JobCompletionProcessorImpl();
        processor.setFireStore(fireStore);
        processor.setScheduleStore(scheduleStore);
        processor.setTaskStore(taskStore);
        processor.setRetryBridge(retryBridge);
        processor.setAlarmHandler(alarmHandler);

        processor.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, fire.getFireStatus());
        assertEquals(0, retryBridge.callCount.get());
        assertEquals(1, alarmHandler.timeoutCount.get());
    }

    @Test
    void testE2E_workerFailureDetection() {
        MockNamingService namingService = new MockNamingService();
        namingService.aliveIds = List.of("worker-a");

        NopJobTask task = new NopJobTask();
        task.setJobTaskId("t1");
        task.setJobFireId("f1");
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-gone");
        task.setStartTime(new Timestamp(currentTime - 10000));

        NopJobFire fire = new NopJobFire();
        fire.setJobFireId("f1");
        fire.setJobScheduleId("s1");
        fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_RUNNING);

        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId("s1");
        schedule.setJobName("testJob");
        schedule.setTimeoutSeconds(60);

        SimpleTaskStore taskStore = new SimpleTaskStore();
        taskStore.runningTasks.add(task);

        SimpleFireStore fireStore = new SimpleFireStore();
        fireStore.fires.put("f1", fire);

        SimpleScheduleStore scheduleStore = new SimpleScheduleStore();
        scheduleStore.schedules.put("s1", schedule);
        scheduleStore.currentTime = currentTime;

        JobTimeoutCheckerImpl checker = new JobTimeoutCheckerImpl();
        checker.setTaskStore(taskStore);
        checker.setFireStore(fireStore);
        checker.setScheduleStore(scheduleStore);
        checker.setNamingService(namingService);
        checker.setDispatchTimeoutMs(5000);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS, task.getTaskStatus());

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus());
        assertNotNull(task.getEndTime());
    }

    static class SimpleScheduleStore implements IJobScheduleStore {
        final Map<String, NopJobSchedule> schedules = new HashMap<>();
        long currentTime;

        @Override public long getCurrentTime() { return currentTime; }
        @Override public NopJobSchedule loadSchedule(String id) { return schedules.get(id); }
        @Override public NopJobSchedule tryLoadSchedule(String id) { return schedules.get(id); }
        @Override public Map<String, NopJobSchedule> batchLoadSchedules(Set<String> ids) {
            Map<String, NopJobSchedule> result = new HashMap<>();
            for (String id : ids) { NopJobSchedule s = schedules.get(id); if (s != null) result.put(id, s); }
            return result;
        }
        @Override public List<NopJobSchedule> fetchDueSchedules(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> s, String p, long t) { return s; }
        @Override public void advanceScheduleAfterSkip(NopJobSchedule s, Timestamp n) {}
        @Override public void insertFireAndAdvanceSchedule(NopJobSchedule s, NopJobFire f, Timestamp n, Integer l) {}
        @Override public void overlayFireAndAdvanceSchedule(NopJobSchedule s, NopJobFire f, Timestamp n, Integer l) {}
        @Override public void recoveryFireAndAdvanceSchedule(NopJobSchedule s, Timestamp n) {}
        @Override public boolean insertManualFire(NopJobSchedule s, NopJobFire f) { return true; }
    }

    static class SimpleFireStore implements IJobFireStore {
        final List<NopJobFire> runningFires = new ArrayList<>();
        final Map<String, NopJobFire> fires = new HashMap<>();

        @Override public List<NopJobFire> fetchRunningFires(int limit, IntRangeSet p) { return new ArrayList<>(runningFires); }
        @Override public Map<String, NopJobFire> batchLoadFires(Set<String> ids) {
            Map<String, NopJobFire> result = new HashMap<>();
            for (String id : ids) { NopJobFire f = fires.get(id); if (f != null) result.put(id, f); }
            return result;
        }
        @Override public List<NopJobFire> fetchDispatchingFires(int limit, IntRangeSet p) { return Collections.emptyList(); }
        @Override public void updateRetryRecordId(String jobFireId, String retryRecordId) {}
        @Override public List<NopJobFire> fetchWaitingFires(int limit, IntRangeSet p) { return Collections.emptyList(); }
        @Override public List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> f, String d, long t) { return f; }
        @Override public void insertTasksAndMarkFireDispatching(NopJobFire f, List<NopJobTask> t) {}
        @Override public void completeFireAndUpdateSchedule(NopJobFire f, NopJobSchedule s) {}
        @Override public boolean cancelFire(String id) { return false; }
        @Override public void failFireWithoutSchedule(String jobFireId, String errorCode, String errorMessage) {}
        @Override public NopJobFire loadFire(String id) { return fires.get(id); }
    }

    static class SimpleTaskStore implements IJobTaskStore {
        final List<NopJobTask> runningTasks = new ArrayList<>();
        final Map<String, List<NopJobTask>> tasksByFire = new HashMap<>();

        @Override public List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet p) { return new ArrayList<>(runningTasks); }
        @Override public List<NopJobTask> findTasksByFireId(String fireId) { return tasksByFire.getOrDefault(fireId, Collections.emptyList()); }
        @Override public boolean updateTask(NopJobTask t) { return true; }
        @Override public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet p) { return Collections.emptyList(); }
        @Override public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> t, String w, long l) { return t; }
        @Override public NopJobTask loadTask(String id) { return null; }
        @Override public long countRunningTasks(String w) { return 0; }
    }

    static class MockNamingService implements INamingService {
        List<String> aliveIds = Collections.emptyList();

        @Override public List<ServiceInstance> getInstances(String serviceName) {
            List<ServiceInstance> result = new ArrayList<>();
            for (String id : aliveIds) {
                ServiceInstance inst = new ServiceInstance();
                inst.setInstanceId(id);
                inst.setHealthy(true);
                inst.setEnabled(true);
                result.add(inst);
            }
            return result;
        }
        @Override public void registerInstance(ServiceInstance i) {}
        @Override public void unregisterInstance(ServiceInstance i) {}
        @Override public void updateInstance(ServiceInstance i) {}
        @Override public List<String> getServices() { return Collections.emptyList(); }
    }

    static class TrackingRetryBridge implements IJobRetryBridge {
        final AtomicInteger callCount = new AtomicInteger();
        String lastPolicyId;

        @Override public String onFireFailed(JobFireFailedEvent event) {
            callCount.incrementAndGet();
            lastPolicyId = event.getRetryPolicyId();
            return "retry-record";
        }
    }

    static class TrackingAlarmHandler implements IJobAlarmHandler {
        final AtomicInteger failedCount = new AtomicInteger();
        final AtomicInteger timeoutCount = new AtomicInteger();

        @Override public void onFireFailed(JobAlarmEvent e) { failedCount.incrementAndGet(); }
        @Override public void onFireTimeout(JobAlarmEvent e) { timeoutCount.incrementAndGet(); }
    }
}
