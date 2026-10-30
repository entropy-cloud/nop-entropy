package io.nop.task.step;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConfigs;
import io.nop.task.TaskErrors;
import io.nop.task.TaskStepReturn;
import io.nop.task.builder.ITaskStepBuilder;
import io.nop.task.builder.TaskStepEnhancer;
import io.nop.task.model.SimpleTaskStepModel;
import io.nop.task.state.TaskStepStateBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.nop.task.TaskErrors.ERR_TASK_STEP_CONFIG_INVALID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2514 MA-03（1.5-C3-002，裁定③）：sync × executor 同池嵌套死锁解除。
 *
 * <p>[C10-006] pool-size=1 小池死锁回归：sync（外层，调用线程阻塞等待）× executor（内层，池上执行）
 * 同池嵌套时池饱和即全体永久互等——修复面 = 构建期 fail-fast（ERR_TASK_STEP_CONFIG_INVALID 族）
 * + syncGet 有界等待（取消唤醒 + 时限，闭合 C2-010 通用挂死面）。
 *
 * <p>缺陷态（修复前）：组合装配通过、syncGet 无超时——互等永不解除（红③以 guard 超时触发）；
 * 修复后：装配期响亮失败、等待有界（绿）。
 */
public class TestSyncExecutorNesting {

    private static final ErrorCode ERR_TEST_NESTING_FAIL =
            ErrorCode.define("nop.err.test.sync-nesting.fail", "sync nesting test failure");

    // ==================== [C10-006] 构建 fail-fast：sync × executor 组合装配期拒绝 ====================

    @Test
    public void c10_006_syncExecutorCombo_buildTimeFailFast() {
        SimpleTaskStepModel stepModel = new SimpleTaskStepModel();
        stepModel.setName("nested");
        stepModel.setSync(true);
        stepModel.setExecutor("pool-size-1");

        TaskStepEnhancer enhancer = new TaskStepEnhancer();
        ITaskStepBuilder stepBuilder = new ITaskStepBuilder() {
            @Override
            public AbstractTaskStep buildRawStep(io.nop.task.model.TaskStepModel model) {
                // 最小 no-op 步骤体：校验链位于 wrap()（buildRawStep 之后），桩不得先抛，
                // 否则 fail-fast 校验不可达（与 timeout/rateLimit 硬失败同码同位）
                return new AbstractTaskStep() {
                    @Override
                    public String getStepType() {
                        return "stub";
                    }

                    @Override
                    public TaskStepReturn execute(ITaskStepRuntime rt) {
                        return TaskStepReturn.CONTINUE;
                    }
                };
            }

            @Override
            public ITaskStep buildDecoratedStep(io.nop.task.model.TaskStepModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public io.nop.task.ITaskStepExecution buildStepExecution(io.nop.task.model.TaskStepModel model) {
                throw new UnsupportedOperationException();
            }
        };

        NopException ex = assertThrows(NopException.class,
                () -> enhancer.buildDecorated(stepModel, stepBuilder),
                "sync x executor combo must fail fast at assembly time (MA-03, ERR_TASK_STEP_CONFIG_INVALID family)");
        assertTrue(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode().equals(ex.getErrorCode()),
                "rejection must use ERR_TASK_STEP_CONFIG_INVALID family, got: " + ex.getErrorCode());
    }

    // ==================== 取消唤醒：阻塞等待被 cancel token 唤醒 ====================

    @Test
    public void c3_002_syncWrapper_cancelWakesBlockedWait() throws Exception {
        ICancelToken cancelToken = new Cancellable();
        ITaskStepRuntime stepRt = stepRtWithCancelToken(cancelToken);

        java.util.concurrent.CompletableFuture<Object> never = new CompletableFuture<>();
        AbstractTaskStep asyncStep = new AbstractTaskStep() {
            @Override
            public String getStepType() {
                return "async";
            }

            @Override
            public TaskStepReturn execute(ITaskStepRuntime rt) {
                return TaskStepReturn.ASYNC(null, never);
            }
        };
        SyncTaskStepWrapper wrapper = new SyncTaskStepWrapper(asyncStep);

        ExecutorService guard = Executors.newSingleThreadExecutor();
        try {
            Future<?> f = guard.submit(() -> {
                NopException ex = assertThrows(NopException.class, () -> wrapper.execute(stepRt),
                        "blocked sync wait must be woken by cancel (MA-03 bounded wait)");
                assertTrue(TaskErrors.ERR_TASK_CANCELLED.getErrorCode().equals(ex.getErrorCode()),
                        "cancel wake must carry cancellation error, got: " + ex.getErrorCode());
                return null;
            });
            // 给等待线程进入阻塞的时间，再触发取消
            Thread.sleep(200);
            ((Cancellable) cancelToken).cancel("test-kill");
            f.get(5, TimeUnit.SECONDS); // 修复前永久挂死 → 本行 guard 超时红
        } finally {
            guard.shutdownNow();
        }
    }

    // ==================== 时限有界：永不完成的异步返回在时限内显式失败 ====================

    @Test
    public void c3_002_syncWrapper_waitTimeout_boundedFailure() throws Exception {
        var cfgProvider = AppConfig.getConfigProvider();
        cfgProvider.updateConfigValue(TaskConfigs.CFG_TASK_STEP_SYNC_WAIT_TIMEOUT_MS, 200);
        try {
            ITaskStepRuntime stepRt = stepRtWithCancelToken(null);
            AbstractTaskStep asyncStep = new AbstractTaskStep() {
                @Override
                public String getStepType() {
                    return "async";
                }

                @Override
                public TaskStepReturn execute(ITaskStepRuntime rt) {
                    return TaskStepReturn.ASYNC(null, new CompletableFuture<Object>());
                }
            };
            SyncTaskStepWrapper wrapper = new SyncTaskStepWrapper(asyncStep);

            NopException ex = assertThrows(NopException.class, () -> wrapper.execute(stepRt),
                    "sync wait must fail bounded instead of hanging forever (C2-010 closed)");
            assertTrue(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode().equals(ex.getErrorCode()),
                    "timeout failure must use ERR_TASK_STEP_CONFIG_INVALID family, got: " + ex.getErrorCode());
        } finally {
            cfgProvider.updateConfigValue(TaskConfigs.CFG_TASK_STEP_SYNC_WAIT_TIMEOUT_MS, 600000);
        }
    }

    // ==================== [C10-006] pool-size=1 同池嵌套：有界失败而非永久互等 ====================

    @Test
    public void c10_006_poolSize1_syncExecutorNesting_boundedInsteadOfDeadlock() throws Exception {
        var cfgProvider = AppConfig.getConfigProvider();
        cfgProvider.updateConfigValue(TaskConfigs.CFG_TASK_STEP_SYNC_WAIT_TIMEOUT_MS, 500);
        ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();
        IThreadPoolExecutor pool = (IThreadPoolExecutor) Proxy.newProxyInstance(
                IThreadPoolExecutor.class.getClassLoader(), new Class<?>[]{IThreadPoolExecutor.class},
                (p, method, args) -> {
                    switch (method.getName()) {
                        case "submit":
                            // submit(Runnable, result) / submit(Callable)
                            if (args[0] instanceof Callable) {
                                return CompletableFuture.supplyAsync(() -> {
                                    try {
                                        return ((Callable<?>) args[0]).call();
                                    } catch (Exception e) {
                                        // test stub: rethrow callable failure to the future's awaiting thread
                                        throw new io.nop.core.exceptions.NopException(e.getMessage() == null ? "callable failed" : e.getMessage(), e);
                                    }
                                }, singleThreadPool);
                            }
                            CompletableFuture<Object> ret = new CompletableFuture<>();
                            CompletableFuture.runAsync(() -> {
                                try {
                                    ((Runnable) args[0]).run();
                                } finally {
                                    ret.complete(args[1]);
                                }
                            }, singleThreadPool);
                            return ret;
                        case "destroy":
                            return null;
                        default:
                            return null;
                    }
                });
        try {
            AbstractTaskStep body = new AbstractTaskStep() {
                @Override
                public String getStepType() {
                    return "noop";
                }

                @Override
                public TaskStepReturn execute(ITaskStepRuntime rt) {
                    return TaskStepReturn.CONTINUE;
                }
            };
            ExecutorTaskStepWrapper executorWrap = new ExecutorTaskStepWrapper(body, "pool-size-1");
            SyncTaskStepWrapper syncWrap = new SyncTaskStepWrapper(executorWrap);

            ITaskStepRuntime stepRt = stepRtWithExecutorAndCancelToken(pool, null);

            // plan 2514 C10-006（修正执行位）：把 syncWrap.execute 提交到同一个 pool-size=1 池线程上
            // 执行——池线程自身阻塞等待自己派生的同池子任务（真实同池嵌套死锁形态）；池被占满，
            // 子任务永不执行，future 永不完成。修复后有界等待在时限内显式失败（非永久互等）。
            Future<?> f = singleThreadPool.submit(() -> {
                assertThrows(NopException.class, () -> syncWrap.execute(stepRt),
                        "pool-size=1 same-pool nesting must fail bounded, not deadlock forever (C10-006)");
                return null;
            });
            f.get(30, TimeUnit.SECONDS); // 缺陷态：互等永不解除 → 本行 guard 超时红
        } finally {
            singleThreadPool.shutdownNow();
            cfgProvider.updateConfigValue(TaskConfigs.CFG_TASK_STEP_SYNC_WAIT_TIMEOUT_MS, 600000);
        }
    }

    // ==================== 测试桩 ====================

    private static ITaskStepRuntime stepRtWithCancelToken(ICancelToken cancelToken) {
        return stepRtWithExecutorAndCancelToken(null, cancelToken);
    }

    private static ITaskStepRuntime stepRtWithExecutorAndCancelToken(IThreadPoolExecutor executor,
                                                                     ICancelToken cancelToken) {
        TaskStepStateBean state = new TaskStepStateBean();
        state.setStepPath("/test/nested");
        return (ITaskStepRuntime) Proxy.newProxyInstance(ITaskStepRuntime.class.getClassLoader(),
                new Class<?>[]{ITaskStepRuntime.class}, (p, method, args) -> {
                    switch (method.getName()) {
                        case "getTaskRuntime":
                            return taskRtWithExecutor(executor);
                        case "getCancelToken":
                            return cancelToken;
                        case "getState":
                            return state;
                        case "getStepPath":
                            return "/test/nested";
                        default:
                            return defaultReturn(method.getReturnType());
                    }
                });
    }

    private static io.nop.task.ITaskRuntime taskRtWithExecutor(IThreadPoolExecutor executor) {
        return (io.nop.task.ITaskRuntime) Proxy.newProxyInstance(io.nop.task.ITaskRuntime.class.getClassLoader(),
                new Class<?>[]{io.nop.task.ITaskRuntime.class}, (p, method, args) -> {
                    switch (method.getName()) {
                        case "getThreadPoolExecutor":
                            return executor;
                        case "getTaskName":
                            return "test-task";
                        default:
                            return defaultReturn(method.getReturnType());
                    }
                });
    }

    private static Object defaultReturn(Class<?> type) {
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        return null;
    }
}
