package io.nop.task.utils;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.state.TaskStepStateBean;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回归测试：{@link TaskStepHelper#retry} 延迟重试路径（retryDelay &gt; 0）每个轮次
 * 只能执行一次 step body（action），不得重复执行业务副作用。
 *
 * <p>缺陷（修复前）：延迟分支 {@code schedule(action, delay)} 在延迟后执行一次 action（run#1，
 * 即本意中的重试执行），随后 {@code thenApply} 回调里又调用一次 {@code action.call()}（run#2）。
 * run#2 的返回值仅用于 isAsync 判断，副作用白做。每次延迟重试轮次 = 2 次 body 执行。
 *
 * <p>计数器断言：首次失败 + 1 个延迟重试轮次 = action 执行恰好 2 次（修复前为 3 次）。
 */
public class TestTaskStepHelperRetryDelayExecutionCount {

    private static final ErrorCode ERR_TEST_RECOVERABLE =
            ErrorCode.define("nop.err.test.retry-delay.recoverable", "test recoverable");

    /**
     * 延迟重试轮次的 action 返回同步结果：每轮只执行一次。
     *
     * <p>修复前失败（executeCount == 3：schedule 内 run#1 + thenApply 回调内 run#2），
     * 修复后通过（executeCount == 2）。
     */
    @Test
    public void delayRetry_syncSuccess_executesActionExactlyOncePerRound() {
        TaskStepStateBean state = new TaskStepStateBean();
        FakeTaskStepRuntime stepRt = new FakeTaskStepRuntime(state);

        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(1);
        policy.setRetryDelay(10);
        policy.setExponentialDelay(false);
        policy.setJitterRatio(0);

        AtomicInteger executeCount = new AtomicInteger();
        Callable<TaskStepReturn> action = () -> {
            if (executeCount.incrementAndGet() == 1)
                throw new NopException(ERR_TEST_RECOVERABLE);
            return TaskStepReturn.RETURN_RESULT("OK");
        };

        TaskStepReturn ret = TaskStepHelper.retry(null, stepRt, policy, action);
        Map<String, Object> outputs = ret.syncGetOutputs();

        assertEquals("OK", outputs.get(TaskConstants.VAR_RESULT),
                "delayed retry round must return the real result");
        assertEquals(2, executeCount.get(),
                "1 initial failure + 1 delayed retry round must execute the step body exactly 2 times. "
                        + "Pre-fix: schedule(action) executed the body once (run#1) and the thenApply callback "
                        + "called action.call() again (run#2) -> 3 executions with duplicated side effects.");
    }

    /**
     * 延迟重试轮次的 action 返回异步结果：每轮只执行一次且结果正确完成。
     */
    @Test
    public void delayRetry_asyncSuccess_executesActionExactlyOncePerRound() {
        TaskStepStateBean state = new TaskStepStateBean();
        FakeTaskStepRuntime stepRt = new FakeTaskStepRuntime(state);

        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(1);
        policy.setRetryDelay(10);
        policy.setExponentialDelay(false);
        policy.setJitterRatio(0);

        AtomicInteger executeCount = new AtomicInteger();
        Callable<TaskStepReturn> action = () -> {
            if (executeCount.incrementAndGet() == 1)
                throw new NopException(ERR_TEST_RECOVERABLE);
            return TaskStepReturn.ASYNC(null, CompletableFuture.supplyAsync(() -> "ASYNC_OK"));
        };

        TaskStepReturn ret = TaskStepHelper.retry(null, stepRt, policy, action);
        Map<String, Object> outputs = ret.syncGetOutputs();

        assertEquals("ASYNC_OK", outputs.get(TaskConstants.VAR_RESULT),
                "delayed retry round must complete with the async result");
        assertEquals(2, executeCount.get(),
                "async delayed retry round must execute the step body exactly once. "
                        + "Pre-fix: 3 executions (duplicate action.call() in thenApply callback).");
    }

    /**
     * 最小 fake {@link ITaskStepRuntime}：仅实现 {@link TaskStepHelper#retry} 延迟路径所需方法。
     * {@code getTaskRuntime()} 返回代理（getScheduledExecutor → global timer），其余方法抛
     * {@link UnsupportedOperationException}（不静默返回）。
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
            // in-memory fake：状态已保存在 state 对象中，无需持久化
        }

        @Override
        public boolean isRecoverMode() {
            return false;
        }

        @Override
        public ITaskStepRuntime newStepRuntime(String stepName, String stepType,
                                               Set<String> persistVars, boolean useParentScope, boolean concurrent) {
            throw new UnsupportedOperationException("not used by TaskStepHelper.retry");
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
            throw new UnsupportedOperationException("not used by TaskStepHelper.retry");
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

    /**
     * 代理 {@link ITaskRuntime}：getScheduledExecutor 返回 global timer（真实延迟调度），
     * 未用到的方法抛异常（No-Silent-No-Op）。
     */
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
                                "not used by TaskStepHelper.retry: " + method.getName());
                }
            }
        };
        return (ITaskRuntime) Proxy.newProxyInstance(
                TestTaskStepHelperRetryDelayExecutionCount.class.getClassLoader(),
                new Class<?>[]{ITaskRuntime.class}, handler);
    }
}
