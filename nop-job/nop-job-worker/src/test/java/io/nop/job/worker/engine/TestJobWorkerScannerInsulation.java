package io.nop.job.worker.engine;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
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

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2496 nop-job MA-05（站点②，C4-012）与 MA-06（C4-009/C7-002）回归。
 *
 * <ul>
 *   <li>MA-05 站点②：{@code handleExecutionResult} 回调动作的 Error 级 Throwable 不被 whenComplete
 *       衍生 future 静默吞掉——带 ERROR 日志 + 终态 fail-safe（task 不再永久滞留 RUNNING，
 *       sumReservedCost 释放方向可达）。</li>
 *   <li>MA-06：worker 侧宽松看门狗——默认无执行超时且长任务不被杀（与 plan340 checker 契约共存）；
 *       配置阈值后只观测不终结；schedule.timeoutSeconds 优先；迟到的 invoke 完成仍正常终态化并
 *       释放 sumReservedCost。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestJobWorkerScannerInsulation extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int TRIGGER_TYPE_FIXED_RATE = 2;
    private static final int TRIGGER_SOURCE_SCHEDULE = 1;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_SUCCESS = 30;
    private static final int TASK_STATUS_FAILED = 40;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJobTaskStore taskStore;

    @Inject
    IJobFireStore fireStore;

    @Inject
    IJobScheduleStore scheduleStore;

    private IBeanContainer originalBeanContainer;

    @AfterEach
    public void tearDown() {
        if (originalBeanContainer != null) {
            BeanContainer.registerInstance(originalBeanContainer);
            originalBeanContainer = null;
        }
    }

    private void rememberOriginalBeanContainer() {
        if (originalBeanContainer == null && BeanContainer.isInitialized()) {
            originalBeanContainer = BeanContainer.instance();
        }
    }

    private JobWorkerScannerImpl newWorker(IJobTaskStore taskStoreOverride, IJobFireStore fireStoreOverride) {
        JobWorkerScannerImpl worker = new JobWorkerScannerImpl();
        worker.setTaskStore(taskStoreOverride != null ? taskStoreOverride : taskStore);
        worker.setFireStore(fireStoreOverride != null ? fireStoreOverride : fireStore);
        worker.setScheduleStore(scheduleStore);
        worker.setInvokerResolver(new DefaultJobInvokerResolver());
        worker.setExecutionContextBuilder(new DefaultJobExecutionContextBuilder());
        worker.setBatchSize(10);
        worker.setAssignedPartitions("1");
        worker.setLockTimeoutMs(1000);
        worker.setCapacityProvider(() -> ResourceVector.MAX_VALUE);
        return worker;
    }

    private void registerNeverCompletingInvoker(CompletableFuture<JobFireResult> promise) {
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobInvoker_test", new IJobInvoker() {
            @Override
            public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
                return promise;
            }

            @Override
            public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }
        });
        rememberOriginalBeanContainer();
        BeanContainer.registerInstance(container);
    }

    private void registerContinueInvoker() {
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
        rememberOriginalBeanContainer();
        BeanContainer.registerInstance(container);
    }

    /**
     * MA-05 站点②（先红后绿）：handleExecutionResult 内 fireStore.loadFire 抛 LinkageError 时，
     * 修复前 Error 被 whenComplete 衍生 future 静默吞掉、task 永久滞留 RUNNING；修复后 Error 被记录
     * 并触发终态 fail-safe，task 终态化为 FAILED。
     */
    @Test
    public void testHandleResultErrorFailSafeTerminalizesTask() {
        registerContinueInvoker();

        PreparedTask prepared = prepareWaitingTask("sched-ma05-w1", "job-ma05-w1");
        IJobFireStore boomFireStore = erroringFireStore(fireStore, prepared.fire.getJobFireId());

        RecordingMetrics metrics = new RecordingMetrics();
        JobWorkerScannerImpl worker = newWorker(null, boomFireStore);
        worker.setWorkerMetrics(metrics);
        worker.scanOnce();

        NopJobTask saved = taskStore.loadTask(prepared.task.getJobTaskId());
        assertEquals(TASK_STATUS_FAILED, saved.getTaskStatus(),
                "MA-05 station-2: Error in handleExecutionResult must trigger terminal fail-safe, "
                        + "task must NOT stay RUNNING forever (release direction of sumReservedCost)");
        assertEquals(1, metrics.taskExecuteFailed,
                "Error-level callback failure must be observable via metric (not silently swallowed)");
    }

    /**
     * MA-06 默认形态：不配置 invoke-timeout（默认 0 = 无执行超时）时，永不完成的 invoke 不被杀——
     * task 保持 RUNNING、promise 不被 complete/cancel、看门狗零触发。与 plan340 checker 契约
     * （TestJobTimeoutChecker.testLongRunningTaskWithoutTimeoutNotKilled，钉死零改动）共同构成
     * 「长任务默认不被杀」的 worker 侧 + checker 侧双向验证。
     */
    @Test
    public void testLongRunningInvokeDefaultNotKilled() throws Exception {
        CompletableFuture<JobFireResult> never = new CompletableFuture<>();
        registerNeverCompletingInvoker(never);

        PreparedTask prepared = prepareWaitingTaskWithCost("sched-ma06-default", "job-ma06-default", 100, 100);

        JobWorkerScannerImpl worker = newWorker(null, null);
        worker.scanOnce();

        NopJobTask running = taskStore.loadTask(prepared.task.getJobTaskId());
        assertEquals(TASK_STATUS_RUNNING, running.getTaskStatus(), "task is dispatched and running");

        Thread.sleep(300);
        assertEquals(TASK_STATUS_RUNNING, taskStore.loadTask(prepared.task.getJobTaskId()).getTaskStatus(),
                "MA-06: default (no invoke-timeout) must never kill a long-running invoke");
        assertFalse(never.isDone(), "worker watchdog must not complete the invoke future");
        assertFalse(never.isCancelled());
        assertEquals(0, worker.getInvokeTimeoutObservedCount(),
                "no observation without a configured threshold");
        assertEquals(1, worker.getInFlightInvokeCount(), "in-flight registry tracks the dispatch");
        assertTrue(worker.getOldestInFlightAgeMillis(System.currentTimeMillis()) >= 300,
                "in-flight age gauge exposes the stuck invoke age");
        // 容量占用方向 sanity：滞留期间 cost 计入 reserved（RUNNING ∈ RESERVED_TASK_STATUSES）
        ResourceVector reserved = taskStore.sumReservedCost(AppConfig.hostId());
        assertEquals(100, reserved.getCpu(), "guard: stuck RUNNING cost is counted in reserved");
    }

    /**
     * MA-06 观测面（先红后绿/变异）：配置 invoke-timeout-ms 后，超龄在飞 invoke 触发宽松观测
     * （ERROR + 观测计数 + gauge 可读），但任务不被终结——checker 链是唯一仲裁者。
     */
    @Test
    public void testInvokeTimeoutObservedButNeverTerminated() throws Exception {
        CompletableFuture<JobFireResult> never = new CompletableFuture<>();
        registerNeverCompletingInvoker(never);

        PreparedTask prepared = prepareWaitingTask("sched-ma06-obs", "job-ma06-obs");

        JobWorkerScannerImpl worker = newWorker(null, null);
        worker.setInvokeTimeoutMs(50);
        worker.scanOnce();

        long deadline = System.currentTimeMillis() + 10_000;
        while (worker.getInvokeTimeoutObservedCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(worker.getInvokeTimeoutObservedCount() >= 1,
                "MA-06: exceeding the configured threshold must produce a visible observation signal");
        assertTrue(worker.getLastInvokeTimeoutObservedMillis() > 0);
        assertEquals(TASK_STATUS_RUNNING, taskStore.loadTask(prepared.task.getJobTaskId()).getTaskStatus(),
                "watchdog is observe-only: task must NOT be terminated by the worker side");
        assertFalse(never.isDone() || never.isCancelled(),
                "watchdog must never complete/cancel the invoke future");
        assertEquals(1, worker.getInFlightInvokeCount(), "stuck invoke remains tracked in-flight");
    }

    /**
     * MA-06 释放方向：看门狗触发观测后，迟到的 invoke 完成仍正常走 handleExecutionResult 终态化，
     * task 离开 RUNNING、sumReservedCost 释放（回归看门狗与终结通道双向不冲突）。
     */
    @Test
    public void testLateCompletionStillReleasesReservedCost() throws Exception {
        CompletableFuture<JobFireResult> late = new CompletableFuture<>();
        registerNeverCompletingInvoker(late);

        PreparedTask prepared = prepareWaitingTaskWithCost("sched-ma06-late", "job-ma06-late", 300, 300);

        JobWorkerScannerImpl worker = newWorker(null, null);
        worker.setInvokeTimeoutMs(50);
        worker.scanOnce();

        assertEquals(300, taskStore.sumReservedCost(AppConfig.hostId()).getCpu(),
                "guard: in-flight RUNNING cost counted while in-flight");

        // 迟到完成（超过看门狗阈值之后）：终结通道不受看门狗影响
        late.complete(JobFireResult.CONTINUE(123456L));

        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            NopJobTask t = taskStore.loadTask(prepared.task.getJobTaskId());
            if (t.getTaskStatus() != null && t.getTaskStatus() != TASK_STATUS_RUNNING)
                break;
            Thread.sleep(20);
        }
        NopJobTask saved = taskStore.loadTask(prepared.task.getJobTaskId());
        assertEquals(TASK_STATUS_SUCCESS, saved.getTaskStatus(),
                "MA-06 release direction: late completion still terminalizes the task");
        assertEquals(0, worker.getInFlightInvokeCount(), "in-flight registry cleared on settlement");
        assertEquals(0, taskStore.sumReservedCost(AppConfig.hostId()).getCpu(),
                "MA-06 release direction: sumReservedCost drops to zero after terminalization");
    }

    /**
     * MA-06 阈值取值顺序（沿用 checker 既有顺序）：schedule.timeoutSeconds > 0 优先于
     * nop.job.worker.invoke-timeout-ms；默认 0 = disabled。
     */
    @Test
    public void testScheduleTimeoutSecondsTakesPrecedence() {
        JobWorkerScannerImpl worker = newWorker(null, null);
        worker.setInvokeTimeoutMs(999_999);

        NopJobSchedule withTimeout = new NopJobSchedule();
        withTimeout.setTimeoutSeconds(30);
        assertEquals(30_000L, worker.resolveInvokeWatchdogThresholdMs(withTimeout),
                "schedule.timeoutSeconds > 0 must take precedence (checker value order)");

        NopJobSchedule withoutTimeout = new NopJobSchedule();
        withoutTimeout.setTimeoutSeconds(null);
        assertEquals(999_999L, worker.resolveInvokeWatchdogThresholdMs(withoutTimeout),
                "falls back to nop.job.worker.invoke-timeout-ms");

        JobWorkerScannerImpl defaultWorker = newWorker(null, null);
        assertEquals(0L, defaultWorker.resolveInvokeWatchdogThresholdMs(withoutTimeout),
                "default (no config) = disabled");
    }

    // ========== fixtures ==========

    /**
     * Delegating IJobFireStore：目标 fire 的 loadFire 抛 LinkageError（MA-05 站点② Error 注入点：
     * handleExecutionResult 首个 store 访问），其余方法全部委托。
     */
    static IJobFireStore erroringFireStore(IJobFireStore delegate, String boomFireId) {
        int[] loadCalls = {0};
        return (IJobFireStore) Proxy.newProxyInstance(IJobFireStore.class.getClassLoader(),
                new Class[]{IJobFireStore.class},
                (proxy, method, args) -> {
                    // 第 1 次 loadFire = executeTask 预加载（正常）；第 2 次 = handleExecutionResult 内（注入点）
                    if (method.getName().equals("loadFire") && args != null && args.length == 1
                            && boomFireId.equals(args[0]) && ++loadCalls[0] >= 2) {
                        throw new LinkageError("fire-load-boom");
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    static class RecordingMetrics implements IJobWorkerMetrics {
        int claimed;
        int success;
        int failure;
        int timeout;
        int rejected;
        int taskExecuteFailed;

        @Override
        public void onTasksClaimed(int count) {
            claimed += count;
        }

        @Override
        public void onTaskSuccess(long durationMs) {
            success++;
        }

        @Override
        public void onTaskFailure(long durationMs) {
            failure++;
        }

        @Override
        public void onTaskTimeout(long durationMs) {
            timeout++;
        }

        @Override
        public void onRejected(int runningCount) {
            rejected++;
        }

        @Override
        public void onTaskExecuteFailed(int count) {
            taskExecuteFailed += count;
        }
    }

    static class PreparedTask {
        final NopJobSchedule schedule;
        final NopJobFire fire;
        final NopJobTask task;

        PreparedTask(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            this.schedule = schedule;
            this.fire = fire;
            this.task = task;
        }
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

    private PreparedTask prepareWaitingTaskWithCost(String scheduleId, String jobName, int costCpu, int costMemory) {
        PreparedTask pt = prepareWaitingTask(scheduleId, jobName);
        NopJobTask task = taskStore.loadTask(pt.task.getJobTaskId());
        task.setCostCpu(costCpu);
        task.setCostMemory(costMemory);
        daoProvider.daoFor(NopJobTask.class).updateEntityDirectly(task);
        return new PreparedTask(pt.schedule, pt.fire, task);
    }
}
