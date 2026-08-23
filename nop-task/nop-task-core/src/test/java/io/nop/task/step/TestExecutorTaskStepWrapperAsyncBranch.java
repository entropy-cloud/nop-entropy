package io.nop.task.step;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.state.TaskStepStateBean;
import io.nop.xlang.xdsl.action.IActionInputModel;
import io.nop.xlang.xdsl.action.IActionOutputModel;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 回归测试：{@link ExecutorTaskStepWrapper} 的子步骤异步分支（isDone()==false 走
 * {@code result.whenComplete}）成功/失败两个方向的完成语义。
 *
 * <p>缺陷（修复前）：whenComplete 回调成败条件写反——
 * <ul>
 *   <li>子步骤异步<b>成功</b> → {@code ret.completeExceptionally(null)} → JDK CompletableFuture
 *       对 null 异常抛 NPE，NPE 被回调机制吞掉 → ret 永不完成 → 任务永久挂死。</li>
 *   <li>子步骤异步<b>失败</b> → {@code ret.complete(null)} → 异常被吞、以 null 结果当成功继续流转。</li>
 * </ul>
 *
 * <p>修复后：成功 → {@code ret.complete(data)}；失败 → {@code ret.completeExceptionally(err)}。
 */
public class TestExecutorTaskStepWrapperAsyncBranch {

    private static final ErrorCode ERR_TEST_ASYNC_FAIL =
            ErrorCode.define("nop.err.test.executor-async.fail", "test async failure");

    private static final long AWAIT_TIMEOUT_MILLS = 5000;

    @Test
    public void asyncSubStepSuccess_completesWrapperWithResult() {
        CompletableFuture<Object> inner = new CompletableFuture<>();
        ITaskStep subStep = new FakeTaskStep() {
            @Nonnull
            @Override
            public TaskStepReturn execute(ITaskStepRuntime stepRt) {
                // 注册后延迟完成，确保走 whenComplete 异步分支（而非 isDone 同步分支）
                GlobalExecutors.globalTimer().schedule(() -> {
                    inner.complete("ASYNC_OK");
                    return null;
                }, 10, TimeUnit.MILLISECONDS);
                return TaskStepReturn.ASYNC(null, inner);
            }
        };

        ExecutorTaskStepWrapper wrapper = new ExecutorTaskStepWrapper(subStep, "nop-global-timer");
        TaskStepReturn ret = wrapper.execute(new FakeTaskStepRuntime(new TaskStepStateBean()));

        TaskStepReturn done = syncGet(ret);
        assertEquals("ASYNC_OK", done.getOutput(TaskConstants.VAR_RESULT),
                "executor-wrapped async sub-step success must complete the wrapper result with the real value. "
                        + "Pre-fix: completeExceptionally(null) threw NPE inside the whenComplete callback, "
                        + "the NPE was swallowed and the wrapper future never completed (task hangs forever).");
    }

    @Test
    public void asyncSubStepFailure_propagatesException() {
        CompletableFuture<Object> inner = new CompletableFuture<>();
        ITaskStep subStep = new FakeTaskStep() {
            @Nonnull
            @Override
            public TaskStepReturn execute(ITaskStepRuntime stepRt) {
                GlobalExecutors.globalTimer().schedule(() -> {
                    inner.completeExceptionally(new NopException(ERR_TEST_ASYNC_FAIL));
                    return null;
                }, 10, TimeUnit.MILLISECONDS);
                return TaskStepReturn.ASYNC(null, inner);
            }
        };

        ExecutorTaskStepWrapper wrapper = new ExecutorTaskStepWrapper(subStep, "nop-global-timer");
        TaskStepReturn ret = wrapper.execute(new FakeTaskStepRuntime(new TaskStepStateBean()));

        try {
            TaskStepReturn done = syncGet(ret);
            fail("executor-wrapped async sub-step failure must propagate the exception, not complete. "
                    + "Got result: " + done.getOutputs());
        } catch (Exception e) {
            Throwable cause = unwrapCause(e);
            assertTrue(cause instanceof NopException,
                    "propagated failure must be the original NopException, got: " + cause);
            assertEquals(ERR_TEST_ASYNC_FAIL.getErrorCode(), ((NopException) cause).getErrorCode(),
                    "executor-wrapped async sub-step failure must surface the original error code. "
                            + "Pre-fix: ret.complete(null) swallowed the failure and returned null as success.");
        }
    }

    private static TaskStepReturn syncGet(TaskStepReturn ret) {
        try {
            return ret.getReturnPromise().toCompletableFuture().get(AWAIT_TIMEOUT_MILLS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("wrapper result did not complete within timeout", e);
        }
    }

    private static Throwable unwrapCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause)
            cause = cause.getCause();
        return cause;
    }

    /**
     * 最小 fake {@link ITaskStep}：子步骤返回未完成的异步结果。
     */
    static class FakeTaskStep implements ITaskStep {
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
        public List<IActionInputModel> getInputs() {
            return Collections.emptyList();
        }

        @Override
        public List<IActionOutputModel> getOutputs() {
            return Collections.emptyList();
        }

        @Override
        public SourceLocation getLocation() {
            return null;
        }

        @Nonnull
        @Override
        public TaskStepReturn execute(ITaskStepRuntime stepRt) {
            throw new UnsupportedOperationException("subclasses must implement execute");
        }
    }

    /**
     * 最小 fake {@link ITaskStepRuntime}：getTaskRuntime() 返回代理（getThreadPoolExecutor →
     * global timer），其余未用到的方法抛 {@link UnsupportedOperationException}。
     */
    static class FakeTaskStepRuntime implements ITaskStepRuntime {
        private final ITaskStepState state;
        private final ITaskRuntime taskRt = newFakeTaskRuntime();

        FakeTaskStepRuntime(ITaskStepState state) {
            this.state = state;
        }

        @Override
        public ITaskRuntime getTaskRuntime() {
            return taskRt;
        }

        @Override
        public ICancelToken getCancelToken() {
            return null;
        }

        @Override
        public void setCancelToken(ICancelToken cancelToken) {
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
            throw new UnsupportedOperationException("not used by ExecutorTaskStepWrapper test");
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
        public IEvalScope getEvalScope() {
            throw new UnsupportedOperationException("not used by ExecutorTaskStepWrapper test");
        }

        @Nonnull
        @Override
        public Set<String> getTagSet() {
            return Collections.emptySet();
        }

        @Override
        public void setTagSet(Set<String> enabledFlags) {
        }
    }

    static ITaskRuntime newFakeTaskRuntime() {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "getScheduledExecutor":
                    case "getThreadPoolExecutor":
                        return GlobalExecutors.globalTimer();
                    case "isRecoverMode":
                        return Boolean.FALSE;
                    case "toString":
                        return "FakeTaskRuntime";
                    default:
                        throw new UnsupportedOperationException(
                                "not used by ExecutorTaskStepWrapper test: " + method.getName());
                }
            }
        };
        return (ITaskRuntime) Proxy.newProxyInstance(
                TestExecutorTaskStepWrapperAsyncBranch.class.getClassLoader(),
                new Class<?>[]{ITaskRuntime.class}, handler);
    }
}
