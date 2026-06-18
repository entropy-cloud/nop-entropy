package io.nop.task.utils;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.task.TaskErrors.ERR_TASK_RETRY_TIMES_EXCEED_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 248 Phase 2 focused 测试：验证 {@link TaskStepHelper#retry} 同步成功路径的 return 修复。
 *
 * <p>缺陷（修复前）：{@code action.call()} 返回同步（非 async）成功结果时，控制流跳过 async 分支后没有 return，
 * 直接落入 {@code setRetryAttempt(retryAttempt + 1)} + {@code while(true)} 循环回顶，导致成功的 step 被反复重新执行
 * 至 retryCount 耗尽（或 retry policy 终止）。
 *
 * <p>修复后：async 分支之后新增 {@code return result;}，使同步成功结果在首轮即返回，action 执行恰好一次。
 *
 * <p>本测试直接调用 {@link TaskStepHelper#retry}（不经 .task.xml / xpl 包装），用计数器断言 action 执行次数：
 * <ul>
 *   <li>{@code syncSuccess_executesExactlyOnce}：修复前失败（executeCount > 1 且最终抛 retry-times-exceed-limit），
 *       修复后通过（executeCount == 1，返回成功结果）。</li>
 *   <li>{@code recoverableFailure_retriesUntilExhausted_unchanged}：失败重试路径回归（plan 246/247 行为不变），
 *       action 抛可恢复异常 → 按 maxRetryCount 重试（1 + maxRetryCount 次执行）→ honest throw。</li>
 * </ul>
 */
public class TestTaskStepHelperRetrySyncReturn {

    private static final ErrorCode ERR_TEST_RECOVERABLE =
            ErrorCode.define("nop.err.test.retry-sync.recoverable", "test recoverable");

    /**
     * 核心回归测试：retry-wrapped 同步成功 step 执行恰好一次。
     *
     * <p>修复前：sync 成功不 return → 循环重执行至 retryCount 耗尽 → executeCount == 1 + maxRetryCount == 3，
     * 且最终抛 {@code ERR_TASK_RETRY_TIMES_EXCEED_LIMIT}。此测试在修复前失败。
     * <p>修复后：首轮 sync 成功即 return → executeCount == 1，返回成功结果。
     */
    @Test
    public void syncSuccess_executesExactlyOnce() {
        TaskStepStateBean state = new TaskStepStateBean();
        FakeTaskStepRuntime stepRt = new FakeTaskStepRuntime(state);

        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(2);
        policy.setRetryDelay(0);

        AtomicInteger executeCount = new AtomicInteger(0);
        Callable<TaskStepReturn> action = () -> {
            executeCount.incrementAndGet();
            return TaskStepReturn.RETURN_RESULT("OK");
        };

        TaskStepReturn ret = TaskStepHelper.retry(null, stepRt, policy, action);

        assertEquals(1, executeCount.get(),
                "sync success must return on first execution (not re-execute until retry exhausted). "
                        + "Pre-fix this was 1 + maxRetryCount = 3 and ultimately threw ERR_TASK_RETRY_TIMES_EXCEED_LIMIT.");
        assertEquals("OK", ret.getOutput(TaskConstants.VAR_RESULT),
                "sync success must return the real result, not be swallowed");
    }

    /**
     * 失败重试路径回归：action 抛可恢复异常 → 按 maxRetryCount 真实重试 → 耗尽 honest throw。
     *
     * <p>证明本修复（仅补 sync 成功 return）未改变失败重试语义（plan 246/247 已验证）。
     * maxRetryCount=2 → 共执行 1 + 2 = 3 次。
     */
    @Test
    public void recoverableFailure_retriesUntilExhausted_unchanged() {
        TaskStepStateBean state = new TaskStepStateBean();
        FakeTaskStepRuntime stepRt = new FakeTaskStepRuntime(state);

        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(2);
        policy.setRetryDelay(0);

        AtomicInteger executeCount = new AtomicInteger(0);
        Callable<TaskStepReturn> action = () -> {
            executeCount.incrementAndGet();
            throw new NopException(ERR_TEST_RECOVERABLE);
        };

        try {
            TaskStepHelper.retry(null, stepRt, policy, action);
            fail("recoverable failure should honest-throw after retry exhausted");
        } catch (NopException e) {
            // plan 247: state.fail() saved the exception → state.exception() non-null → thrown is the saved one,
            // not the generic ERR_TASK_RETRY_TIMES_EXCEED_LIMIT fallback (which only fires when exception is null)
            assertNotNull(e, "retry exhaustion must propagate exception honestly");
            assertNotEquals(ERR_TASK_RETRY_TIMES_EXCEED_LIMIT.getErrorCode(), e.getErrorCode(),
                    "after plan 247 fix, thrown exception is the saved recoverable one, not the generic fallback");
            assertEquals(ERR_TEST_RECOVERABLE.getErrorCode(), e.getErrorCode(),
                    "thrown exception must be the recoverable exception saved by fail()");
        }

        assertEquals(3, executeCount.get(),
                "recoverable failure with maxRetryCount=2 must execute 1 + 2 = 3 times (retry path unchanged)");
    }

    /**
     * 最小 fake {@link ITaskStepRuntime}：仅实现 {@link TaskStepHelper#retry} 同步循环路径所需方法。
     * 其余方法抛 {@link UnsupportedOperationException}（遵循 No-Silent-No-Op 规则，不静默返回）。
     */
    static class FakeTaskStepRuntime implements ITaskStepRuntime {
        private final ITaskStepState state;

        FakeTaskStepRuntime(ITaskStepState state) {
            this.state = state;
        }

        @Override
        public ITaskRuntime getTaskRuntime() {
            // TaskStepStateBean.fail(e, taskRt) 忽略 taskRt；retryDelay=0 路径不进延迟调度，故 null 安全
            return null;
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
}
