package io.nop.job.worker.engine;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.IntRangeSet;
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
import io.nop.job.worker.metrics.IJobWorkerMetrics;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * AR-84: a WAITING task with null costCpu/costMemory (legacy pre-Plan-212 schedule)
     * must not NPE the worker scan; it should be claimed and executed (normalized to cost=0).
     * Before the fix, auto-unboxing null Integer in {@code new ResourceVector(...)} threw NPE
     * and aborted the entire scan batch.
     */
    @Test
    public void testNullCostTaskDoesNotNpeAndIsExecuted() {
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

        PreparedTask prepared = prepareWaitingTask("schedule-ar84", "job-ar84");
        // Explicitly ensure cost fields are null (simulate legacy pre-Plan-212 persisted row)
        NopJobTask legacyTask = taskStore.loadTask(prepared.task.getJobTaskId());
        legacyTask.setCostCpu(null);
        legacyTask.setCostMemory(null);
        daoProvider.daoFor(NopJobTask.class).updateEntityDirectly(legacyTask);

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
        assertEquals(TASK_STATUS_SUCCESS, savedTask.getTaskStatus(),
                "Null-cost task must be claimed and executed (normalized to 0), not NPE the scan");
    }

    /**
     * AR-83: capacity=4000m, a self-attributed WAITING task with cost=3000m (> capacity/2)
     * must be claimed and executed by an idle worker. Before the fix, double-counting
     * (cost counted in myReserved AND in fits check) made any task with cost > capacity/2
     * permanently stuck.
     */
    @Test
    public void testLargeSelfAttributedTaskClaimedWhenIdle() {
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

        PreparedTask prepared = prepareWaitingTaskWithCost("schedule-ar83-large", "job-ar83-large", 3000, 3000);
        // Attribute the WAITING task to self (as a bestFit dispatcher would).
        NopJobTask selfTask = taskStore.loadTask(prepared.task.getJobTaskId());
        selfTask.setWorkerInstanceId(AppConfig.hostId());
        daoProvider.daoFor(NopJobTask.class).updateEntityDirectly(selfTask);

        // Sanity: the self-attributed WAITING cost is in reserved (WAITING ∈ RESERVED_TASK_STATUSES)
        ResourceVector reserved = taskStore.sumReservedCost(AppConfig.hostId());
        assertEquals(3000, reserved.getCpu(), "guard: self-attributed WAITING cost counted in reserved");

        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setCapacityProvider(() -> new ResourceVector(4000, 4000));
        worker.scanOnce();

        NopJobTask saved = taskStore.loadTask(prepared.task.getJobTaskId());
        assertEquals(TASK_STATUS_SUCCESS, saved.getTaskStatus(),
                "Self-attributed task cost=3000m with capacity=4000m must be claimed by idle worker "
                        + "(was stuck before AR-83 fix because cost > capacity/2)");
    }

    /**
     * AR-83 防退化：资源限制仍生效，不退化为无限。cumulative claims within capacity all
     * claimed; the task that would push cumulative beyond capacity is rejected.
     * Uses non-self-attributed (null workerInstanceId) candidates so each claim adds new load.
     */
    @Test
    public void testResourceLimitCumulativeRejectsExcess() {
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

        // capacity {1000, 2000}; candidates are non-self (null attribution) so each claim is new load
        PreparedTask t1 = prepareWaitingTaskWithCost("schedule-ar83-c1", "job-ar83-c1", 300, 500);
        PreparedTask t2 = prepareWaitingTaskWithCost("schedule-ar83-c2", "job-ar83-c2", 300, 500);
        PreparedTask t3 = prepareWaitingTaskWithCost("schedule-ar83-c3", "job-ar83-c3", 500, 900);

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

        assertEquals(TASK_STATUS_SUCCESS, taskStore.loadTask(t1.task.getJobTaskId()).getTaskStatus(),
                "first task (cumulative 300/500) within capacity must be claimed");
        assertEquals(TASK_STATUS_SUCCESS, taskStore.loadTask(t2.task.getJobTaskId()).getTaskStatus(),
                "second task (cumulative 600/1000) within capacity must be claimed");
        assertEquals(TASK_STATUS_WAITING, taskStore.loadTask(t3.task.getJobTaskId()).getTaskStatus(),
                "third task (cumulative 1100/1900 exceeds capacity 1000 cpu) must be rejected");
    }

    /**
     * AR-94 守卫证明：dispatcher（多 coordinator 竞态）超额派发后，worker 侧 fit-check 是容量
     * 不变量的权威守卫。本用例模拟超额派发的后果（5 个自身归因 WAITING 任务，累计成本 >
     * capacity）：(a) worker 拒绝认领（不超额执行）；(b) 经 Phase 3 重派发（workerInstanceId 置 null）
     * 后，worker 按累计递减认领不超过 capacity 的数量。
     */
    @Test
    public void testWorkerGuardsCapacityWhenDispatcherOverAssigns() {
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

        // 模拟两个 dispatcher 超额派发的后果：5 个自身归因 WAITING 任务，每个 {300,500}
        PreparedTask[] overAssigned = new PreparedTask[5];
        for (int i = 0; i < 5; i++) {
            overAssigned[i] = prepareWaitingTaskWithCost("sched-ar94-g-" + i, "job-ar94-g-" + i, 300, 500);
            NopJobTask t = taskStore.loadTask(overAssigned[i].task.getJobTaskId());
            t.setWorkerInstanceId(AppConfig.hostId());
            daoProvider.daoFor(NopJobTask.class).updateEntityDirectly(t);
        }

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

        // (a) 守卫：reserved {1500,2500} > capacity {1000,2000} → worker 拒绝认领，全部保持 WAITING
        worker.scanOnce();
        for (PreparedTask pt : overAssigned) {
            assertEquals(TASK_STATUS_WAITING, taskStore.loadTask(pt.task.getJobTaskId()).getTaskStatus(),
                    "guard: worker must NOT claim when dispatcher over-assigned beyond capacity");
        }

        // (b) 自愈：经 Phase 3 重派发（age + reset 置 null）后，worker 按累计递减认领不超过 capacity
        for (PreparedTask pt : overAssigned) {
            NopJobTask t = taskStore.loadTask(pt.task.getJobTaskId());
            t.setCreateTime(new Timestamp(System.currentTimeMillis() - 600_000));
            daoProvider.daoFor(NopJobTask.class).updateEntityDirectly(t);
        }
        int reset = taskStore.resetStaleWaitingTasks(100, null, System.currentTimeMillis() - 300_000);
        assertEquals(5, reset, "all over-assigned tasks re-dispatched (workerInstanceId cleared)");

        worker.scanOnce();
        int claimedSuccess = 0;
        int totalClaimedCpu = 0;
        int totalClaimedMem = 0;
        for (PreparedTask pt : overAssigned) {
            NopJobTask t = taskStore.loadTask(pt.task.getJobTaskId());
            if (t.getTaskStatus() == TASK_STATUS_SUCCESS) {
                claimedSuccess++;
                totalClaimedCpu += t.getCostCpu();
                totalClaimedMem += t.getCostMemory();
            }
        }
        assertEquals(3, claimedSuccess,
                "after re-dispatch, worker claims exactly capacity-fitting count (cumulative {900,1500} <= {1000,2000})");
        assertTrue(totalClaimedCpu <= 1000 && totalClaimedMem <= 2000,
                "guard: total claimed cost never exceeds capacity");
    }

    /**
     * AR-86 worker 侧：已认领批次中某任务 loadSchedule 抛异常，其余已认领任务仍被执行（per-task 隔离）。
     */
    @Test
    public void testPerTaskIsolationBadTaskDoesNotBlockRest() {
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

        PreparedTask pt1 = prepareWaitingTask("sched-pti-1", "job-pti-1");
        PreparedTask pt2 = prepareWaitingTask("sched-pti-2", "job-pti-2");
        // sabotage task1: make its fire point to a non-existent schedule so executeTask's loadSchedule throws
        NopJobFire fire1 = fireStore.loadFire(pt1.fire.getJobFireId());
        fire1.setJobScheduleId("nonexistent-sched-pti");
        daoProvider.daoFor(NopJobFire.class).updateEntityDirectly(fire1);

        RecordingWorkerMetrics metrics = new RecordingWorkerMetrics();
        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setWorkerMetrics(metrics);
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setCapacityProvider(() -> ResourceVector.MAX_VALUE);
        worker.scanOnce();

        NopJobTask saved2 = taskStore.loadTask(pt2.task.getJobTaskId());
        assertEquals(TASK_STATUS_SUCCESS, saved2.getTaskStatus(),
                "good task must still execute despite the bad task in the same claimed batch");
        NopJobTask saved1 = taskStore.loadTask(pt1.task.getJobTaskId());
        assertTrue(saved1.getTaskStatus() != TASK_STATUS_SUCCESS,
                "bad task (loadSchedule threw) must NOT be marked SUCCESS");
        assertEquals(1, metrics.taskExecuteFailed,
                "bad task's failure recorded as metric (not silently swallowed)");
    }

    /**
     * AR-91：single 模式 task（workerInstanceId=NULL，DefaultJobTaskBuilder 现产物）在
     * enforceAttribution=true 的非同地部署 worker 下必须可被认领（IS NULL 分支），不再饥饿。
     * 认领后 workerInstanceId 被设为 worker hostId（SUSPICIOUS 探活路径不破坏）。
     */
    @Test
    public void testSingleModeTaskClaimableUnderEnforceAttribution() {
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

        PreparedTask pt = prepareWaitingTask("sched-ar91", "job-ar91");
        // single-mode: workerInstanceId is NULL (as DefaultJobTaskBuilder now produces)
        assertNull(taskStore.loadTask(pt.task.getJobTaskId()).getWorkerInstanceId(),
                "guard: single-mode task is null-attributed before claim");

        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setEnforceAttribution(true); // <-- key: non-co-deployed dedicated-worker scenario
        worker.setCapacityProvider(() -> ResourceVector.MAX_VALUE);
        worker.scanOnce();

        NopJobTask saved = taskStore.loadTask(pt.task.getJobTaskId());
        assertEquals(TASK_STATUS_SUCCESS, saved.getTaskStatus(),
                "single-mode (null-attribution) task must be claimable under enforceAttribution=true (AR-91)");
        assertEquals(AppConfig.hostId(), saved.getWorkerInstanceId(),
                "after claim workerInstanceId set to claiming worker (SUSPICIOUS liveness path intact)");
    }

    /**
     * AR-93：overfetch 有候选但无一 fit 时产生可观测信号（WARN + onRejected），候选保持 WAITING，
     * 与"无候选"（正常空闲）场景区分。
     */
    @Test
    public void testOverfetchNoFittingCandidateEmitsSignal() {
        PreparedTask pt = prepareWaitingTaskWithCost("sched-ar93", "job-ar93", 2000, 2000);

        RecordingWorkerMetrics metrics = new RecordingWorkerMetrics();
        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStore);
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setWorkerMetrics(metrics);
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        // remaining {1000,2000}; candidate cost {2000,2000} exceeds cpu → none fit
        worker.setCapacityProvider(() -> new ResourceVector(1000, 2000));
        worker.scanOnce();

        assertEquals(TASK_STATUS_WAITING, taskStore.loadTask(pt.task.getJobTaskId()).getTaskStatus(),
                "non-fitting candidate must stay WAITING");
        assertTrue(metrics.rejectedCount >= 1,
                "AR-93: candidates-exist-but-none-fit must emit an observable onRejected signal");
    }

    /**
     * AR-85：CLAIMED→RUNNING 的 CAS 失败（任务被超时检查器移交给他人）时，worker 不再调用
     * 该任务的 invoker（消除重复执行）。用 delegating store 使 updateTask 返回 false 模拟 CAS 失败。
     */
    @Test
    public void testClaimCasFailureSkipsInvoker() {
        rememberOriginalBeanContainer();
        int[] invokeCount = {0};
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobInvoker_test", new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                invokeCount[0]++;
                return CompletableFuture.completedFuture(JobFireResult.CONTINUE(123456L));
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        BeanContainer.registerInstance(container);

        PreparedTask pt = prepareWaitingTask("sched-ar85", "job-ar85");

        RecordingWorkerMetrics metrics = new RecordingWorkerMetrics();
        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        // wrap the real taskStore: updateTask (CLAIMED→RUNNING CAS) returns false → ownership lost
        worker.setTaskStore(new FailingCasTaskStore(taskStore));
        worker.setFireStore(fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setWorkerMetrics(metrics);
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setCapacityProvider(() -> ResourceVector.MAX_VALUE);
        worker.scanOnce();

        assertEquals(0, invokeCount[0],
                "invoker must NOT be called when CLAIMED→RUNNING CAS failed (ownership lost)");
        NopJobTask saved = taskStore.loadTask(pt.task.getJobTaskId());
        assertTrue(saved.getTaskStatus() != TASK_STATUS_SUCCESS,
                "task must not reach SUCCESS when ownership CAS failed");
        assertEquals(1, metrics.taskExecuteFailed,
                "CAS-failure branch emits metric (non-silent return)");
    }

    /**
     * Delegating IJobTaskStore that makes updateTask (the CLAIMED→RUNNING CAS) return false,
     * simulating a concurrent ownership change (timeout checker moved task to SUSPICIOUS).
     */
    private static final class FailingCasTaskStore implements IJobTaskStore {
        private final IJobTaskStore delegate;

        FailingCasTaskStore(IJobTaskStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean updateTask(NopJobTask task) {
            return false;
        }

        @Override
        public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions) {
            return delegate.fetchWaitingTasks(limit, partitions);
        }

        @Override
        public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions,
                                                   String workerInstanceId, boolean enforceAttribution) {
            return delegate.fetchWaitingTasks(limit, partitions, workerInstanceId, enforceAttribution);
        }

        @Override
        public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) {
            return delegate.tryLockTasksForExecute(tasks, workerInstanceId, lockTimeoutMs);
        }

        @Override
        public List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions) {
            return delegate.fetchRunningTasks(limit, partitions);
        }

        @Override
        public List<NopJobTask> findTasksByFireId(String jobFireId) {
            return delegate.findTasksByFireId(jobFireId);
        }

        @Override
        public NopJobTask loadTask(String jobTaskId) {
            return delegate.loadTask(jobTaskId);
        }

        @Override
        public long countInFlightTasks(String workerInstanceId) {
            return delegate.countInFlightTasks(workerInstanceId);
        }

        @Override
        public ResourceVector sumReservedCost(String workerInstanceId) {
            return delegate.sumReservedCost(workerInstanceId);
        }

        @Override
        public List<io.nop.job.dao.store.WorkerReservedCost> sumReservedCostByWorker() {
            return delegate.sumReservedCostByWorker();
        }

        @Override
        public int resetStaleWaitingTasks(int batchSize, IntRangeSet partitions, long deadlineMs) {
            return delegate.resetStaleWaitingTasks(batchSize, partitions, deadlineMs);
        }
    }

    private static class RecordingWorkerMetrics implements IJobWorkerMetrics {
        int taskExecuteFailed;
        int rejectedCount;

        @Override public void onTasksClaimed(int count) { }
        @Override public void onTaskSuccess(long durationMs) { }
        @Override public void onTaskFailure(long durationMs) { }
        @Override public void onTaskTimeout(long durationMs) { }
        @Override public void onRejected(int runningCount) { rejectedCount++; }
        @Override public void onTaskExecuteFailed(int count) { taskExecuteFailed += count; }
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
