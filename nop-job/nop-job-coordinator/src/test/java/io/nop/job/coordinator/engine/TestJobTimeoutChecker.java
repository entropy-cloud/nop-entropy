package io.nop.job.coordinator.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;
import io.nop.job.api.alarm.IJobAlarmHandler;
import io.nop.job.api.alarm.JobAlarmEvent;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
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
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestJobTimeoutChecker {

    private JobTimeoutCheckerImpl checker;
    private MockTaskStore taskStore;
    private MockFireStore fireStore;
    private MockScheduleStore scheduleStore;
    private MockNamingService namingService;
    private MockAlarmHandler alarmHandler;
    private long currentTime;

    @BeforeEach
    void setUp() {
        checker = new JobTimeoutCheckerImpl();
        taskStore = new MockTaskStore();
        fireStore = new MockFireStore();
        scheduleStore = new MockScheduleStore();
        namingService = new MockNamingService();
        alarmHandler = new MockAlarmHandler();

        checker.setTaskStore(taskStore);
        checker.setFireStore(fireStore);
        checker.setScheduleStore(scheduleStore);
        checker.setNamingService(namingService);
        checker.setAlarmHandler(alarmHandler);
        checker.setDispatchTimeoutMs(5000);

        currentTime = System.currentTimeMillis();
    }

    @Test
    void testDispatchTimeout_marksTimedOutFire() {
        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));
        fireStore.addDispatchingFire(fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, fire.getFireStatus());
        assertEquals("system", fire.getUpdatedBy());
        assertNotNull(fire.getEndTime());
    }

    @Test
    void testDispatchTimeout_notYetExpired() {
        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 2000));
        fireStore.addDispatchingFire(fire);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING, fire.getFireStatus());
    }

    @Test
    void testDispatchTimeout_firesAlarm() {
        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));
        fireStore.addDispatchingFire(fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(1, alarmHandler.getAlarmCount());
        JobAlarmEvent event = alarmHandler.getLastEvent();
        assertEquals("f1", event.getJobFireId());
        assertEquals("s1", event.getJobScheduleId());
    }

    @Test
    void testDispatchTimeout_updatesScheduleStats() {
        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));
        fireStore.addDispatchingFire(fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setActiveFireCount(1);
        schedule.setTotalFireCount(5L);
        schedule.setFailFireCount(1L);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(0, schedule.getActiveFireCount());
        assertEquals(6L, schedule.getTotalFireCount());
        assertEquals(2L, schedule.getFailFireCount());
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, schedule.getLastFireStatus());
    }

    @Test
    void testDispatchTimeout_disabledWhenZero() {
        checker.setDispatchTimeoutMs(0);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 100000));
        fireStore.addDispatchingFire(fire);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING, fire.getFireStatus());
    }

    @Test
    void testWorkerLiveness_marksSuspiciousThenTimeoutWhenWorkerGone() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-b");
        task.setStartTime(new Timestamp(currentTime - 10000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS, task.getTaskStatus());

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus());
    }

    @Test
    void testWorkerLiveness_notMarkedWhenWorkerAlive() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 10000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_RUNNING, task.getTaskStatus());
    }

    @Test
    void testSuspiciousToTimeout_conversion() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS);
        task.setWorkerInstanceId("worker-gone");
        task.setStartTime(new Timestamp(currentTime - 60000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus());
        assertNotNull(task.getEndTime());
        assertEquals("system", task.getUpdatedBy());
    }

    @Test
    void testNoNamingService_skipsWorkerLivenessCheck() {
        checker.setNamingService(null);

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-gone");
        task.setStartTime(new Timestamp(currentTime - 10000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_RUNNING, task.getTaskStatus());
    }

    @Test
    void testExistingTaskTimeout_unchanged() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 120000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus());
    }

    @Test
    void testEmptyTasks_noop() {
        scheduleStore.setCurrentTime(currentTime);
        checker.scanOnce();
        assertEquals(0, alarmHandler.getAlarmCount());
    }

    @Test
    void testWorkerLiveness_nullWorkerIdNotMarked() {
        namingService.setAliveInstances(List.of("worker-a"));

        NopJobTask task = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId(null);
        task.setStartTime(new Timestamp(currentTime - 10000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire);

        NopJobSchedule schedule = createSchedule("s1", "testJob");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_RUNNING, task.getTaskStatus());
    }

    @Test
    void testBatchResilience_taskTimeout_singleFailureDoesNotAbortBatch() {
        NopJobTask task1 = createTask("t1", "f1", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task1.setWorkerInstanceId("worker-a");
        task1.setStartTime(new Timestamp(currentTime - 120000));

        NopJobTask task2 = createTask("t2", "f2", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task2.setWorkerInstanceId("worker-a");
        task2.setStartTime(new Timestamp(currentTime - 120000));

        MockTaskStore explodingStore = new MockTaskStore() {
            @Override
            public boolean updateTask(NopJobTask task) {
                if ("t2".equals(task.getJobTaskId())) {
                    throw new NopException(JobCoreErrors.ERR_JOB_TIMEOUT);
                }
                return super.updateTask(task);
            }
        };
        explodingStore.addRunningTask(task1);
        explodingStore.addRunningTask(task2);
        checker.setTaskStore(explodingStore);

        namingService.setAliveInstances(List.of("worker-a"));

        NopJobFire fire1 = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        NopJobFire fire2 = createFire("f2", "s2", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f1", fire1);
        fireStore.addFire("f2", fire2);

        NopJobSchedule schedule1 = createSchedule("s1", "job1");
        schedule1.setTimeoutSeconds(60);
        NopJobSchedule schedule2 = createSchedule("s2", "job2");
        schedule2.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s1", schedule1);
        scheduleStore.addSchedule("s2", schedule2);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task1.getTaskStatus());
    }

    @Test
    void testBatchResilience_dispatchTimeout_singleFailureDoesNotAbortBatch() {
        NopJobFire fire1 = createFire("f1", "s1", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));
        NopJobFire fire2 = createFire("f2", "s2", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));

        fireStore.addDispatchingFire(fire1);
        fireStore.addDispatchingFire(fire2);

        NopJobSchedule schedule1 = createSchedule("s1", "job1");
        scheduleStore.addSchedule("s1", schedule1);

        MockScheduleStore explodingScheduleStore = new MockScheduleStore() {
            @Override
            public NopJobSchedule tryLoadSchedule(String scheduleId) {
                if ("s2".equals(scheduleId)) {
                    throw new NopException(JobCoreErrors.ERR_JOB_TIMEOUT);
                }
                return super.tryLoadSchedule(scheduleId);
            }
        };
        explodingScheduleStore.addSchedule("s1", schedule1);
        NopJobSchedule schedule2 = createSchedule("s2", "job2");
        explodingScheduleStore.addSchedule("s2", schedule2);
        explodingScheduleStore.setCurrentTime(currentTime);
        checker.setScheduleStore(explodingScheduleStore);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT, fire1.getFireStatus());
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_DISPATCHING, fire2.getFireStatus());
    }

    @Test
    void test_dispatchTimeoutScheduleDeleted() {
        NopJobFire fire = createFire("f-deleted", "s-deleted", _NopJobCoreConstants.FIRE_STATUS_DISPATCHING,
                new Timestamp(currentTime - 10000));
        fireStore.addDispatchingFire(fire);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals("f-deleted", fireStore.getFailedFireId(),
                "deleted schedule should trigger failFireWithoutSchedule");
        assertNotNull(fireStore.getFailedErrorCode());
    }

    @Test
    void test_timeoutUpdateTaskVersionConflictLogsWarn() {
        MockTaskStore versionStore = new MockTaskStore() {
            @Override
            public boolean updateTask(NopJobTask task) {
                return false;
            }
        };
        checker.setTaskStore(versionStore);

        NopJobTask task = createTask("t-ver", "f-ver", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 120000));
        versionStore.addRunningTask(task);

        NopJobFire fire = createFire("f-ver", "s-ver", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f-ver", fire);

        NopJobSchedule schedule = createSchedule("s-ver", "job1");
        schedule.setTimeoutSeconds(60);
        scheduleStore.addSchedule("s-ver", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus(),
                "Task should still be set to TIMEOUT locally even when updateTask returns false");
    }

    @Test
    void testExecutionTimeoutUsedWhenScheduleHasNoTimeoutSeconds() {
        checker.setExecutionTimeoutMs(3000);

        NopJobTask task = createTask("t-exec", "f-exec", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 5000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f-exec", "s-exec", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f-exec", fire);

        NopJobSchedule schedule = createSchedule("s-exec", "job1");
        schedule.setTimeoutSeconds(null);
        scheduleStore.addSchedule("s-exec", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus(),
                "Should use executionTimeoutMs when schedule has no timeoutSeconds");
    }

    @Test
    void testExecutionTimeoutNotExpiredYet() {
        checker.setExecutionTimeoutMs(10000);

        NopJobTask task = createTask("t-exec2", "f-exec2", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 5000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f-exec2", "s-exec2", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f-exec2", fire);

        NopJobSchedule schedule = createSchedule("s-exec2", "job1");
        schedule.setTimeoutSeconds(null);
        scheduleStore.addSchedule("s-exec2", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_RUNNING, task.getTaskStatus(),
                "Should not timeout if executionTimeoutMs not yet reached");
    }

    @Test
    void testExecutionTimeoutPreferredOverDispatchTimeout() {
        checker.setDispatchTimeoutMs(30000);
        checker.setExecutionTimeoutMs(3000);

        NopJobTask task = createTask("t-pref", "f-pref", _NopJobCoreConstants.TASK_STATUS_RUNNING);
        task.setWorkerInstanceId("worker-a");
        task.setStartTime(new Timestamp(currentTime - 5000));
        taskStore.addRunningTask(task);

        NopJobFire fire = createFire("f-pref", "s-pref", _NopJobCoreConstants.FIRE_STATUS_RUNNING, null);
        fireStore.addFire("f-pref", fire);

        NopJobSchedule schedule = createSchedule("s-pref", "job1");
        schedule.setTimeoutSeconds(null);
        scheduleStore.addSchedule("s-pref", schedule);

        scheduleStore.setCurrentTime(currentTime);

        checker.scanOnce();

        assertEquals(_NopJobCoreConstants.TASK_STATUS_TIMEOUT, task.getTaskStatus(),
                "executionTimeoutMs should be preferred over dispatchTimeoutMs for task execution timeout");
    }

    private NopJobTask createTask(String taskId, String fireId, int status) {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(taskId);
        task.setJobFireId(fireId);
        task.setTaskStatus(status);
        return task;
    }

    private NopJobFire createFire(String fireId, String scheduleId, int status, Timestamp startTime) {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(fireId);
        fire.setJobScheduleId(scheduleId);
        fire.setFireStatus(status);
        fire.setStartTime(startTime);
        return fire;
    }

    private NopJobSchedule createSchedule(String scheduleId, String jobName) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(scheduleId);
        schedule.setJobName(jobName);
        return schedule;
    }

    private ServiceInstance createInstance(String instanceId) {
        ServiceInstance inst = new ServiceInstance();
        inst.setInstanceId(instanceId);
        inst.setAddr("localhost");
        inst.setPort(8080);
        inst.setHealthy(true);
        inst.setEnabled(true);
        return inst;
    }

    static class MockTaskStore implements IJobTaskStore {
        private List<NopJobTask> runningTasks = new ArrayList<>();

        void addRunningTask(NopJobTask task) {
            runningTasks.add(task);
        }

        @Override
        public List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions) {
            return new ArrayList<>(runningTasks);
        }

        @Override public boolean updateTask(NopJobTask task) { return true; }
        @Override public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet p, String wid, boolean enfo) { return Collections.emptyList(); }
        @Override public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) { return tasks; }
        @Override public List<NopJobTask> findTasksByFireId(String jobFireId) { return Collections.emptyList(); }
        @Override public NopJobTask loadTask(String jobTaskId) { return null; }
        @Override public long countInFlightTasks(String workerInstanceId) { return 0; }
        @Override public io.nop.job.api.resource.ResourceVector sumReservedCost(String workerInstanceId) { return io.nop.job.api.resource.ResourceVector.ZERO; }
        @Override public java.util.List<io.nop.job.dao.store.WorkerReservedCost> sumReservedCostByWorker() { return java.util.Collections.emptyList(); }
    }

    static class MockFireStore implements IJobFireStore {
        private Map<String, NopJobFire> fireMap = new java.util.HashMap<>();
        private List<NopJobFire> dispatchingFires = new ArrayList<>();
        private String failedFireId;
        private String failedErrorCode;

        void addFire(String fireId, NopJobFire fire) {
            fireMap.put(fireId, fire);
        }

        void addDispatchingFire(NopJobFire fire) {
            dispatchingFires.add(fire);
        }

        String getFailedFireId() { return failedFireId; }
        String getFailedErrorCode() { return failedErrorCode; }

        @Override
        public Map<String, NopJobFire> batchLoadFires(Set<String> fireIds) {
            Map<String, NopJobFire> result = new java.util.HashMap<>();
            for (String id : fireIds) {
                NopJobFire fire = fireMap.get(id);
                if (fire != null) result.put(id, fire);
            }
            return result;
        }

        @Override
        public List<NopJobFire> fetchDispatchingFires(int limit, IntRangeSet partitions) {
            return new ArrayList<>(dispatchingFires);
        }

        @Override public List<NopJobFire> fetchWaitingFires(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> fires, String dispatchInstanceId, long lockTimeoutMs) { return fires; }
        @Override public void insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks) {}
        @Override public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {}
        @Override public boolean cancelFire(String jobFireId) { return false; }
        @Override public void failFireWithoutSchedule(String jobFireId, String errorCode, String errorMessage) {
            this.failedFireId = jobFireId;
            this.failedErrorCode = errorCode;
        }
        @Override public NopJobFire loadFire(String jobFireId) { return fireMap.get(jobFireId); }
        @Override public void updateRetryRecordId(String jobFireId, String retryRecordId) {}
    }

    static class MockScheduleStore implements IJobScheduleStore {
        private Map<String, NopJobSchedule> scheduleMap = new java.util.HashMap<>();
        private long currentTime;

        void addSchedule(String scheduleId, NopJobSchedule schedule) {
            scheduleMap.put(scheduleId, schedule);
        }

        void setCurrentTime(long time) {
            this.currentTime = time;
        }

        @Override
        public long getCurrentTime() {
            return currentTime;
        }

        @Override
        public NopJobSchedule loadSchedule(String scheduleId) {
            return scheduleMap.get(scheduleId);
        }

        @Override
        public NopJobSchedule tryLoadSchedule(String id) {
            return scheduleMap.get(id);
        }

        @Override
        public Map<String, NopJobSchedule> batchLoadSchedules(Set<String> scheduleIds) {
            Map<String, NopJobSchedule> result = new java.util.HashMap<>();
            for (String id : scheduleIds) {
                NopJobSchedule s = scheduleMap.get(id);
                if (s != null) result.put(id, s);
            }
            return result;
        }

        @Override public List<NopJobSchedule> fetchDueSchedules(int limit, IntRangeSet partitions) { return Collections.emptyList(); }
        @Override public List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> schedules, String plannerInstanceId, long lockTimeoutMs) { return schedules; }
        @Override public void advanceScheduleAfterSkip(NopJobSchedule schedule, Timestamp nextFireTime) {}
        @Override public void insertFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime, Integer lastFireStatus) {}
        @Override public void overlayFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime, Integer lastFireStatus) {}
        @Override public void recoveryFireAndAdvanceSchedule(NopJobSchedule schedule, Timestamp nextFireTime) {}
        @Override public boolean insertManualFire(NopJobSchedule schedule, NopJobFire fire) { return true; }
    }

    static class MockNamingService implements INamingService {
        private List<ServiceInstance> instances;

        void setAliveInstances(List<String> instanceIds) {
            List<ServiceInstance> list = new ArrayList<>();
            for (String id : instanceIds) {
                ServiceInstance inst = new ServiceInstance();
                inst.setInstanceId(id);
                inst.setHealthy(true);
                inst.setEnabled(true);
                list.add(inst);
            }
            this.instances = list;
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            return instances != null ? new ArrayList<>(instances) : null;
        }

        @Override public void registerInstance(ServiceInstance instance) {}
        @Override public void unregisterInstance(ServiceInstance instance) {}
        @Override public void updateInstance(ServiceInstance instance) {}
        @Override public List<String> getServices() { return Collections.emptyList(); }
    }

    static class MockAlarmHandler implements IJobAlarmHandler {
        private List<JobAlarmEvent> events = new ArrayList<>();

        @Override
        public void onFireTimeout(JobAlarmEvent event) {
            events.add(event);
        }

        @Override
        public void onFireFailed(JobAlarmEvent event) {
            events.add(event);
        }

        int getAlarmCount() {
            return events.size();
        }

        JobAlarmEvent getLastEvent() {
            return events.isEmpty() ? null : events.get(events.size() - 1);
        }
    }
}
