package io.nop.job.worker.engine;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobWorkerScanner extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int FIRE_STATUS_SUCCESS = 30;
    private static final int FIRE_STATUS_FAILED = 40;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_SUCCESS = 30;
    private static final int TASK_STATUS_FAILED = 40;
    private static final String EXECUTOR_KIND_TEST = "test";
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

    private IBeanContainer originalBeanContainer;

    @AfterEach
    public void tearDown() {
        if (originalBeanContainer != null) {
            BeanContainer.registerInstance(originalBeanContainer);
            originalBeanContainer = null;
        }
    }

    @Test
    public void testWorkerExecutesTaskSuccess() {
        rememberOriginalBeanContainer();
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobInvoker_test", new IJobInvoker() {
            @Override
            public java.util.concurrent.CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE(123456L));
            }

            @Override
            public java.util.concurrent.CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        BeanContainer.registerInstance(container);

        PreparedTask prepared = prepareWaitingTask("schedule-worker-1", "job-worker-1");

        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setCapacityProvider(() -> ResourceVector.MAX_VALUE);
        worker.scanOnce();

        NopJobTask savedTask = taskStore.loadTask(prepared.task.getJobTaskId());
        assertEquals(TASK_STATUS_SUCCESS, savedTask.getTaskStatus());
        assertNotNull(savedTask.getStartTime());
        assertNotNull(savedTask.getEndTime());
        assertEquals(AppConfig.hostId(), savedTask.getWorkerInstanceId());
        assertNotNull(savedTask.getResultPayloadComponent().get_jsonMap());

        JobCompletionProcessorLike completion = new JobCompletionProcessorLike(scheduleStore, fireStore, taskStore);
        completion.complete(prepared.fire.getJobFireId());

        NopJobFire savedFire = fireStore.loadFire(prepared.fire.getJobFireId());
        assertEquals(FIRE_STATUS_SUCCESS, savedFire.getFireStatus());
    }

    @Test
    public void testWorkerExecutesTaskFailure() {
        rememberOriginalBeanContainer();
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobInvoker_test", new IJobInvoker() {
            @Override
            public java.util.concurrent.CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(JobFireResult.ERROR(
                        new ErrorBean("JOB_TEST_ERROR").description("worker failed")
                ));
            }

            @Override
            public java.util.concurrent.CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        BeanContainer.registerInstance(container);

        PreparedTask prepared = prepareWaitingTask("schedule-worker-2", "job-worker-2");

        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setCapacityProvider(() -> ResourceVector.MAX_VALUE);
        worker.scanOnce();

        NopJobTask savedTask = taskStore.loadTask(prepared.task.getJobTaskId());
        assertEquals(TASK_STATUS_FAILED, savedTask.getTaskStatus());
        assertEquals("JOB_TEST_ERROR", savedTask.getErrorCode());

        JobCompletionProcessorLike completion = new JobCompletionProcessorLike(scheduleStore, fireStore, taskStore);
        completion.complete(prepared.fire.getJobFireId());

        NopJobFire savedFire = fireStore.loadFire(prepared.fire.getJobFireId());
        assertEquals(FIRE_STATUS_FAILED, savedFire.getFireStatus());
        assertEquals("JOB_TEST_ERROR", savedFire.getErrorCode());
    }

    @Test
    public void testMaxConcurrencySkipsWhenAtLimit() {
        PreparedTask prepared = prepareWaitingTask("schedule-worker-3", "job-worker-3");

        NopJobTask runningTask = taskStore.loadTask(prepared.task.getJobTaskId());
        runningTask.setTaskStatus(TASK_STATUS_RUNNING);
        runningTask.setWorkerInstanceId(AppConfig.hostId());
        runningTask.setStartTime(new Timestamp(System.currentTimeMillis()));
        runningTask.setUpdatedBy("test");
        runningTask.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        taskStore.updateTask(runningTask);

        PreparedTask prepared2 = prepareWaitingTask("schedule-worker-4", "job-worker-4");

        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setMaxConcurrency(1);
        worker.setCapacityProvider(() -> ResourceVector.MAX_VALUE);
        worker.scanOnce();

        NopJobTask task2 = taskStore.loadTask(prepared2.task.getJobTaskId());
        assertEquals(TASK_STATUS_WAITING, task2.getTaskStatus());
    }

    @Test
    public void testMaxConcurrencyAllowsWhenBelowLimit() {
        rememberOriginalBeanContainer();
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobInvoker_test", new IJobInvoker() {
            @Override
            public java.util.concurrent.CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE(123456L));
            }

            @Override
            public java.util.concurrent.CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        BeanContainer.registerInstance(container);

        PreparedTask prepared = prepareWaitingTask("schedule-worker-5", "job-worker-5");

        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setMaxConcurrency(5);
        worker.setCapacityProvider(() -> ResourceVector.MAX_VALUE);
        worker.scanOnce();

        NopJobTask savedTask = taskStore.loadTask(prepared.task.getJobTaskId());
        assertEquals(TASK_STATUS_SUCCESS, savedTask.getTaskStatus());
    }

    // ========== Plan 212 Phase 4 E2E 验证 ==========

    /**
     * 场景 A（异构满载，弱断言）：capacity 完全耗尽 → isZeroOrNegative 闸门触发，不再拉取新 task。
     * 用 RUNNING task 预占全部 capacity。
     */
    @Test
    public void testResourceCapacityExhaustion() {
        rememberOriginalBeanContainer();
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobInvoker_test", new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE(123456L));
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        BeanContainer.registerInstance(container);

        // 预占全部 capacity（RUNNING task，cost=1000m cpu, 2000MB mem）
        saveRunningTaskWithCost("occ-schedule", "occ-job", AppConfig.hostId(), 1000, 2000);

        ResourceVector reserved = taskStore.sumReservedCost(AppConfig.hostId());
        assertEquals(1000, reserved.getCpu());
        assertEquals(2000, reserved.getMemory());

        PreparedTask pt = prepareWaitingTask("exhaust-schedule", "exhaust-job");

        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setCapacityProvider(() -> new ResourceVector(1000, 2000));
        worker.scanOnce();

        NopJobTask saved = taskStore.loadTask(pt.task.getJobTaskId());
        assertEquals(TASK_STATUS_WAITING, saved.getTaskStatus(),
                "Running task fully occupies capacity -> waiting task should remain WAITING");
    }

    /**
     * 场景 B（向后兼容）：capacity=MAX_VALUE, cost=0 → 行为与 count-based 一致。
     * 语义契约验证：与 testWorkerExecutesTaskSuccess 等价，但显式声明向后兼容。
     */
    @Test
    public void testBackwardCompatibleMaxValueCapacity() {
        rememberOriginalBeanContainer();
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobInvoker_test", new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE(123456L));
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        BeanContainer.registerInstance(container);

        PreparedTask pt = prepareWaitingTask("compat-schedule", "compat-job");

        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setCapacityProvider(() -> ResourceVector.MAX_VALUE);
        worker.scanOnce();

        NopJobTask saved = taskStore.loadTask(pt.task.getJobTaskId());
        assertEquals(TASK_STATUS_SUCCESS, saved.getTaskStatus(),
                "MAX_VALUE capacity + cost=0 must behave identically to count-based mode");
    }

    /**
     * 场景 C（混合）：部分 task 有 cost，部分无 cost（cost=0）。
     * 无 cost task 始终通过 fits 过滤，有 cost task 仅在容量内通过。
     */
    @Test
    public void testMixedCostAndZeroCostTasks() {
        rememberOriginalBeanContainer();
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobInvoker_test", new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE(123456L));
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        BeanContainer.registerInstance(container);

        // 预占部分 capacity（600 cpu, 1200 MB）→ remaining = {400, 800} 在 capacity={1000,2000} 下
        saveRunningTaskWithCost("occ-mix-schedule", "occ-mix-job", AppConfig.hostId(), 600, 1200);

        // task 0 cost: cost=0,0 → ZERO 永远 fits
        PreparedTask taskZero = prepareWaitingTask("mix-zero-schedule", "mix-zero-job");
        // task fits: cost=300,500 → 400>=300 && 800>=500 → fits
        PreparedTask taskFits = prepareWaitingTaskWithCost("mix-fits-schedule", "mix-fits-job", 300, 500);
        // task no fit: cost=500,900 → 400>=500? no → does NOT fit
        PreparedTask taskNoFit = prepareWaitingTaskWithCost("mix-nofit-schedule", "mix-nofit-job", 500, 900);

        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setCapacityProvider(() -> new ResourceVector(1000, 2000));
        worker.scanOnce();

        NopJobTask savedZero = taskStore.loadTask(taskZero.task.getJobTaskId());
        assertEquals(TASK_STATUS_SUCCESS, savedZero.getTaskStatus(),
                "Zero-cost task must always be claimed (ZERO fits any remaining)");

        NopJobTask savedFits = taskStore.loadTask(taskFits.task.getJobTaskId());
        assertEquals(TASK_STATUS_SUCCESS, savedFits.getTaskStatus(),
                "Task with cost <= remaining must be claimed");

        NopJobTask savedNoFit = taskStore.loadTask(taskNoFit.task.getJobTaskId());
        assertEquals(TASK_STATUS_WAITING, savedNoFit.getTaskStatus(),
                "Task with cost > remaining must NOT be claimed");
    }

    private PreparedTask prepareWaitingTask(String scheduleId, String jobName) {
        long now = System.currentTimeMillis();

        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(scheduleId);
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName(jobName);
        schedule.setDisplayName(jobName);
        schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
        schedule.setExecutorKind(EXECUTOR_KIND_TEST);
        schedule.setExecutorKind("test");
        schedule.getJobParamsComponent().set_jsonValue(Map.of("k", "v"));
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
        schedule.setRepeatIntervalMs(1000L);
        schedule.setPartitionIndex((short) 1);
        schedule.setFireCount(1L);
        schedule.setActiveFireCount(1);
        schedule.setLastFireTime(new Timestamp(now - 1000));
        schedule.setVersion(0L);
        schedule.setCreatedBy("test");
        schedule.setCreateTime(new Timestamp(now));
        schedule.setUpdatedBy("test");
        schedule.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

        NopJobFire fire = new NopJobFire();
        fire.setJobScheduleId(schedule.getJobScheduleId());
        fire.setNamespaceId(schedule.getNamespaceId());
        fire.setGroupId(schedule.getGroupId());
        fire.setJobName(schedule.getJobName());
        fire.setTriggerSource(TRIGGER_SOURCE_SCHEDULE);
        fire.setScheduledFireTime(new Timestamp(now - 1000));
        fire.setFireStatus(FIRE_STATUS_RUNNING);
        fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of("k", "v"));
        fire.setExecutorKind(schedule.getExecutorKind());
        fire.setPartitionIndex(schedule.getPartitionIndex());
        fire.setVersion(0L);
        fire.setCreatedBy("test");
        fire.setCreateTime(new Timestamp(now));
        fire.setUpdatedBy("test");
        fire.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

        NopJobTask task = new NopJobTask();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(TASK_STATUS_WAITING);
        task.getTaskPayloadComponent().set_jsonValue(Map.of("jobFireId", fire.getJobFireId()));
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setVersion(0L);
        task.setCreatedBy("test");
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy("test");
        task.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);

        return new PreparedTask(schedule, fire, task);
    }

    // ========== Plan 212 Phase 4 helpers ==========

    private void saveRunningTaskWithCost(String scheduleId, String jobName,
                                          String workerInstanceId,
                                          int costCpu, int costMemory) {
        long now = System.currentTimeMillis();
        // JOB_SCHEDULE_ID is VARCHAR(32); each test method runs in an isolated local DB,
        // so the caller-provided scheduleId is already unique within the test.
        String sid = scheduleId;
        String fid = "fire-" + sid;
        String tid = "task-" + sid;

        NopJobSchedule s = new NopJobSchedule();
        s.setJobScheduleId(sid);
        s.setNamespaceId("default");
        s.setGroupId("default");
        s.setJobName(jobName);
        s.setDisplayName(jobName);
        s.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
        s.setExecutorKind("test");
        s.getJobParamsComponent().set_jsonValue(Map.of("k", "v"));
        s.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
        s.setRepeatIntervalMs(1000L);
        s.setPartitionIndex((short) 1);
        s.setFireCount(1L);
        s.setActiveFireCount(1);
        s.setLastFireTime(new Timestamp(now - 1000));
        s.setVersion(0L);
        s.setCreatedBy("test");
        s.setCreateTime(new Timestamp(now));
        s.setUpdatedBy("test");
        s.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(s);

        NopJobFire f = new NopJobFire();
        f.setJobFireId(fid);
        f.setJobScheduleId(sid);
        f.setNamespaceId("default");
        f.setGroupId("default");
        f.setJobName(jobName);
        f.setTriggerSource(TRIGGER_SOURCE_SCHEDULE);
        f.setScheduledFireTime(new Timestamp(now - 1000));
        f.setFireStatus(FIRE_STATUS_RUNNING);
        f.getJobParamsSnapshotComponent().set_jsonValue(Map.of("k", "v"));
        f.setExecutorKind("test");
        f.setPartitionIndex((short) 1);
        f.setVersion(0L);
        f.setCreatedBy("test");
        f.setCreateTime(new Timestamp(now));
        f.setUpdatedBy("test");
        f.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(f);

        NopJobTask t = new NopJobTask();
        t.setJobTaskId(tid);
        t.setJobFireId(fid);
        t.setTaskNo(1);
        t.setTaskStatus(TASK_STATUS_RUNNING);
        t.setWorkerInstanceId(workerInstanceId);
        t.setStartTime(new Timestamp(now));
        t.setCostCpu(costCpu);
        t.setCostMemory(costMemory);
        t.getTaskPayloadComponent().set_jsonValue(Map.of("jobFireId", fid));
        t.setPartitionIndex((short) 1);
        t.setVersion(0L);
        t.setCreatedBy("test");
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy("test");
        t.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(t);
    }

    private PreparedTask prepareWaitingTaskWithCost(String scheduleId, String jobName,
                                                     int costCpu, int costMemory) {
        PreparedTask pt = prepareWaitingTask(scheduleId, jobName);
        NopJobTask task = taskStore.loadTask(pt.task.getJobTaskId());
        task.setCostCpu(costCpu);
        task.setCostMemory(costMemory);
        daoProvider.daoFor(NopJobTask.class).updateEntityDirectly(task);
        return new PreparedTask(pt.schedule, pt.fire, task);
    }

    private static final int FIRE_STATUS_CANCELED = 60;

    @NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
    public static class Inner extends JunitBaseTestCase {
        private static final int SCHEDULE_STATUS_ENABLED = 10;
        private static final int FIRE_STATUS_RUNNING = 20;
        private static final int FIRE_STATUS_CANCELED = 60;
        private static final int TASK_STATUS_WAITING = 0;
        private static final int TASK_STATUS_CLAIMED = 15;
        private static final String EXECUTOR_KIND_TEST = "test";
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

        private IBeanContainer originalBeanContainer;

        @AfterEach
        public void tearDown() {
            if (originalBeanContainer != null) {
                BeanContainer.registerInstance(originalBeanContainer);
                originalBeanContainer = null;
            }
        }

        @Test
        public void testCanceledFireDoesNotWriteSuccess() {
            if (originalBeanContainer == null && BeanContainer.isInitialized()) {
                originalBeanContainer = BeanContainer.instance();
            }
            StaticBeanContainer container = new StaticBeanContainer();
            container.registerBean("nopJobInvoker_test", new IJobInvoker() {
                @Override
                public java.util.concurrent.CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                    return CompletableFuture.completedFuture(JobFireResult.CONTINUE(123456L));
                }

                @Override
                public java.util.concurrent.CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                    return CompletableFuture.completedFuture(Boolean.TRUE);
                }
            });
            BeanContainer.registerInstance(container);

            PreparedTask prepared = prepareWaitingTask("schedule-ar36-1", "job-ar36-1");

            NopJobFire fire = fireStore.loadFire(prepared.fire.getJobFireId());
            fire.setFireStatus(FIRE_STATUS_CANCELED);
            fire.setUpdatedBy("test-cancel");
            fire.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            daoProvider.daoFor(NopJobFire.class).updateEntityDirectly(fire);

            JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
            worker.setTaskStore(taskStore);
            worker.setFireStore(fireStore);
            worker.setScheduleStore(scheduleStore);
            worker.setInvokerResolver(new DefaultJobInvokerResolver());
            worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
            worker.setBatchSize(10);
            worker.setAssignedPartitions("1");
            worker.setLockTimeoutMs(1000);
            worker.scanOnce();

            NopJobTask savedTask = taskStore.loadTask(prepared.task.getJobTaskId());
            assertEquals(TASK_STATUS_WAITING, savedTask.getTaskStatus(),
                    "Task should remain in WAITING status when fire is already CANCELED");
            assertNull(savedTask.getErrorCode());
        }

        @Test
        public void testNullPromiseTreatedAsError() {
            if (originalBeanContainer == null && BeanContainer.isInitialized()) {
                originalBeanContainer = BeanContainer.instance();
            }
            StaticBeanContainer container = new StaticBeanContainer();
            container.registerBean("nopJobInvoker_test", new IJobInvoker() {
                @Override
                public java.util.concurrent.CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                    return null;
                }

                @Override
                public java.util.concurrent.CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                    return CompletableFuture.completedFuture(Boolean.TRUE);
                }
            });
            BeanContainer.registerInstance(container);

            PreparedTask prepared = prepareWaitingTask("schedule-ar41-1", "job-ar41-1");

            JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
            worker.setTaskStore(taskStore);
            worker.setFireStore(fireStore);
            worker.setScheduleStore(scheduleStore);
            worker.setInvokerResolver(new DefaultJobInvokerResolver());
            worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
            worker.setBatchSize(10);
            worker.setAssignedPartitions("1");
            worker.setLockTimeoutMs(1000);
            worker.scanOnce();

            NopJobTask savedTask = taskStore.loadTask(prepared.task.getJobTaskId());
            assertEquals(TASK_STATUS_FAILED, savedTask.getTaskStatus(),
                    "Null promise should be treated as error, not SUCCESS");
            assertEquals("JOB_INVOKER_RETURNED_NULL", savedTask.getErrorCode());
        }

        private PreparedTask prepareWaitingTask(String scheduleId, String jobName) {
            long now = System.currentTimeMillis();

            NopJobSchedule schedule = new NopJobSchedule();
            schedule.setJobScheduleId(scheduleId);
            schedule.setNamespaceId("default");
            schedule.setGroupId("default");
            schedule.setJobName(jobName);
            schedule.setDisplayName(jobName);
            schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
            schedule.setExecutorKind(EXECUTOR_KIND_TEST);
            schedule.getJobParamsComponent().set_jsonValue(Map.of("k", "v"));
            schedule.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
            schedule.setRepeatIntervalMs(1000L);
            schedule.setPartitionIndex((short) 1);
            schedule.setFireCount(1L);
            schedule.setActiveFireCount(1);
            schedule.setLastFireTime(new Timestamp(now - 1000));
            schedule.setVersion(0L);
            schedule.setCreatedBy("test");
            schedule.setCreateTime(new Timestamp(now));
            schedule.setUpdatedBy("test");
            schedule.setUpdateTime(new Timestamp(now));
            daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);

            NopJobFire fire = new NopJobFire();
            fire.setJobScheduleId(schedule.getJobScheduleId());
            fire.setNamespaceId(schedule.getNamespaceId());
            fire.setGroupId(schedule.getGroupId());
            fire.setJobName(schedule.getJobName());
            fire.setTriggerSource(TRIGGER_SOURCE_SCHEDULE);
            fire.setScheduledFireTime(new Timestamp(now - 1000));
            fire.setFireStatus(FIRE_STATUS_RUNNING);
            fire.getJobParamsSnapshotComponent().set_jsonValue(Map.of("k", "v"));
            fire.setExecutorKind(schedule.getExecutorKind());
            fire.setPartitionIndex(schedule.getPartitionIndex());
            fire.setVersion(0L);
            fire.setCreatedBy("test");
            fire.setCreateTime(new Timestamp(now));
            fire.setUpdatedBy("test");
            fire.setUpdateTime(new Timestamp(now));
            daoProvider.daoFor(NopJobFire.class).saveEntityDirectly(fire);

            NopJobTask task = new NopJobTask();
            task.setJobFireId(fire.getJobFireId());
            task.setTaskNo(1);
            task.setTaskStatus(TASK_STATUS_WAITING);
            task.getTaskPayloadComponent().set_jsonValue(Map.of("jobFireId", fire.getJobFireId()));
            task.setPartitionIndex(fire.getPartitionIndex());
            task.setVersion(0L);
            task.setCreatedBy("test");
            task.setCreateTime(new Timestamp(now));
            task.setUpdatedBy("test");
            task.setUpdateTime(new Timestamp(now));
            daoProvider.daoFor(NopJobTask.class).saveEntityDirectly(task);

            return new PreparedTask(schedule, fire, task);
        }

        private static final class PreparedTask {
            private final NopJobSchedule schedule;
            private final NopJobFire fire;
            private final NopJobTask task;

            private PreparedTask(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
                this.schedule = schedule;
                this.fire = fire;
                this.task = task;
            }
        }
    }

    private void rememberOriginalBeanContainer() {
        if (originalBeanContainer == null && BeanContainer.isInitialized()) {
            originalBeanContainer = BeanContainer.instance();
        }
    }

    private static final class PreparedTask {
        private final NopJobSchedule schedule;
        private final NopJobFire fire;
        private final NopJobTask task;

        private PreparedTask(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            this.schedule = schedule;
            this.fire = fire;
            this.task = task;
        }
    }

    private static final class JobCompletionProcessorLike {
        private final IJobScheduleStore scheduleStore;
        private final IJobFireStore fireStore;
        private final IJobTaskStore taskStore;

        private JobCompletionProcessorLike(IJobScheduleStore scheduleStore, IJobFireStore fireStore,
                                           IJobTaskStore taskStore) {
            this.scheduleStore = scheduleStore;
            this.fireStore = fireStore;
            this.taskStore = taskStore;
        }

        private void complete(String fireId) {
            NopJobFire fire = fireStore.loadFire(fireId);
            NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
            NopJobTask task = taskStore.findTasksByFireId(fireId).get(0);
            fire.setFireStatus(task.getTaskStatus() == TASK_STATUS_SUCCESS ? FIRE_STATUS_SUCCESS : FIRE_STATUS_FAILED);
            fire.setStartTime(task.getStartTime());
            fire.setEndTime(task.getEndTime());
            fire.setDurationMs(task.getDurationMs());
            fire.setErrorCode(task.getErrorCode());
            fire.setErrorMessage(task.getErrorMessage());
            fire.setUpdatedBy("test");
            fire.setUpdateTime(task.getEndTime());
            schedule.setActiveFireCount(0);
            schedule.setLastEndTime(task.getEndTime());
            schedule.setLastFireStatus(fire.getFireStatus());
            schedule.setUpdatedBy("test");
            schedule.setUpdateTime(task.getEndTime());
            fireStore.completeFireAndUpdateSchedule(fire, schedule);
        }
    }
}
