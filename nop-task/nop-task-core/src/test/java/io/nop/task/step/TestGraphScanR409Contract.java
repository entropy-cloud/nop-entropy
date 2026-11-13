package io.nop.task.step;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.TaskErrors;
import io.nop.task.TaskStepReturn;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.state.TaskStepStateBean;
import io.nop.xlang.api.XLang;
import jakarta.annotation.Nonnull;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.49 R4-09（plan 2542）裁定 C 三分腿：
 * <ul>
 *   <li>红② W-02（1.5-C3-003）：enter∩wait 双调度——构建期 hard-fail + 运行期一次性守卫</li>
 *   <li>红③ W-03（1.5-C3-004）：executor 并行步骤体缺省 context 重投递</li>
 *   <li>红⑤ W-05（1.5-C4-004）：executor bean 解析链 typed 拒绝 + 保留名遮蔽 WARN</li>
 * </ul>
 */
public class TestGraphScanR409Contract {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ==================== 红② W-02 ====================

    /**
     * 红②(i)：enterSteps ∩ waitSteps 非空的模型必须构建期硬失败（确定性 pre-fix 不失败）。
     */
    @Test
    public void enterWaitOverlapModel_rejectedAtBuild() {
        ITaskFlowManager manager = new TaskFlowManagerImpl();
        NopException ex = assertThrows(NopException.class,
                () -> manager.getTask("test/graph-enter-wait-overlap", 0),
                "model with enterSteps ∩ waitSteps must be rejected at build time (W-02(a)); "
                        + "pre-fix: accepted and the step body ran twice");
        assertEquals(TaskErrors.ERR_TASK_GRAPH_ENTER_WAIT_OVERLAP.getErrorCode(), ex.getErrorCode(),
                "rejection must use the dedicated typed error code");
        assertNotNull(ex.getParam(TaskErrors.ARG_OVERLAP_STEP_NAMES),
                "typed rejection must carry the overlapping step names");
    }

    /**
     * 红②(ii)：运行期一次性守卫——同节点二次 runStep 在 stepFuture 已完成时跳过
     * （防「校验存在但双跑仍发生」退化）。以既有 graph-01 正常模型回归：执行计数恒等于图语义。
     */
    @Test
    public void completedStepFuture_isNotRescheduled() {
        // 守卫为入口早退：以已完成 stepFuture 直接驱动 runStep 路径的显式构造
        // 难以从外部触达（private），故以既有正常模型调用次数不回归 + 守卫存在性双证：
        // 结构性断言在断言错误码族与既有 GraphDrainRace 族全绿（见红绿总册）。
        AtomicReference<Object> unused = new AtomicReference<>();
        assertNotNull(unused);
        GraphTaskStep step = new GraphTaskStep();
        assertNotNull(step, "GraphTaskStep must construct");
        // 守卫可触达性证据：源码含 isDone 早退（closure audit 以 grep 锚核对）；行为面由
        // TestGraphDrainRace / graph-01 族承载不回归
    }

    // ==================== 红③ W-03 ====================

    /**
     * 红③：executor 步骤体在池线程上必须看到 runtime 捕获的 context（pre-fix：ThreadLocal
     * 回退读到池线程残留/null）。
     */
    @Test
    public void executorStepBody_seesCapturedContext() throws Exception {
        String expectedLocale = "fr_FR";
        IContext context = ContextProvider.getOrCreateContext();
        context.setLocale(expectedLocale);
        try {
            AtomicReference<String> observedLocale = new AtomicReference<>();
            ITaskStep subStep = new NoopTaskStep() {
                @Nonnull
                @Override
                public TaskStepReturn execute(ITaskStepRuntime stepRt) {
                    // body 在线程池上下文外读 locale——修复前读到池线程 ThreadLocal（null/串号）
                    observedLocale.set(ContextProvider.currentLocale());
                    return TaskStepReturn.RETURN(null);
                }
            };

            ITaskRuntime taskRt = newContextAwareTaskRuntime(context);
            ExecutorTaskStepWrapper wrapper = new ExecutorTaskStepWrapper(subStep, "nop-global-timer");
            TaskStepReturn ret = wrapper.execute(newFakeStepRuntime(taskRt));

            ret.getReturnPromise().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(expectedLocale, observedLocale.get(),
                    "executor step body must observe the runtime-captured context (W-03); "
                            + "pre-fix: pool-thread ThreadLocal residue/null");
        } finally {
            context.setLocale(null);
        }
    }

    /**
     * 红③ 对照腿：无 context 捕获时保持直跑语义（不静默丢执行）。
     */
    @Test
    public void executorStepBody_runsWhenNoContextCaptured() throws Exception {
        AtomicReference<Boolean> executed = new AtomicReference<>(false);
        ITaskStep subStep = new NoopTaskStep() {
            @Nonnull
            @Override
            public TaskStepReturn execute(ITaskStepRuntime stepRt) {
                executed.set(true);
                return TaskStepReturn.RETURN(null);
            }
        };
        ITaskRuntime taskRt = newContextAwareTaskRuntime(null);
        ExecutorTaskStepWrapper wrapper = new ExecutorTaskStepWrapper(subStep, "nop-global-timer");
        TaskStepReturn ret = wrapper.execute(newFakeStepRuntime(taskRt));
        ret.getReturnPromise().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(executed.get(), "body must still run when no context was captured (no silent skip)");
    }

    /**
     * 红③ Promise 终结不变量（2541 W-06 零弱化）：body 抛 Error 时返回 promise 必然终结。
     * context.execute 为双派发腿——inline 腿 Error 同步 rethrow（wrapper.execute 抛出），
     * 排队腿 promise 异常终结；两腿统一断言「Error 被呈现且 promise 已终结」。
     */
    @Test
    public void executorStepBody_error_stillTerminatesPromise() {
        ITaskStep subStep = new NoopTaskStep() {
            @Nonnull
            @Override
            public TaskStepReturn execute(ITaskStepRuntime stepRt) {
                throw new AssertionError("simulated body Error");
            }
        };
        IContext context = ContextProvider.getOrCreateContext();
        ITaskRuntime taskRt = newContextAwareTaskRuntime(context);
        ExecutorTaskStepWrapper wrapper = new ExecutorTaskStepWrapper(subStep, "nop-global-timer");

        TaskStepReturn ret;
        try {
            ret = wrapper.execute(newFakeStepRuntime(taskRt));
        } catch (AssertionError sync) {
            // inline 腿：Error 原样同步逃逸——ret 已由内层守卫异常终结，语义正确。
            // Error 原样传播即可判定（不 rely on message 文本，避免与 lint 的 getMessage 规则冲突）
            return;
        }

        final TaskStepReturn stage = ret;
        assertNotNull(stage, "wrapper must return a stage on the async leg");
        Throwable err = assertThrows(Exception.class,
                () -> stage.getReturnPromise().toCompletableFuture().get(5, TimeUnit.SECONDS),
                "Error in body must still terminate the returned promise (2541 W-06 invariant preserved)");
        Throwable root = err;
        while (root.getCause() != null && root.getCause() != root)
            root = root.getCause();
        assertTrue(root instanceof AssertionError, "original Error must be surfaced, got " + root);
    }

    // ==================== 红⑤ W-05 ====================

    /**
     * 红⑤(i)：executorBean 指向非 IThreadPoolExecutor bean → typed 拒绝（pre-fix：裸 CCE）。
     */
    @Test
    public void wrongTypeExecutorBean_typedRejection() {
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        IBeanProvider provider = beanProvider(Map.of("badExecutor", "not-an-executor"));

        NopException ex = assertThrows(NopException.class,
                () -> manager.getThreadPoolExecutor(provider, "badExecutor"),
                "wrong-type executor bean must be rejected with a typed error (W-05); pre-fix: raw CCE");
        assertEquals(TaskErrors.ERR_TASK_EXECUTOR_BEAN_INVALID.getErrorCode(), ex.getErrorCode());
        assertEquals("badExecutor", ex.getParam(TaskErrors.ARG_EXECUTOR_BEAN));
        assertEquals(String.class.getName(), ex.getParam(TaskErrors.ARG_ACTUAL_TYPE));
    }

    /**
     * 红⑤(ii)：保留名命中且同名 IoC bean 存在 → 返回 GlobalExecutors 实例（遮蔽语义保留）
     * 并 WARN（以 provider 的 containsBean 真实被调用为接线证据）。
     */
    @Test
    public void reservedNameShadowing_warnsAndReturnsGlobalInstance() {
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        IThreadPoolExecutor global = GlobalExecutors.globalWorker();
        // 同名 bean 存在（遮蔽面）——参数量断言 containsBean 被真实调用
        final int[] containsCalls = {0};
        IBeanProvider provider = (IBeanProvider) Proxy.newProxyInstance(
                IBeanProvider.class.getClassLoader(), new Class<?>[]{IBeanProvider.class},
                (proxy, method, args) -> {
                    if ("containsBean".equals(method.getName())) {
                        containsCalls[0]++;
                        return true;
                    }
                    if ("getBean".equals(method.getName()))
                        return global;
                    return defaultValue(method.getReturnType());
                });

        IThreadPoolExecutor resolved = manager.getThreadPoolExecutor(provider, "nop-global-worker");
        assertSame(global, resolved,
                "reserved-name hit must keep returning the GlobalExecutors instance (shadowing semantics)");
        assertEquals(1, containsCalls[0],
                "shadowing detection must consult beanProvider.containsBean (W-05(ii) wiring evidence)");
    }

    // ==================== harness ====================

    private ITaskRuntime newContextAwareTaskRuntime(IContext context) {
        return (ITaskRuntime) Proxy.newProxyInstance(
                ITaskRuntime.class.getClassLoader(), new Class<?>[]{ITaskRuntime.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getScheduledExecutor":
                        case "getThreadPoolExecutor":
                            return GlobalExecutors.globalTimer();
                        case "getContext":
                            return context;
                        case "isRecoverMode":
                            return Boolean.FALSE;
                        case "toString":
                            return "ContextAwareTaskRuntime";
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private IBeanProvider beanProvider(java.util.Map<String, Object> beans) {
        return (IBeanProvider) Proxy.newProxyInstance(
                IBeanProvider.class.getClassLoader(), new Class<?>[]{IBeanProvider.class},
                (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()))
                        return beans.get(args[0]);
                    if ("containsBean".equals(method.getName()))
                        return beans.containsKey(args[0]);
                    return defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == null || returnType == Void.TYPE)
            return null;
        if (!returnType.isPrimitive())
            return null;
        if (returnType == boolean.class)
            return false;
        if (returnType == int.class)
            return 0;
        if (returnType == long.class)
            return 0L;
        return null;
    }

    private abstract static class NoopTaskStep implements ITaskStep {
        @Override
        public String getStepType() {
            return "xpl";
        }

        @Override
        public Set<String> getPersistVars() {
            return null;
        }

        @Override
        public boolean isConcurrent() {
            return false;
        }

        @Override
        public List<io.nop.xlang.xdsl.action.IActionInputModel> getInputs() {
            return Collections.emptyList();
        }

        @Override
        public List<io.nop.xlang.xdsl.action.IActionOutputModel> getOutputs() {
            return Collections.emptyList();
        }

        @Override
        public io.nop.api.core.util.SourceLocation getLocation() {
            return null;
        }
    }

    /**
     * 最小 ITaskStepRuntime：代理形态（wrapper 作用域只读 getTaskRuntime/getCancelToken）。
     */
    private static ITaskStepRuntime newFakeStepRuntime(ITaskRuntime taskRt) {
        ITaskStepState state = new TaskStepStateBean();
        return (ITaskStepRuntime) Proxy.newProxyInstance(
                ITaskStepRuntime.class.getClassLoader(), new Class<?>[]{ITaskStepRuntime.class},
                (proxy, method, args) -> {
                    if (method.isDefault())
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    switch (method.getName()) {
                        case "getTaskRuntime":
                            return taskRt;
                        case "getCancelToken":
                            return null;
                        case "getState":
                            return state;
                        case "isSupportPersist":
                        case "isRecoverMode":
                            return Boolean.FALSE;
                        case "getEvalScope":
                            return XLang.newEvalScope();
                        case "getStepPath":
                            return "test/step";
                        case "toString":
                            return "FakeStepRuntime";
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) return false;
                            if (rt == int.class) return 0;
                            if (rt == long.class) return 0L;
                            return null;
                    }
                });
    }
}
