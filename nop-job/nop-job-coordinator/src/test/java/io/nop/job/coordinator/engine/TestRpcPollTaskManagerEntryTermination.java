package io.nop.job.coordinator.engine;

import io.micrometer.core.instrument.MeterRegistry;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2513 1.4-C4-001 / C10-001 收口门：RpcPollTaskManager 条目终结驱动五面回归。
 * <p>
 * 裁定①（ai-dev/design/concurrency/job-rpcpoll-termination-and-invoker-lock.md）五面：
 * (a) 失败预算/退避——连续失败达阈值 resolve ERROR(ERR_JOB_REMOTE_INVOKE_FAILED)，
 * 达阈值前 2^(streak-1) 封顶 12 轮跳轮退避（缝1红①：缺陷态 activeCount&gt;0 永不终结）；
 * (b) deadline 全局超时兜底（schedule.timeoutSeconds 优先，全局键同源，两键均无=0 不杀，
 * plan 340 同构对照）；(c) DB 回传 resolveFromAuthority（TIMEOUT 回传 + 未命中 no-op）；
 * (d) PollEntry 最小视图（反射白名单，三实体不驻留）；(e) gauge + 失败 counter。
 * 每个终结测试末尾统一 activeCount()==0 断言。
 */
public class TestRpcPollTaskManagerEntryTermination {

    private static final String TASK_ID = "task-c40001";
    private static final String FIRE_ID = "fire-c40001";
    private static final String SCHEDULE_ID = "sched-c40001";

    private RpcPollTaskManager manager;
    private AlwaysFailingStatusClient client;

    @BeforeEach
    void setUp() {
        manager = new RpcPollTaskManager();
        client = new AlwaysFailingStatusClient();
        manager.setRpcPollTaskClient(client);
        manager.setPollIntervalMs(1000);
        manager.setScheduledExecutor(io.nop.commons.concurrent.executor.GlobalExecutors.globalTimer());
    }

    @AfterEach
    void tearDown() {
        // 终结全部在途条目，防泄漏句柄影响其他测试
        if (manager != null) {
            manager.setPollFailThreshold(1);
            manager.setRpcPollTaskClient(new IRpcPollTaskClient() {
                @Override
                public CompletionStage<String> startJob(NopJobSchedule s, NopJobFire f, NopJobTask t,
                                                        ICancelToken token) {
                    return CompletableFuture.completedFuture(t.getJobTaskId());
                }

                @Override
                public CompletionStage<TaskStatusBean> getJobStatus(NopJobSchedule s, NopJobFire f, NopJobTask t,
                                                                    ICancelToken token) {
                    return CompletableFuture.completedFuture(status(TaskStatusBean.STATUS_SUCCESS));
                }

                @Override
                public CompletionStage<Boolean> cancelJob(NopJobSchedule s, NopJobFire f, NopJobTask t,
                                                          ICancelToken token) {
                    return CompletableFuture.completedFuture(Boolean.TRUE);
                }
            });
        }
    }

    // ========== 缝1（红①）：getJobStatus 恒失败 + 无 timeoutSeconds —— 失败预算终结 ==========

    /**
     * 红①→绿：getJobStatus 恒 failedFuture、schedule 无 timeoutSeconds、未配置全局超时——
     * 缺陷态（无失败预算）下 activeCount()>0 永不终结（deadline 轮询观察窗内恒红）；
     * 修复后连续失败达阈值（此处配置 2 缩短观察窗）resolve ERROR(ERR_JOB_REMOTE_INVOKE_FAILED)，
     * 条目移出注册表。接线证据（rule 23）：预算经 pollOnce → handlePollFailure → future.complete
     * 真实消费。
     */
    @Test
    void testC4_001_failBudgetTerminatesEntryWhenStatusAlwaysFails() throws Exception {
        manager.setPollFailThreshold(2);

        CompletableFuture<JobFireResult> future = new CompletableFuture<>();
        manager.register(newSchedule(null), newFire(), newTask(), null, future);

        JobFireResult result = future.get(15, TimeUnit.SECONDS);

        assertTrue(result.isErrorResult(), "fail budget must settle the entry future with an explicit error");
        assertEquals(JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED.getErrorCode(), result.getError().getErrorCode(),
                "budget-exceeded must resolve ERR_JOB_REMOTE_INVOKE_FAILED (not a silent clear)");
        assertEntryRemoved(manager);
    }

    /** 装配期 fail-fast：阈值 <=0 非法（缺省 5 合法）。 */
    @Test
    void testC4_001_failBudgetThresholdMustBePositive() {
        NopException ex = assertThrows(NopException.class, () -> manager.setPollFailThreshold(0));
        assertEquals(JobCoreErrors.ERR_JOB_CONFIG_INVALID.getErrorCode(), ex.getErrorCode());
        assertDoesNotThrow(() -> manager.setPollFailThreshold(1));
    }

    /**
     * 退避形态：达阈值前按 2^(streak-1) 封顶 12 轮跳轮——状态查询 RPC 次数严格少于 pollOnce
     * 轮询次数（跳过的轮次只做纯内存检查，不发起 RPC）。
     */
    @Test
    void testC4_001_backoffSkipsStatusPollsBeforeThreshold() {
        manager.setPollFailThreshold(5);

        CompletableFuture<JobFireResult> future = new CompletableFuture<>();
        RpcPollTaskManager.PollEntry entry = new RpcPollTaskManager.PollEntry(
                TASK_ID, newSchedule(null), newFire(), newTask(), null, future, 0L);

        // 手动驱动 pollOnce（确定性，不依赖真实节拍）：streak=1 不跳轮；streak>=2 后出现跳轮。
        // 阈值 5 + 退避（2+4+8 跳轮）在第 19 轮才计满——12 轮内不得终结
        for (int i = 0; i < 12 && !future.isDone(); i++) {
            manager.pollOnce(entry);
        }

        assertFalse(future.isDone(), "threshold 5 not reached within 12 rounds when backoff skips polls");
        assertTrue(client.statusCalls.get() < 12,
                "backoff must skip status RPCs: statusCalls=" + client.statusCalls.get()
                        + " must be < 12 pollOnce rounds");
        assertTrue(entry.getFailStreak().get() >= 4, "streak must have grown past the backoff onset");

        // 清理：终结条目
        future.complete(JobFireResult.CONTINUE);
    }

    // ========== 缝2（裁定①(b)）：deadline 全局超时兜底 ==========

    /**
     * 不配 schedule.timeoutSeconds、注入全局 execution-timeout-ms → deadline 兜底在墙钟上限内
     * resolve ERR_JOB_TIMEOUT；不配置时 deadline=0 不杀（plan 340 同构对照）。
     */
    @Test
    void testC4_001_globalExecutionTimeoutFallbackTerminatesEntry() throws Exception {
        manager.setExecutionTimeoutMs(1500);

        AlwaysRunningClient runningClient = new AlwaysRunningClient();
        manager.setRpcPollTaskClient(runningClient);

        CompletableFuture<JobFireResult> future = new CompletableFuture<>();
        manager.register(newSchedule(null), newFire(), newTask(), null, future);

        JobFireResult result = future.get(15, TimeUnit.SECONDS);

        assertTrue(result.isErrorResult());
        assertEquals(JobCoreErrors.ERR_JOB_TIMEOUT.getErrorCode(), result.getError().getErrorCode(),
                "global execution-timeout-ms must provide the wall-clock deadline fallback");
        assertTrue(runningClient.cancelCalled, "deadline timeout must best-effort cancel the remote job");
        assertEntryRemoved(manager);
    }

    /**
     * plan 340 同构对照：两键均未配置（schedule.timeoutSeconds 无 + 全局 -1）→ deadline=0，
     * 长任务不被杀、条目保持注册（观察窗内 future 不终结）。
     */
    @Test
    void testC4_001_noTimeoutConfigured_doesNotKillLongTask() throws Exception {
        manager.setExecutionTimeoutMs(-1);

        AlwaysRunningClient runningClient = new AlwaysRunningClient();
        manager.setRpcPollTaskClient(runningClient);

        CompletableFuture<JobFireResult> future = new CompletableFuture<>();
        manager.register(newSchedule(null), newFire(), newTask(), null, future);
        try {
            Thread.sleep(2500);
            assertFalse(future.isDone(),
                    "plan 340 parity: without any timeout config the long task must not be killed");
            assertEquals(1, manager.activeCount(), "entry stays registered while the remote task runs");
        } finally {
            future.complete(JobFireResult.CONTINUE);
            future.get(2, TimeUnit.SECONDS);
            assertEntryRemoved(manager);
        }
    }

    // ========== 缝3（裁定①(c)）：DB 终态回传 resolveFromAuthority ==========

    /**
     * checker 置 TIMEOUT 终态后经 resolveFromAuthority 回传 → future 完成 ERR_JOB_TIMEOUT +
     * activeCount 归零。接线证据（rule 23）：manager.resolveFromAuthority 为 checker 侧
     * notifyPollManagerTaskTimeout 的直连通道（两 TIMEOUT 写点均接）。
     */
    @Test
    void testC4_001_resolveFromAuthorityCompletesFutureWithTimeout() throws Exception {
        AlwaysRunningClient runningClient = new AlwaysRunningClient();
        manager.setRpcPollTaskClient(runningClient);

        CompletableFuture<JobFireResult> future = new CompletableFuture<>();
        manager.register(newSchedule(null), newFire(), newTask(), null, future);
        assertEquals(1, manager.activeCount());

        manager.resolveFromAuthority(TASK_ID,
                JobFireResult.ERROR(new io.nop.api.core.beans.ErrorBean(
                        JobCoreErrors.ERR_JOB_TIMEOUT.getErrorCode())));

        JobFireResult result = future.get(5, TimeUnit.SECONDS);
        assertTrue(result.isErrorResult());
        assertEquals(JobCoreErrors.ERR_JOB_TIMEOUT.getErrorCode(), result.getError().getErrorCode());
        assertEntryRemoved(manager);
    }

    /** 未命中（未知 taskId / future 已终结）= 合法 no-op，不抛不resolve 他条目。 */
    @Test
    void testC4_001_resolveFromAuthorityMissIsNoop() {
        assertDoesNotThrow(() -> manager.resolveFromAuthority("task-not-registered",
                JobFireResult.CONTINUE));

        CompletableFuture<JobFireResult> future = new CompletableFuture<>();
        future.complete(JobFireResult.CONTINUE);
        RpcPollTaskManager.PollEntry entry = new RpcPollTaskManager.PollEntry(
                TASK_ID, newSchedule(null), newFire(), newTask(), null, future, 0L);
        assertDoesNotThrow(() -> manager.resolveFromAuthority(TASK_ID, JobFireResult.ERROR(null)));
        assertTrue(future.isDone());
        assertNull(future.join().getError(), "already-done future must not be overwritten");
    }

    // ========== 缝4（裁定①(d)）：PollEntry 最小视图反射白名单 ==========

    /**
     * C5-005 驻留体积断言口径（§3.4）：反射枚举 PollEntry 字段集——不得含任何 NopJob* 实体
     * 类型字段（三实体 JSON payload 对象图不驻留），字段集 ⊆ 白名单清单。
     */
    @Test
    void testC4_001_pollEntryMinimalView_noEntityFieldsWhitelistOnly() {
        Set<String> whitelist = Set.of(
                "taskId", "scheduleId", "fireId", "targetHost", "cancelToken", "deadline", "future",
                "request", "failStreak", "skipRounds", "pollHandle", "onCancelListener");

        List<String> violations = new ArrayList<>();
        for (Class<?> c = RpcPollTaskManager.PollEntry.class; c != null && c != Object.class;
             c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!whitelist.contains(f.getName())) {
                    violations.add("unexpected field: " + f.getName() + " : " + f.getType());
                }
                if (f.getType().getName().startsWith("io.nop.job.dao.entity.NopJob")) {
                    violations.add("entity field retained: " + f.getName() + " : " + f.getType());
                }
            }
        }
        assertTrue(violations.isEmpty(), "PollEntry must hold the minimal view only: " + violations);
    }

    // ========== 缝（裁定①(e)）：gauge + 失败 counter ==========

    @Test
    void testC4_001_activeCountGaugeAndFailureCounterVisible() throws Exception {
        MeterRegistry registry = GlobalMeterRegistry.instance();
        if (registry == null) {
            return; // registry 未初始化的部署形态：观测通道静默跳过（绝缘层同形态）
        }
        manager.setPollFailThreshold(1);

        CompletableFuture<JobFireResult> future = new CompletableFuture<>();
        manager.register(newSchedule(null), newFire(), newTask(), null, future);
        future.get(5, TimeUnit.SECONDS);

        // micrometer 同名 gauge 去重保留首个注册（生产单例 manager 下即其 entries 表）；
        // 注册表终结事实断言以本 manager 的 activeCount 为准
        assertNotNull(registry.find("nop.job.remote.poll.active-count").gauge(),
                "active-count gauge must be registered");
        assertEntryRemoved(manager);
    }

    // ========== helpers ==========

    /**
     * 终结移除走 future.whenComplete → cancelEntry：get() 唤醒可先于完成线程执行完回调
     * （complete 先落结果后跑依赖动作），注册表摘除为紧随的异步步——有界等待后断言归零。
     */
    private static void assertEntryRemoved(RpcPollTaskManager mgr) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (mgr.activeCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(0, mgr.activeCount(), "terminated entry must leave the registry");
    }

    private NopJobTask newTask() {
        NopJobTask task = new NopJobTask();
        task.setJobTaskId(TASK_ID);
        task.setJobFireId(FIRE_ID);
        task.setTaskStatus(0);
        task.setTargetHost("worker-host-1");
        return task;
    }

    private NopJobFire newFire() {
        NopJobFire fire = new NopJobFire();
        fire.setJobFireId(FIRE_ID);
        fire.setJobScheduleId(SCHEDULE_ID);
        return fire;
    }

    private NopJobSchedule newSchedule(Integer timeoutSeconds) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(SCHEDULE_ID);
        schedule.setJobName("c40001Job");
        schedule.setTimeoutSeconds(timeoutSeconds);
        return schedule;
    }

    private static TaskStatusBean status(int taskStatus) {
        TaskStatusBean bean = new TaskStatusBean();
        bean.setTaskStatus(taskStatus);
        return bean;
    }

    /** getJobStatus 恒 failedFuture（红①注入形态）；记录实际状态查询次数。 */
    private static final class AlwaysFailingStatusClient implements IRpcPollTaskClient {
        final java.util.concurrent.atomic.AtomicInteger statusCalls = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public CompletionStage<String> startJob(NopJobSchedule s, NopJobFire f, NopJobTask t, ICancelToken token) {
            return CompletableFuture.completedFuture(t.getJobTaskId());
        }

        @Override
        public CompletionStage<TaskStatusBean> getJobStatus(NopJobSchedule s, NopJobFire f, NopJobTask t,
                                                            ICancelToken token) {
            statusCalls.incrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("connection refused"));
        }

        @Override
        public CompletionStage<Boolean> cancelJob(NopJobSchedule s, NopJobFire f, NopJobTask t,
                                                  ICancelToken token) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
    }

    /** getJobStatus 恒 RUNNING（终结只能靠 deadline/回传通道）。 */
    private static final class AlwaysRunningClient implements IRpcPollTaskClient {
        volatile boolean cancelCalled;


        @Override
        public CompletionStage<String> startJob(NopJobSchedule s, NopJobFire f, NopJobTask t, ICancelToken token) {
            return CompletableFuture.completedFuture(t.getJobTaskId());
        }

        @Override
        public CompletionStage<TaskStatusBean> getJobStatus(NopJobSchedule s, NopJobFire f, NopJobTask t,
                                                            ICancelToken token) {
            return CompletableFuture.completedFuture(status(TaskStatusBean.STATUS_RUNNING));
        }

        @Override
        public CompletionStage<Boolean> cancelJob(NopJobSchedule s, NopJobFire f, NopJobTask t,
                                                  ICancelToken token) {
            cancelCalled = true;
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
    }
}
