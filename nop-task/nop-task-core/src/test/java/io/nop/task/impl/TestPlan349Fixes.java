package io.nop.task.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskErrors;
import io.nop.task.TaskStepReturn;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.exceptions.NopTaskCancelledException;
import io.nop.task.metrics.TaskFlowMetricsImpl;
import io.nop.task.state.DefaultTaskStateStore;
import io.nop.task.state.FullSnapshotTaskStateStore;
import io.nop.task.state.TaskStateBean;
import io.nop.task.state.TaskStepStateBean;
import io.nop.task.step.AbstractTaskStep;
import io.nop.task.step.ExecutorTaskStepWrapper;
import io.nop.task.step.LoopNTaskStep;
import io.nop.task.step.SleepTaskStep;
import io.nop.task.step.TaskStepExecution;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_WAIT_STEP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 349 Phase 2-5 修复回归：状态码对齐、终态守卫、图模式错误边/挂死、wrapper 取消终结不变量、
 * 边界守卫与运行时修复。每条对应分析报告中确认的 live defect。
 */
public class TestPlan349Fixes extends AbstractTaskTestCase {

    @BeforeEach
    public void setUpBeans() {
        StaticBeanContainer beans = new StaticBeanContainer();
        beans.registerBean("myExecutor", GlobalExecutors.globalWorker());
        BeanContainer.registerInstance(beans);
    }

    private TaskStepReturn execute(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        return task.execute(taskRt);
    }

    // ==================== Phase 2: 状态码对齐 + 终态守卫 ====================

    /**
     * 引擎状态常量必须与 ORM 字典（task-status / task-step-status）及生成常量数值一致。
     * 修复前 TaskConstants 使用 30/40/50/60 体系，DB 行经 ext:dict 展示全部错位
     * （COMPLETED 显示"执行中"、KILLED 显示"已完成"等）。
     */
    @Test
    public void statusConstantsAlignWithDictAndGeneratedConstants() {
        assertEquals(_NopTaskCoreConstants.TASK_STATUS_SUSPENDED, TaskConstants.TASK_STATUS_SUSPENDED);
        assertEquals(_NopTaskCoreConstants.TASK_STATUS_ACTIVATED, TaskConstants.TASK_STATUS_ACTIVE,
                "engine ACTIVE must equal dict ACTIVATED(30)");
        assertEquals(_NopTaskCoreConstants.TASK_STATUS_COMPLETED, TaskConstants.TASK_STATUS_COMPLETED);
        assertEquals(_NopTaskCoreConstants.TASK_STATUS_EXPIRED, TaskConstants.TASK_STATUS_TIMEOUT,
                "engine TIMEOUT must equal dict EXPIRED(50)");
        assertEquals(_NopTaskCoreConstants.TASK_STATUS_FAILED, TaskConstants.TASK_STATUS_FAILED);
        assertEquals(_NopTaskCoreConstants.TASK_STATUS_KILLED, TaskConstants.TASK_STATUS_KILLED);
        assertEquals(_NopTaskCoreConstants.TASK_STEP_STATUS_ACTIVATED, TaskConstants.TASK_STEP_STATUS_ACTIVE,
                "engine step ACTIVE must equal dict ACTIVATED(30)");

        // 字典字面值锚定（源：nop-task/model/nop-task.orm.xml 的 task/task-status、task/task-step-status dict）
        assertEquals(30, TaskConstants.TASK_STATUS_ACTIVE);
        assertEquals(40, TaskConstants.TASK_STATUS_COMPLETED);
        assertEquals(50, TaskConstants.TASK_STATUS_TIMEOUT);
        assertEquals(60, TaskConstants.TASK_STATUS_FAILED);
        assertEquals(70, TaskConstants.TASK_STATUS_KILLED);
        assertEquals(30, TaskConstants.TASK_STEP_STATUS_ACTIVE);
    }

    /** step 级终态守卫：已终态（如 kill 竞态先到）不得被覆写为 COMPLETED/FAILED（first-terminal-wins）。 */
    @Test
    public void stepTerminalStateCannotBeOverwritten() {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_KILLED);

        state.succeed(1, null, null);
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_KILLED), state.getStepStatus(),
                "succeed after KILLED must not overwrite terminal status");

        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        assertEquals(Integer.valueOf(_NopTaskCoreConstants.TASK_STEP_STATUS_KILLED), state.getStepStatus(),
                "setStepStatus after terminal must be ignored");
    }

    /** task 级终态守卫：driveTaskCompleted 在已终态（KILLED）上不得覆写。 */
    @Test
    public void taskTerminalStateCannotBeOverwritten() throws Exception {
        TaskImpl task = new TaskImpl("t", 0, new NoopStep(), false, null, null, null, null);
        TaskStateBean state = new TaskStateBean();
        state.setTaskInstanceId("ti-1");
        state.setTaskName("t");
        state.setTaskStatus(TaskConstants.TASK_STATUS_KILLED);
        state.exception(new NopTaskCancelledException("kill"));

        Method m = TaskImpl.class.getDeclaredMethod("driveTaskCompleted",
                ITaskRuntime.class, io.nop.task.ITaskState.class, TaskStepReturn.class);
        m.setAccessible(true);
        m.invoke(task, FakeStepRt.fakeTaskRuntime(), state, TaskStepReturn.CONTINUE);

        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_KILLED), state.getTaskStatus(),
                "late COMPLETED driver must not overwrite KILLED terminal (async complete vs cancel race)");
    }

    /** DefaultTaskStateStore.newMainStepState 必须设置 taskInstanceId/stepInstanceId（对齐 Dao 实现）。 */
    @Test
    public void newMainStepStateSetsInstanceIds() {
        DefaultTaskStateStore store = new DefaultTaskStateStore();
        TaskStateBean taskState = new TaskStateBean();
        taskState.setTaskInstanceId("ti-2");

        io.nop.task.ITaskStepState state = store.newMainStepState(taskState);
        assertEquals("ti-2", state.getTaskInstanceId(),
                "mainStep state must carry taskInstanceId (pre-fix: null)");
        assertNotNull(state.getStepInstanceId(), "mainStep state must carry stepInstanceId (pre-fix: null)");
    }

    // ==================== Phase 3: 图模式 ====================

    /**
     * 错误边（nextOnError）：节点失败时错误分支必须被执行并以 exit 正常完成图。
     * 修复前任何节点失败直接 fail-fast 整图，错误边永不可达（check2 P1）。
     */
    @Test
    public void graphErrorBranchRunsOnFailure() {
        Map<String, Object> ret = execute("test/graph-error-01").syncGetOutputs();
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "error branch must run and drive the graph to normal completion");
    }

    /**
     * waitError 依赖的步骤成功完成时，错误分支按"跳过"处理，图正常完成。
     * 修复前 waitFuture 永不完成，图挂死。
     */
    @Test
    public void graphErrorBranchSkippedWhenNoError() throws Exception {
        TaskStepReturn ret = execute("test/graph-error-skip-01");
        Map<String, Object> outs = ret.getReturnPromise().toCompletableFuture()
                .get(15, TimeUnit.SECONDS).syncGetOutputs();
        assertEquals("OK", outs.get(TaskConstants.VAR_RESULT),
                "graph must complete when the waitError dependency succeeds (skip semantics); pre-fix: hang");
    }

    /** TaskFlowAnalyzer 递归：嵌套在 if/then 内的 graph 的 waitSteps 拼错必须在构建期报错。 */
    @Test
    public void ifNestedBadWaitStepRefRejectedAtBuildTime() {
        NopException err = assertThrows(NopException.class,
                () -> taskFlowManager.getTask("test/if-nested-badref", 0),
                "waitSteps typo inside nested if/then graph must be rejected at build time "
                        + "(pre-fix: forEachStep missed grandchildren, error deferred to runtime NPE)");
        assertEquals(ERR_TASK_UNKNOWN_WAIT_STEP.getErrorCode(), err.getErrorCode());
    }

    // ==================== Phase 4: wrapper 取消终结不变量 ====================

    /**
     * executor 包装：取消后返回 promise 必须以取消异常在有限时间内终结。
     * 修复前 ret 无 complete 路径，上层续延永久挂死。
     */
    @Test
    public void executorWrapperCancelTerminatesReturnValue() throws Exception {
        Cancellable token = new Cancellable();
        Map<String, Object> holders = new HashMap<>();
        holders.put("cancelToken", (ICancelToken) token);
        ITaskStepRuntime stepRt = FakeStepRt.of(new TaskStepStateBean(), holders);

        CompletableFuture<TaskStepReturn> never = new CompletableFuture<>();
        ExecutorTaskStepWrapper wrapper = new ExecutorTaskStepWrapper(new NeverStep(never), "myExecutor");
        TaskStepReturn ret = wrapper.execute(stepRt);
        assertTrue(ret.isAsync());

        token.cancel("kill");
        try {
            ret.getReturnPromise().toCompletableFuture().get(10, TimeUnit.SECONDS);
            throw new AssertionError("cancelled executor step must fail, not complete");
        } catch (ExecutionException e) {
            assertInstanceOf(NopTaskCancelledException.class, e.getCause(),
                    "cancelled executor step must terminate with cancellation exception (pre-fix: hang forever)");
        }
    }

    /**
     * 超时竞速：body 返回永不完成的 promise 且不检查 cancel token 时，
     * 超时必须仍使返回值以 TIMEOUT 取消异常终结。
     */
    @Test
    public void timeoutForcesCompletionOfUnresponsiveAsyncBody() throws Exception {
        CompletableFuture<TaskStepReturn> never = new CompletableFuture<>();
        TaskStepReturn ret = TaskStepHelper.timeout(300,
                c -> TaskStepReturn.ASYNC_RETURN(never),
                new Cancellable(), GlobalExecutors.globalTimer());

        try {
            ret.getReturnPromise().toCompletableFuture().get(10, TimeUnit.SECONDS);
            throw new AssertionError("timed-out step must fail, not complete");
        } catch (ExecutionException e) {
            assertInstanceOf(NopTaskCancelledException.class, e.getCause(),
                    "timeout must force-complete the returned promise (pre-fix: hang forever)");
            assertEquals("timeout", ((NopTaskCancelledException) e.getCause()).getCancelReason(),
                    "forced termination must carry TIMEOUT cancel reason so the task TIMEOUT driver can classify");
        }
        never.complete(TaskStepReturn.CONTINUE);
    }

    /** sleep 步骤被取消时必须抛取消异常，而不是静默 CONTINUE 记为成功。 */
    @Test
    public void sleepCancelledThrows() {
        Cancellable token = new Cancellable();
        token.cancel("kill");
        Map<String, Object> holders = new HashMap<>();
        holders.put("cancelToken", (ICancelToken) token);
        ITaskStepRuntime stepRt = FakeStepRt.of(new TaskStepStateBean(), holders);

        SleepTaskStep step = new SleepTaskStep();
        step.setSleepMillisExpr(scope -> 5000L);
        assertThrows(NopTaskCancelledException.class, () -> step.execute(stepRt),
                "cancelled sleep must throw cancellation (pre-fix: silent CONTINUE recorded as success)");
    }

    // ==================== Phase 5: 边界守卫与运行时修复 ====================

    /** fork producer 求值为 null：按空 fork 处理，不 NPE。 */
    @Test
    public void forkNullProducerYieldsEmptyFork() {
        Map<String, Object> ret = execute("test/fork-null-producer").syncGetOutputs();
        assertEquals("done", ret.get(TaskConstants.VAR_RESULT),
                "null producer result must be treated as empty fork (pre-fix: NPE)");
    }

    /** fork-n count=0：按空 fork 处理，不抛 IllegalArgumentException。 */
    @Test
    public void forkNZeroYieldsEmptyFork() {
        Map<String, Object> ret = execute("test/fork-n-zero").syncGetOutputs();
        assertEquals("done", ret.get(TaskConstants.VAR_RESULT),
                "count=0 must be treated as empty fork (pre-fix: AsyncHelper Guard IAE)");
    }

    /** LoopN 异步路径 until 满足：返回 CONTINUE（循环正常结束、后续步骤继续执行），与同步路径一致。 */
    @Test
    public void loopnAsyncUntilContinuesNotEnd() throws Exception {
        LoopNTaskStep step = new LoopNTaskStep();
        step.setBeginExpr(scope -> 0);
        step.setEndExpr(scope -> 2);
        step.setIndexName("i");
        step.setUntilExpr(scope -> true);

        AtomicInteger runs = new AtomicInteger();
        CompletableFuture<TaskStepReturn> pending = new CompletableFuture<>();
        step.setBody(new AbstractTaskStep() {
            @Nonnull
            @Override
            public TaskStepReturn execute(ITaskStepRuntime rt) {
                runs.incrementAndGet();
                return TaskStepReturn.ASYNC(null, pending);
            }
        });

        TaskStepReturn ret = step.execute(FakeStepRt.of(new TaskStepStateBean()));
        pending.complete(TaskStepReturn.CONTINUE);

        TaskStepReturn synced = ret.sync();
        assertFalse(synced.isEnd(),
                "async until-satisfied must end the loop with CONTINUE semantics "
                        + "(pre-fix: RETURN_RESULT_END, terminating the outer sequential)");
        assertEquals(1, runs.get(), "loop body must run exactly once before until fires");
    }

    /** TaskStepReturn.of：显式 nextStepName（End/Exit 哨兵）优先于 returnValue 自带的跳转。 */
    @Test
    public void taskStepReturnOfExplicitNextWins() {
        TaskStepReturn ret = TaskStepReturn.of(TaskConstants.STEP_NAME_END,
                TaskStepReturn.RETURN("other", Collections.singletonMap("x", 1)));
        assertTrue(ret.isEnd(),
                "makeReturn(END, ret) must not silently drop the END sentinel when ret carries its own nextStepName");
    }

    /** 终态 FAILED 步骤配置了 nextOnError：resume 时经错误分支续跑而非重抛（错误分支可再入）。 */
    @Test
    public void nextOnErrorFailedStepResumeEntersErrorBranch() {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED);
        state.exception(new NopException(TaskErrors.ERR_TASK_STEP_ALREADY_FAILED));

        Map<String, Object> holders = new HashMap<>();
        holders.put("childStepRt", FakeStepRt.of(state));
        ITaskStepRuntime parentRt = FakeStepRt.of(new TaskStepStateBean(), holders);

        TaskStepExecution exec = new TaskStepExecution(null, "failStep",
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                null, null, new NoopStep(), null, "handler", false, null, false);

        TaskStepReturn ret = exec.executeWithParentRt(parentRt);
        assertEquals("handler", ret.getNextStepName(),
                "resume of FAILED step with nextOnError must re-enter the error branch (pre-fix: rethrow, "
                        + "error branch unreachable after resume)");
        // buildErrorResult 的返回经 TaskStepReturn.of 包装：错误负载在 RESULT 下，同时 errorName 变量写入父 scope
        assertNotNull(ret.getResult(), "error result payload must be present");
        assertNotNull(parentRt.getEvalScope().getLocalValue("ERROR"),
                "errorName variable must be exported to parent scope for the error branch");
    }

    /** resetGlobalStats 必须同时清零全局限流器统计（修复前只重置信号量）。 */
    @Test
    public void resetGlobalStatsClearsRateLimiterStats() {
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        ITaskRuntime taskRt = FakeStepRt.fakeTaskRuntime();
        IRateLimiter limiter = manager.getRateLimiter(taskRt, "k", 100, true);
        assertTrue(limiter.tryAcquire(1), "acquire should succeed and bump stats");

        manager.resetGlobalStats();

        Map<String, IRateLimiter.RateLimiterStats> stats = manager.getGlobalRateLimiterStats();
        IRateLimiter.RateLimiterStats limiterStats = stats.get("fake-task:k");
        assertNotNull(limiterStats, "global rate limiter must remain visible after reset");
        assertEquals(0, limiterStats.getAcquireSuccessCount(),
                "resetGlobalStats must clear rate limiter acquire stats (pre-fix: semaphore only)");
    }

    /** resume 路径（getTaskRuntime）构造的 runtime 必须启用真实指标（与 fresh 执行对称）。 */
    @Test
    public void resumeRuntimeHasRealMetrics() {
        FullSnapshotTaskStateStore store = new FullSnapshotTaskStateStore();
        TaskFlowManagerImpl manager = (TaskFlowManagerImpl) taskFlowManager;
        manager.setNonPersistStateStore(store);
        manager.setTaskStateStore(store);
        try {
            ITask task = taskFlowManager.getTask("test/suspend-plain", 0);
            ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
            task.execute(taskRt); // 挂起 → saveTaskState → snapshot
            String taskInstanceId = taskRt.getTaskInstanceId();

            ITaskRuntime resumeRt = manager.getTaskRuntime(taskInstanceId, null, null);
            assertInstanceOf(TaskFlowMetricsImpl.class, resumeRt.getMetrics(),
                    "resume runtime must record task/step metrics (pre-fix: EmptyTaskFlowMetrics)");
        } finally {
            manager.setNonPersistStateStore(DefaultTaskStateStore.INSTANCE);
            manager.setTaskStateStore(null);
        }
    }

    /** newChildRuntime：svcCtx=null 时父取消必须传播到子 runtime（修复前自引用 no-op，子任务失控）。 */
    @Test
    public void newChildRuntimeCancelPropagatesToChild() {
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        TaskRuntimeImpl parent = new TaskRuntimeImpl(manager, DefaultTaskStateStore.INSTANCE, null, null, false);
        ITaskRuntime child = parent.newChildRuntime(FakeStepRt.fakeTask("child-task"), false);

        assertFalse(child.isCancelled());
        parent.cancel("kill");
        assertTrue(child.isCancelled(),
                "parent cancel must propagate to child runtime (pre-fix: this::cancel self-reference no-op)");
    }

    /** MultiStepResultBean setter 必须是替换语义，不能合并残留。 */
    @Test
    public void multiStepResultBeanSetterReplaces() {
        io.nop.task.step.MultiStepResultBean bean = new io.nop.task.step.MultiStepResultBean();
        bean.add("a", new io.nop.task.StepResultBean());
        bean.setStepResultBeanMap(Collections.singletonMap("b", new io.nop.task.StepResultBean()));
        assertEquals(1, bean.size(), "setter must replace, not merge (pre-fix: putAll kept stale 'a')");
        assertNotNull(bean.getStepResultBean("b"));
    }

    // ==================== 辅助 ====================

    static class NoopStep extends AbstractTaskStep {
        @Nonnull
        @Override
        public TaskStepReturn execute(ITaskStepRuntime stepRt) {
            return TaskStepReturn.CONTINUE;
        }
    }

    static class NeverStep extends AbstractTaskStep {
        private final CompletableFuture<TaskStepReturn> never;

        NeverStep(CompletableFuture<TaskStepReturn> never) {
            this.never = never;
        }

        @Nonnull
        @Override
        public TaskStepReturn execute(ITaskStepRuntime stepRt) {
            return TaskStepReturn.ASYNC_RETURN(never);
        }
    }
}
