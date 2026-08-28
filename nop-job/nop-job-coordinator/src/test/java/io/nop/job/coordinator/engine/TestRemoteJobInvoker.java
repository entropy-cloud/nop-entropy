package io.nop.job.coordinator.engine;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.util.ICancelToken;
import io.nop.job.api.JobInstanceState;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.core.JobCoreErrors;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.job.dao.entity.NopJobTask;
import io.nop.job.dao.store.FireScheduleOutcome;
import io.nop.job.dao.store.IJobFireStore;
import io.nop.job.dao.store.IJobScheduleStore;
import io.nop.job.dao.store.IJobTaskStore;
import io.nop.job.dao.store.WorkerReservedCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2254: RemoteJobInvoker（executorKind=rpcPoll）三段式语义测试。
 * 轮询段委托集中式 {@link RpcPollTaskManager}：startJob 异步成功 → register →
 * 管理器周期 getJobStatus（客户端三方法均为异步 mock）。
 * 使用最小轮询间隔（1000ms）与 short-timeout 的异步等待。
 */
public class TestRemoteJobInvoker {

    private static final String TASK_ID = "task-1";
    private static final String FIRE_ID = "fire-1";
    private static final String SCHEDULE_ID = "schedule-1";

    private NopJobTask task;
    private NopJobFire fire;
    private NopJobSchedule schedule;
    private MockTaskStore taskStore;
    private MockFireStore fireStore;
    private MockScheduleStore scheduleStore;
    private MockRpcPollTaskClient client;
    private RpcPollTaskManager pollTaskManager;
    private RemoteJobInvoker invoker;

    @BeforeEach
    void setUp() {
        task = new NopJobTask();
        task.setJobTaskId(TASK_ID);
        task.setJobFireId(FIRE_ID);
        task.setTaskStatus(0);

        fire = new NopJobFire();
        fire.setJobFireId(FIRE_ID);
        fire.setJobScheduleId(SCHEDULE_ID);

        schedule = new NopJobSchedule();
        schedule.setJobScheduleId(SCHEDULE_ID);
        schedule.setJobName("testJob");

        taskStore = new MockTaskStore();
        fireStore = new MockFireStore();
        scheduleStore = new MockScheduleStore();
        client = new MockRpcPollTaskClient();

        taskStore.stored = task;
        fireStore.stored = fire;
        scheduleStore.stored = schedule;

        pollTaskManager = new RpcPollTaskManager();
        pollTaskManager.setRpcPollTaskClient(client);
        pollTaskManager.setTaskStore(taskStore);
        pollTaskManager.setPollIntervalMs(1000);

        invoker = new RemoteJobInvoker();
        invoker.setTaskStore(taskStore);
        invoker.setFireStore(fireStore);
        invoker.setScheduleStore(scheduleStore);
        invoker.setRpcPollTaskClient(client);
        invoker.setPollTaskManager(pollTaskManager);
    }

    @Test
    void testStartJobReturnsEmpty_failsWithRemoteInvokeFailed() {
        client.startResult = "";

        JobFireResult result = await(invoker.invokeAsync(ctx(false)));

        assertTrue(result.isErrorResult());
        assertEquals(JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED.getErrorCode(), result.getError().getErrorCode());
    }

    @Test
    void testStartJobThrows_failsWithRemoteInvokeFailed() {
        client.startError = new IllegalStateException("connection refused");

        JobFireResult result = await(invoker.invokeAsync(ctx(false)));

        assertTrue(result.isErrorResult());
        // 非 NopException：保留笼统 REMOTE_INVOKE_FAILED
        assertEquals(JobCoreErrors.ERR_JOB_REMOTE_INVOKE_FAILED.getErrorCode(), result.getError().getErrorCode());
    }

    /**
     * check2 [P3-5]: startJob 抛出的 NopException 携带语义错误码时必须原样透传，
     * 不得被 invokeAsync 的 catch-all 抹平为 ERR_JOB_REMOTE_INVOKE_FAILED
     * （如"serviceName 未配置"被记成"远程调用失败"会误导排障与错误码告警分类）。
     */
    @Test
    void testStartJobThrowsNopException_originalErrorCodePreserved() {
        client.startError = new io.nop.api.core.exceptions.NopException(JobCoreErrors.ERR_JOB_SERVICE_NAME_REQUIRED);

        JobFireResult result = await(invoker.invokeAsync(ctx(false)));

        assertTrue(result.isErrorResult());
        assertEquals(JobCoreErrors.ERR_JOB_SERVICE_NAME_REQUIRED.getErrorCode(), result.getError().getErrorCode(),
                "semantic error code from startJob must be preserved, not flattened to REMOTE_INVOKE_FAILED");
    }

    @Test
    void testPollSuccess_completesWithContinue() {
        client.startResult = TASK_ID;
        client.statuses.add(status(TaskStatusBean.STATUS_RUNNING));
        client.statuses.add(status(TaskStatusBean.STATUS_SUCCESS));

        JobFireResult result = await(invoker.invokeAsync(ctx(false)));

        assertFalse(result.isErrorResult());
        assertNull(result.getError());
    }

    @Test
    void testPollFailure_completesWithWorkerError() {
        client.startResult = TASK_ID;
        client.statuses.add(status(TaskStatusBean.STATUS_RUNNING));
        ErrorBean workerError = new ErrorBean("nop.err.worker.failed").description("boom");
        TaskStatusBean failed = status(TaskStatusBean.STATUS_FAILURE);
        failed.setError(workerError);
        client.statuses.add(failed);

        JobFireResult result = await(invoker.invokeAsync(ctx(false)));

        assertTrue(result.isErrorResult());
        assertEquals("nop.err.worker.failed", result.getError().getErrorCode());
    }

    @Test
    void testPollCancelled_completesWithCanceled() {
        client.startResult = TASK_ID;
        client.statuses.add(status(TaskStatusBean.STATUS_RUNNING));
        client.statuses.add(status(TaskStatusBean.STATUS_CANCELLED));

        JobFireResult result = await(invoker.invokeAsync(ctx(false)));

        assertTrue(result.isErrorResult());
        assertEquals(JobCoreErrors.ERR_JOB_CANCELED.getErrorCode(), result.getError().getErrorCode());
    }

    @Test
    void testPollTimeout_completesWithTimeout() {
        client.startResult = TASK_ID;
        client.statuses.add(status(TaskStatusBean.STATUS_RUNNING));
        client.statuses.add(status(TaskStatusBean.STATUS_TIMEOUT));

        JobFireResult result = await(invoker.invokeAsync(ctx(false)));

        assertTrue(result.isErrorResult());
        assertEquals(JobCoreErrors.ERR_JOB_TIMEOUT.getErrorCode(), result.getError().getErrorCode());
    }

    @Test
    void testPollNotFound_completesWithRemoteTaskLost() {
        client.startResult = TASK_ID;
        client.statuses.add(status(TaskStatusBean.STATUS_RUNNING));
        client.statuses.add(status(TaskStatusBean.STATUS_NOT_FOUND));

        JobFireResult result = await(invoker.invokeAsync(ctx(false)));

        assertTrue(result.isErrorResult());
        assertEquals(JobCoreErrors.ERR_JOB_REMOTE_TASK_LOST.getErrorCode(), result.getError().getErrorCode());
    }

    @Test
    void testWallClockTimeout_cancelsRemoteAndCompletesWithTimeout() {
        schedule.setTimeoutSeconds(1);
        client.startResult = TASK_ID;
        client.statuses.add(status(TaskStatusBean.STATUS_RUNNING));
        client.statuses.add(status(TaskStatusBean.STATUS_RUNNING));

        JobFireResult result = await(invoker.invokeAsync(ctx(false)));

        assertTrue(result.isErrorResult());
        assertEquals(JobCoreErrors.ERR_JOB_TIMEOUT.getErrorCode(), result.getError().getErrorCode());
        assertTrue(client.cancelCalled, "wall-clock timeout must cancel remote job");
    }

    @Test
    void testCancelToken_cancelsRemoteAndCompletesWithCanceled() {
        client.startResult = TASK_ID;
        client.statuses.add(status(TaskStatusBean.STATUS_RUNNING));

        JobFireResult result = await(invoker.invokeAsync(ctx(true)));

        assertTrue(result.isErrorResult());
        assertEquals(JobCoreErrors.ERR_JOB_CANCELED.getErrorCode(), result.getError().getErrorCode());
        assertTrue(client.cancelCalled, "cancel token must cancel remote job");
    }

    @Test
    void testConcurrentlyFinalized_stopsPolling() {
        client.startResult = TASK_ID;
        client.statuses.add(status(TaskStatusBean.STATUS_RUNNING));
        // after first poll, task is finalized concurrently
        client.statuses.add(status(TaskStatusBean.STATUS_RUNNING));

        NopJobTask fresh = new NopJobTask();
        fresh.setJobTaskId(TASK_ID);
        fresh.setJobFireId(FIRE_ID);
        fresh.setTaskStatus(50); // TASK_STATUS_TIMEOUT (concurrently finalized)
        taskStore.freshTask = fresh;

        JobFireResult result = await(invoker.invokeAsync(ctx(false)));

        assertTrue(result.isErrorResult());
        assertEquals(JobCoreErrors.ERR_JOB_CANCELED.getErrorCode(), result.getError().getErrorCode());
        assertFalse(client.cancelCalled, "concurrently finalized task must not trigger cancel");
    }

    @Test
    void testCancelAsync_callsCancelJob() throws Exception {
        client.startResult = TASK_ID;

        CompletionStage<Boolean> stage = invoker.cancelAsync(ctx(false));
        Boolean cancelled = await(stage);

        assertTrue(cancelled);
        assertTrue(client.cancelCalled);
    }

    /**
     * check2 [P1-2]（升级为异步语义）：单个挂起的 getJobStatus 不得阻塞其他 rpcPoll 任务的轮询。
     * 此前所有轮询回调共享静态单线程执行器——task-a 的 poll 挂起时 task-b 的 poll 永远排不上；
     * 修复后为小型线程池。**客户端异步化后**语义升级：task-a 的 getJobStatus 阻塞（同步阻塞的
     * mock 实现模拟最坏情况）时，task-b 的 poll 必须仍能完成并 resolve future——集中式管理器的
     * 单一调度循环不为单个在途 RPC 阻塞。
     */
    @Test
    void testHungPollDoesNotBlockOtherPollTasks() throws Exception {
        CountDownLatch hungEntered = new CountDownLatch(1);
        CountDownLatch releaseHung = new CountDownLatch(1);
        AtomicInteger hungPollCount = new AtomicInteger();

        BlockingPollClient selective = new BlockingPollClient(hungEntered, releaseHung, hungPollCount);
        MockTaskStore perTaskStore = new MockTaskStore() {
            @Override
            public NopJobTask loadTask(String jobTaskId) {
                NopJobTask t = new NopJobTask();
                t.setJobTaskId(jobTaskId);
                t.setJobFireId(FIRE_ID);
                t.setTaskStatus(20);
                return t;
            }
        };
        invoker.setRpcPollTaskClient(selective);
        invoker.setTaskStore(perTaskStore);
        pollTaskManager.setRpcPollTaskClient(selective);
        pollTaskManager.setTaskStore(perTaskStore);

        CompletableFuture<JobFireResult> futureA =
                (CompletableFuture<JobFireResult>) invoker.invokeAsync(ctxForTask("task-a"));
        CompletableFuture<JobFireResult> futureB =
                (CompletableFuture<JobFireResult>) invoker.invokeAsync(ctxForTask("task-b"));

        // task-b 的 poll（~1s 后）返回 SUCCESS → future 完成；此时 task-a 的 poll 仍挂起
        JobFireResult resultB = futureB.get(10, TimeUnit.SECONDS);
        assertFalse(resultB.isErrorResult(), "task-b poll must complete while task-a poll is hung");
        assertTrue(hungEntered.await(10, TimeUnit.SECONDS), "task-a poll must have started (and be hung)");

        // 释放 task-a：第二次 poll 返回 CANCELLED → future 终结、条目移出注册表（测试清理）
        releaseHung.countDown();
        JobFireResult resultA = futureA.get(10, TimeUnit.SECONDS);
        assertTrue(resultA.isErrorResult());
        assertEquals(JobCoreErrors.ERR_JOB_CANCELED.getErrorCode(), resultA.getError().getErrorCode());
    }

    /** task-a 的 getJobStatus 阻塞在 latch 上（第一次），释放后第二次返回 CANCELLED。 */
    private static final class BlockingPollClient implements IRpcPollTaskClient {
        private final CountDownLatch hungEntered;
        private final CountDownLatch releaseHung;
        private final AtomicInteger hungPollCount;

        BlockingPollClient(CountDownLatch hungEntered, CountDownLatch releaseHung, AtomicInteger hungPollCount) {
            this.hungEntered = hungEntered;
            this.releaseHung = releaseHung;
            this.hungPollCount = hungPollCount;
        }

        @Override
        public CompletionStage<String> startJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            return CompletableFuture.completedFuture(task.getJobTaskId());
        }

        @Override
        public CompletionStage<TaskStatusBean> getJobStatus(NopJobSchedule schedule, NopJobFire fire,
                                                            NopJobTask task) {
            if ("task-a".equals(task.getJobTaskId())) {
                if (hungPollCount.incrementAndGet() == 1) {
                    hungEntered.countDown();
                    try {
                        releaseHung.await(15, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return CompletableFuture.completedFuture(status(TaskStatusBean.STATUS_RUNNING));
                }
                return CompletableFuture.completedFuture(status(TaskStatusBean.STATUS_CANCELLED));
            }
            return CompletableFuture.completedFuture(status(TaskStatusBean.STATUS_SUCCESS));
        }

        @Override
        public CompletionStage<Boolean> cancelJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            return CompletableFuture.completedFuture(true);
        }
    }

    private IJobExecutionContext ctxForTask(String taskId) {
        Ctx c = new Ctx();
        c.setAttribute("jobTaskId", taskId);
        c.setAttribute("jobFireId", FIRE_ID);
        c.setCancelToken(new ICancelToken() {
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public String getCancelReason() {
                return null;
            }

            @Override
            public void appendOnCancel(java.util.function.Consumer<String> task) {
            }

            @Override
            public void removeOnCancel(java.util.function.Consumer<String> task) {
            }
        });
        return c;
    }

    private IJobExecutionContext ctx(boolean cancelled) {
        Ctx c = new Ctx();
        c.setAttribute("jobTaskId", TASK_ID);
        c.setAttribute("jobFireId", FIRE_ID);
        c.setCancelToken(new ICancelToken() {
            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public String getCancelReason() {
                return null;
            }

            @Override
            public void appendOnCancel(java.util.function.Consumer<String> task) {
            }

            @Override
            public void removeOnCancel(java.util.function.Consumer<String> task) {
            }
        });
        return c;
    }

    private static TaskStatusBean status(int taskStatus) {
        TaskStatusBean bean = new TaskStatusBean();
        bean.setTaskStatus(taskStatus);
        return bean;
    }

    private static <T> T await(CompletionStage<T> stage) {
        try {
            return ((CompletableFuture<T>) stage).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("timed out waiting for async result", e);
        }
    }

    private static class Ctx extends JobInstanceState implements IJobExecutionContext {
        private ICancelToken cancelToken;

        void setCancelToken(ICancelToken cancelToken) {
            this.cancelToken = cancelToken;
        }

        @Override
        public long getMinScheduleTime() {
            return 0;
        }

        @Override
        public long getMaxScheduleTime() {
            return 0;
        }

        @Override
        public long getMaxExecutionCount() {
            return 0;
        }

        @Override
        public long getMaxFailedCount() {
            return 0;
        }

        @Override
        public boolean isJobFinished() {
            return false;
        }

        @Override
        public boolean isInstanceRunning() {
            return false;
        }

        @Override
        public boolean isScheduleEnabled() {
            return false;
        }

        @Override
        public ICancelToken getCancelToken() {
            return cancelToken;
        }
    }

    private static class MockTaskStore implements IJobTaskStore {
        NopJobTask stored;
        NopJobTask freshTask;

        @Override
        public NopJobTask loadTask(String jobTaskId) {
            return freshTask != null ? freshTask : stored;
        }

        @Override
        public boolean updateTask(NopJobTask task) {
            return true;
        }

        @Override
        public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions) {
            return Collections.emptyList();
        }

        @Override
        public List<NopJobTask> fetchWaitingTasks(int limit, IntRangeSet partitions,
                                                  String workerInstanceId, boolean enforceAttribution) {
            return Collections.emptyList();
        }

        @Override
        public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId,
                                                       long lockTimeoutMs) {
            return tasks;
        }

        @Override
        public List<NopJobTask> fetchRunningTasks(int limit, IntRangeSet partitions,
                                                  Timestamp cursorTime, String cursorId) {
            return Collections.emptyList();
        }

        @Override
        public List<NopJobTask> findTasksByFireId(String jobFireId) {
            return Collections.emptyList();
        }

        @Override
        public long countInFlightTasks(String workerInstanceId) {
            return 0;
        }

        @Override
        public ResourceVector sumReservedCost(String workerInstanceId) {
            return new ResourceVector(0, 0);
        }

        @Override
        public List<WorkerReservedCost> sumReservedCostByWorker() {
            return Collections.emptyList();
        }

        @Override
        public List<NopJobTask> resetStaleWaitingTasks(int batchSize, IntRangeSet partitions, long deadlineMs,
                                                       Timestamp cursorTime, String cursorId) {
            return Collections.emptyList();
        }
    }

    private static class MockFireStore implements IJobFireStore {
        NopJobFire stored;

        @Override
        public NopJobFire loadFire(String jobFireId) {
            return stored;
        }

        @Override
        public List<NopJobFire> fetchWaitingFires(int limit, IntRangeSet partitions) {
            return Collections.emptyList();
        }

        @Override
        public List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions) {
            return Collections.emptyList();
        }

        @Override
        public List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> fires, String dispatchInstanceId,
                                                        long lockTimeoutMs) {
            return fires;
        }

        @Override
        public void insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks) {
        }

        @Override
        public FireScheduleOutcome completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
            return null;
        }

        @Override
        public FireScheduleOutcome cancelFire(String jobFireId) {
            return null;
        }

        @Override
        public NopJobFire getFireById(String jobFireId) {
            return stored;
        }

        @Override
        public Map<String, NopJobFire> batchLoadFires(Set<String> fireIds) {
            return Collections.emptyMap();
        }

        @Override
        public List<NopJobFire> fetchDispatchingFires(int limit, IntRangeSet partitions,
                                                      Timestamp cursorTime, String cursorId) {
            return Collections.emptyList();
        }

        @Override
        public boolean revertDispatchingFireToWaiting(NopJobFire fire, long backoffUntilMs) {
            return false;
        }

        @Override
        public boolean failFireWithoutSchedule(String jobFireId, String errorCode, String errorMessage) {
            return true;
        }
    }

    private static class MockScheduleStore implements IJobScheduleStore {
        NopJobSchedule stored;

        @Override
        public NopJobSchedule loadSchedule(String jobScheduleId) {
            return stored;
        }

        @Override
        public List<NopJobSchedule> fetchDueSchedules(int limit, IntRangeSet partitions) {
            return Collections.emptyList();
        }

        @Override
        public List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> schedules,
                                                            String plannerInstanceId, long lockTimeoutMs) {
            return schedules;
        }

        @Override
        public void advanceScheduleAfterSkip(NopJobSchedule schedule, Timestamp nextFireTime) {
        }

        @Override
        public void insertFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime,
                                                 Integer triggerSource) {
        }

        @Override
        public void overlayFireAndAdvanceSchedule(NopJobSchedule schedule, NopJobFire fire, Timestamp nextFireTime,
                                                  Integer triggerSource) {
        }

        @Override
        public void recoveryFireAndAdvanceSchedule(NopJobSchedule schedule, Timestamp nextFireTime) {
        }

        @Override
        public boolean insertManualFire(NopJobSchedule schedule, NopJobFire fire) {
            return true;
        }

        @Override
        public NopJobSchedule tryLoadSchedule(String jobScheduleId) {
            return stored;
        }

        @Override
        public Map<String, NopJobSchedule> batchLoadSchedules(Set<String> scheduleIds) {
            return Collections.emptyMap();
        }

        @Override
        public long getCurrentTime() {
            return System.currentTimeMillis();
        }
    }

    private static class MockRpcPollTaskClient implements IRpcPollTaskClient {
        String startResult;
        RuntimeException startError;
        final java.util.List<TaskStatusBean> statuses = new java.util.ArrayList<>();
        boolean cancelCalled;

        @Override
        public CompletionStage<String> startJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            if (startError != null) {
                return CompletableFuture.failedFuture(startError);
            }
            return CompletableFuture.completedFuture(startResult);
        }

        @Override
        public CompletionStage<TaskStatusBean> getJobStatus(NopJobSchedule schedule, NopJobFire fire,
                                                            NopJobTask task) {
            return CompletableFuture.completedFuture(
                    statuses.isEmpty() ? status(TaskStatusBean.STATUS_RUNNING) : statuses.remove(0));
        }

        @Override
        public CompletionStage<Boolean> cancelJob(NopJobSchedule schedule, NopJobFire fire, NopJobTask task) {
            cancelCalled = true;
            return CompletableFuture.completedFuture(true);
        }
    }
}