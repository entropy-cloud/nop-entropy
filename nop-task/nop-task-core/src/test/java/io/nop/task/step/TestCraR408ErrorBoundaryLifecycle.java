package io.nop.task.step;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.core.context.IServiceContext;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepReturn;
import io.nop.task.impl.FakeStepRt;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.state.SnapshotTaskStateStore;
import io.nop.task.state.TaskStepStateBean;
import io.nop.task.utils.TaskStepHelper;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.task.TaskErrors.ERR_TASK_STEP_CONFIG_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.48 R4-08（plan 2541）组 B 取消/清理契约域钉族：W-06（MA-13 Throwable 边界——C10-013
 * 宿主：Error 注入 permit 回满+promise 5s 终结断言一套禁拆分；C8-016 矩阵腿 ii）、W-07（MA-14
 * 生命周期解耦：(a) winner→body seam 取消信号行为腿 (d) 延迟重试撤回行为腿；(b)(iii) 契约
 * 文档结构钉归 E5、(c) permit 早释为裁定语义——owner doc 声明承接）、W-09（MA-16 sleep
 * 运行期上限路线 β + 负值/零值既有 no-op 语义零触碰）。
 */
public class TestCraR408ErrorBoundaryLifecycle {

    private static final ErrorCode TEST_PROBE_ERROR =
            define("nop.err.test.r408.probe", "r408 test probe");


    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ==================== W-06 / C10-013（红⑥）：Error 注入三断言一套 ====================

    @Test
    public void throttleBodyThrowsError_permitReturnedAndErrorRethrown() {
        // C10-013 断言套（禁拆分）throttle body 同步抛出形态：
        // ①Error 原样传播（不 adapt 包装、不吞）②permit 回满（修复前泄漏 → 该 key 永久全拒）
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        ITaskRuntime taskRt = manager.newTaskRuntime(FakeStepRt.fakeTask("r408-w06"), false, null);
        ITaskStepRuntime mainRt = taskRt.newMainStepRuntime();

        var sem = taskRt.getSemaphore("r408-w06-key", 2, true);
        assertEquals(2, sem.availablePermits());

        ThrottleTaskStepWrapper wrapper = new ThrottleTaskStepWrapper(new AbstractTaskStep() {
            @Override
            public TaskStepReturn execute(ITaskStepRuntime rt) {
                throw new AssertionError("r408-error-boundary-probe");
            }
        }, true, 2, 100, (IEvalAction) scope -> "r408-w06-key");

        AssertionError err = assertThrows(AssertionError.class, () -> wrapper.execute(mainRt),
                "Error must be rethrown as-is (not adapted/wrapped) on the throttle sync path (W-06 rethrow necessity)");
        assertEquals("r408-error-boundary-probe", err.getMessage());
        assertEquals(2, sem.availablePermits(),
                "permit must return to the pool after synchronous Error (pre-fix: leaked)");
    }

    @Test
    public void executorBodyThrowsError_promiseTerminatesWithin5s() throws Exception {
        // C10-013 断言③：executor 回调 Error 注入形态——ret promise 必然终结（5s 口径，
        // 闩带上界 30s 纪律防测试自身挂死）。修复前 Error 穿透 catch(Exception) → ret 无
        // 终结路径 → 任务永驻非终态、清理/终态 driver 全跳过零信号
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        ITaskRuntime taskRt = manager.newTaskRuntime(FakeStepRt.fakeTask("r408-w06e"), false, null);
        ITaskStepRuntime mainRt = taskRt.newMainStepRuntime();

        ExecutorTaskStepWrapper wrapper = new ExecutorTaskStepWrapper(new AbstractTaskStep() {
            @Override
            public TaskStepReturn execute(ITaskStepRuntime rt) {
                throw new AssertionError("r408-executor-error-probe");
            }
        }, "nop-global-worker");

        TaskStepReturn ret = wrapper.execute(mainRt);
        CompletableFuture<TaskStepReturn> promise = ret.getReturnPromise().toCompletableFuture();

        try {
            promise.get(5, TimeUnit.SECONDS);
            fail("promise must complete exceptionally with the injected Error (C10-013 5s termination)");
        } catch (Exception expected) {
            Throwable cause = expected.getCause() != null ? expected.getCause() : expected;
            boolean errorSeen = cause instanceof AssertionError
                    || (cause.getCause() instanceof AssertionError);
            assertTrue(errorSeen, "promise completion must carry the AssertionError, got: " + expected);
        }
    }

    @Test
    public void runOnContextBodyThrowsError_futureTerminates() throws Exception {
        // C10-013 第二腿（RunOnContext 同形态）：context.execute 派发的步骤体抛 Error →
        // future 必然终结（异步 context 形态——runnable 的 Error 被 context 执行器捕获，
        // future 完成 + 线程侧传播双通道断言）。real ServiceContextImpl 委托 + 定制
        // IContext.execute（独立线程执行，捕获 runnable Throwable——生产 context 执行器语义）
        io.nop.core.context.ServiceContextImpl delegate = new io.nop.core.context.ServiceContextImpl();
        java.util.concurrent.atomic.AtomicReference<Throwable> contextCaught =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.ExecutorService contextExecutor =
                java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "r408-ctx-exec");
                    t.setDaemon(true);
                    return t;
                });
        io.nop.api.core.context.IContext customCtx = (io.nop.api.core.context.IContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{io.nop.api.core.context.IContext.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        Runnable r = (Runnable) args[0];
                        contextExecutor.execute(() -> {
                            try {
                                r.run();
                            } catch (Throwable t) {
                                // context 执行器语义：runnable 的 Throwable 被执行器边界捕获
                                contextCaught.set(t);
                            }
                        });
                        return null;
                    }
                    try {
                        return method.invoke(delegate.getContext(), args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        IServiceContext svcCtx = (IServiceContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{IServiceContext.class},
                (proxy, method, args) -> {
                    if ("getContext".equals(method.getName())) {
                        return customCtx;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        try {
            TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
            ITaskRuntime taskRt = manager.newTaskRuntime(FakeStepRt.fakeTask("r408-w06r"), false, svcCtx);
            ITaskStepRuntime mainRt = taskRt.newMainStepRuntime();

            RunOnContextTaskStepWrapper wrapper = new RunOnContextTaskStepWrapper(new AbstractTaskStep() {
                @Override
                public TaskStepReturn execute(ITaskStepRuntime rt) {
                    throw new AssertionError("r408-runoncontext-error-probe");
                }
            });

            TaskStepReturn ret = wrapper.execute(mainRt);
            CompletableFuture<TaskStepReturn> promise = ret.getReturnPromise().toCompletableFuture();
            try {
                promise.get(5, TimeUnit.SECONDS);
                fail("future must complete exceptionally with the injected Error");
            } catch (Exception expected) {
                Throwable cause = expected.getCause() != null ? expected.getCause() : expected;
                boolean errorSeen = cause instanceof AssertionError
                        || (cause.getCause() instanceof AssertionError);
                assertTrue(errorSeen,
                        "future completion must carry the AssertionError (C10-013 5s termination), got: "
                                + expected);
            }
            assertTrue(contextCaught.get() instanceof AssertionError,
                    "Error must also propagate thread-side (rethrow necessity — not swallowed by the wrapper)");
        } finally {
            contextExecutor.shutdownNow();
        }
    }

    // ==================== W-06 C8-016 矩阵腿 ii：TaskStepExecution sync 出口 Error ====================

    @Test
    public void taskStepExecutionSyncError_terminalDriverRunsAndErrorRethrown() {
        // 矩阵腿 ii（裁定 B 抽取承载）：步骤体同步抛 Error → sync 出口 catch(Throwable) 收宽
        // → Error 亦进 driveStepFailure 既有通道（终态 FAILED driver/meter 可达）→ Error 原样
        // 传播。修复前：Error 穿透 catch(Exception) → 终态 driver 全跳过（snapshot 停留
        // ACTIVE、终态 save 缺失）——双断言即红。
        // 载体说明：XPL 步骤边界会把 throw 的 Error 包装为 NopEvalException（Exception 路径），
        // 模型链路不可注入裸 Error——本腿以 Java 直构 TaskStepExecution（body 为原生步骤对象
        // 直抛 AssertionError）承载；AssertionError 直注入与 C10-013 两形态腿同型
        SnapshotTaskStateStore snapshotStore = new SnapshotTaskStateStore();
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        manager.setNonPersistStateStore(snapshotStore);
        ITaskRuntime taskRt = manager.newTaskRuntime(FakeStepRt.fakeTask("r408-w06x"), false, null);
        ITaskStepRuntime mainRt = taskRt.newMainStepRuntime();

        TaskStepExecution exec = new TaskStepExecution(null, "errorStep",
                java.util.List.of(), java.util.List.of(), java.util.Set.of(),
                null, null,
                new AbstractTaskStep() {
                    @Override
                    public TaskStepReturn execute(ITaskStepRuntime rt) {
                        throw new AssertionError("r408-error-boundary-probe");
                    }
                },
                null, null, false, null, false);

        AssertionError err = assertThrows(AssertionError.class, () -> exec.executeWithParentRt(mainRt),
                "Error from step body must propagate as-is (never adapted to business failure)");
        assertEquals("r408-error-boundary-probe", err.getMessage());

        String stepPath = "@main/errorStep";
        ITaskStepState latest = snapshotStore.getLatestSnapshot(stepPath);
        assertNotNull(latest, "terminal driver must persist a step state snapshot");
        assertEquals(io.nop.task.core._NopTaskCoreConstants.TASK_STEP_STATUS_FAILED, latest.getStepStatus(),
                "sync-out Error must reach the FAILED terminal driver (pre-fix: driver skipped, stays ACTIVE)");
    }

    // ==================== W-07(a)（红⑦ i）：timeout winner → body seam 取消信号 ====================

    @Test
    public void timeoutWinnerCompletion_cancelsBodySeamToken() throws Exception {
        // 红⑦(i) 行为腿（seam 确定性构造）：body promise 正常完成即 winner 终结 → body seam
        // cancellable 必须收到取消信号——winner 完成后启动的幽灵残段在下个协作检查点停止推进。
        // 修复前正常完成路径不 cancel → 幽灵残段继续运行（红）
        // 生产接线形态（TimeoutTaskStepWrapper :22-24）：body seam token = timeout 的内部
        // cancellable（task lambda 入参）——winner 完成后必须收到取消信号
        AtomicReference<ICancellable> seamRef = new AtomicReference<>();
        AtomicBoolean ghostAborted = new AtomicBoolean(false);
        // body promise 必须真实异步（ASYNC 对已完成 future 退化为同步形态——timeout 走同步
        // 快路径不建 winner，取消腿不可达）
        CompletableFuture<TaskStepReturn> bodyPromise = new CompletableFuture<>();

        TaskStepReturn ret = TaskStepHelper.timeout(10_000, cancellable -> {
            seamRef.set(cancellable);
            // 幽灵残段：body promise 完成后 200ms 到协作检查点检查取消状态
            CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS).execute(() -> {
                if (seamRef.get() != null && seamRef.get().isCancelled())
                    ghostAborted.set(true);
            });
            return TaskStepReturn.ASYNC(null, bodyPromise);
        }, null, GlobalExecutors.globalTimer());

        // winner = body 正常完成路径（非超时）：完成 body promise 触发 winner 终结
        bodyPromise.complete(TaskStepReturn.RETURN_RESULT("body-done"));

        assertEquals("body-done", ret.sync().getResult(),
                "winner = body normal completion, timeout (10s) not fired");
        long deadline = System.currentTimeMillis() + 2000;
        while (!ghostAborted.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(seamRef.get() != null && seamRef.get().isCancelled(),
                "body seam cancellable must be cancelled on ANY winner completion path (W-07(a) MA-14)");
        assertTrue(ghostAborted.get(),
                "ghost body continuation must observe the cancel signal at its cooperative checkpoint "
                        + "(pre-fix: normal-completion path never cancelled the seam)");
    }

    // ==================== W-07(d)（红⑦ iv）：延迟重试撤回 ====================

    @Test
    public void delayedRetryCancelledDuringDelay_bodyNeverExecutes() throws Exception {
        // 红⑦(iv) 行为腿：延迟段接 cancelToken——cancel 触发时延迟段以取消终结、载体上的
        // 步骤体不执行（对齐 DelayTaskStep 正例 bindCancelToken 接线；2498 载体裁定零回退——
        // timer 只触发+载体执行形态不变，本钉只锁取消接线）。修复前延迟段无取消接线 →
        // cancel 后 schedule 体仍完整执行一次副作用（红）
        TaskStepStateBean state = new TaskStepStateBean();
        state.setRetryAttempt(1);
        state.exception(new io.nop.api.core.exceptions.NopException(TEST_PROBE_ERROR));

        Cancellable cancelToken = new Cancellable();
        DelayProbeStepRt stepRt = new DelayProbeStepRt(state, cancelToken);

        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(3);
        policy.setRetryDelay(500);
        policy.setExponentialDelay(false);
        policy.setJitterRatio(0);

        AtomicInteger bodyExecCount = new AtomicInteger();
        Callable<TaskStepReturn> action = () -> {
            bodyExecCount.incrementAndGet();
            return TaskStepReturn.RETURN_RESULT("OK");
        };

        TaskStepReturn ret = TaskStepHelper.retry(null, stepRt, policy, action);
        assertTrue(ret.isAsync(), "delayed retry (retryAttempt=1, delay=500ms) must be async");

        // 延迟窗内取消（500ms delay 的前 1/10 处）
        cancelToken.cancel("test-kill");

        try {
            ret.getReturnPromise().toCompletableFuture().get(2, TimeUnit.SECONDS);
            fail("cancelled delayed retry must terminate exceptionally, not complete the body");
        } catch (Exception expected) {
            // 取消终结（延迟段被 cancel 传导）
        }

        // 给足延迟窗余量：若取消未撤回 schedule 体，body 会在 ~500ms 执行（红）
        Thread.sleep(700);
        assertEquals(0, bodyExecCount.get(),
                "cancelled delayed retry must never execute the body (W-07(d) MA-14; "
                        + "pre-fix the scheduled body ran once after the delay despite cancellation)");
    }

    // ==================== W-09（红⑨）：sleep 运行期上限 ====================

    @Test
    public void sleepOverRuntimeLimit_rejectedWithTypedError() {
        // 运行期上限路线（β）：sleep 求值超 nop.task.step.max-sleep-ms → 运行期 typed 拒绝。
        // 模型 r408-sleep-over-limit（sleep 500ms）+ 动态配置 100（平台测试框架 reset 契约
        // 清洗动态覆盖）。修复前：配置无消费方 → sleep 正常执行完（红）
        io.nop.api.core.config.AppConfig.getConfigProvider()
                .updateConfigValue(io.nop.task.TaskConfigs.CFG_TASK_STEP_MAX_SLEEP_MS, 100);
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        ITask task = manager.getTask("test/r408-sleep-over-limit", 0);
        ITaskRuntime taskRt = manager.newTaskRuntime(task, false, null);

        NopException err = assertThrows(NopException.class,
                () -> task.execute(taskRt).syncGetOutputs(),
                "sleep exceeding max-sleep-ms must be rejected at execution time (W-09 MA-16, route beta)");
        assertEquals(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode(), err.getErrorCode());
        assertTrue(String.valueOf(err.getParam("reason")).contains("max-sleep-ms"),
                "rejection reason must name the runtime-expression limit");
    }

    @Test
    public void sleepNonPositive_keepsLegacyNoOpSemantics() {
        // 负值/零值维持既有 no-op 短路语义（裁定 B：校验仅承载超上界单语义，无额外行为收缩；
        // :40-42 取消显式失败语义零触碰）
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        ITaskRuntime taskRt = manager.newTaskRuntime(FakeStepRt.fakeTask("r408-sleep-legacy"), false, null);
        ITaskStepRuntime rt = taskRt.newMainStepRuntime();

        SleepTaskStep sleepStep = new SleepTaskStep();
        sleepStep.setSleepMillisExpr(scope -> 0L);
        assertEquals(TaskStepReturn.CONTINUE, sleepStep.execute(rt));
        sleepStep.setSleepMillisExpr(scope -> -5L);
        assertEquals(TaskStepReturn.CONTINUE, sleepStep.execute(rt));
    }

    /**
     * 延迟重试撤回钉的最小 ITaskStepRuntime 桩（形态沿 TestTaskStepHelperRetryDelayOffload_C4_001
     * 的 RecordingStepRuntime——新增可控 cancel token 以承载取消传导）。
     */
    static class DelayProbeStepRt implements ITaskStepRuntime {
        private final ITaskStepState state;
        private final Cancellable cancelToken;
        private final IEvalScope scope = XLang.newEvalScope(new java.util.concurrent.ConcurrentHashMap<>());

        DelayProbeStepRt(ITaskStepState state, Cancellable cancelToken) {
            this.state = state;
            this.cancelToken = cancelToken;
        }

        @Override
        public IEvalScope getEvalScope() {
            return scope;
        }

        @Override
        public ITaskRuntime getTaskRuntime() {
            return FakeStepRt.fakeTaskRuntime();
        }

        @Override
        public io.nop.api.core.util.ICancelToken getCancelToken() {
            return cancelToken;
        }

        @Override
        public void setCancelToken(io.nop.api.core.util.ICancelToken cancelToken) {
        }

        @Override
        public Set<String> getOutputNames() {
            return null;
        }

        @Override
        public void setOutputNames(Set<String> outputNames) {
        }

        @Override
        public ITaskStepState getState() {
            return state;
        }

        @Override
        public boolean isSupportPersist() {
            return false;
        }

        @Override
        public void saveState() {
        }

        @Override
        public boolean isRecoverMode() {
            return false;
        }

        @Override
        public ITaskStepRuntime newStepRuntime(String stepName, String stepType,
                                               Set<String> persistVars, boolean useParentScope, boolean concurrent) {
            throw new UnsupportedOperationException("not used by delay-retry probe");
        }

        @Override
        public void addStepCleanup(Runnable cleanup) {
        }

        @Override
        public void runStepCleanups() {
        }

        @Override
        public Throwable getException() {
            return null;
        }

        @Override
        public void setException(Throwable exception) {
        }

        @Override
        public boolean isCancelled() {
            return cancelToken.isCancelled();
        }

        @Override
        public java.util.Set<String> getTagSet() {
            return java.util.Set.of();
        }

        @Override
        public void setTagSet(java.util.Set<String> enabledFlags) {
        }
    }
}
