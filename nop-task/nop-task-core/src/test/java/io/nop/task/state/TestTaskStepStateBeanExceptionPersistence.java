package io.nop.task.state;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.task.ITaskStepRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 247 Phase 2 focused 测试：验证 {@link TaskStepStateBean} 的 exception 持久化，
 * 以及由此解锁的 {@link RetryPolicy} 异常分类联动。
 *
 * <p>Baseline（修复前）：{@code exception(Throwable)} 与 {@code fail(Throwable, ITaskRuntime)} 均为
 * 空 method body，{@code exception()} getter 永远返回 null。这导致 {@link RetryPolicy#getRetryDelay}
 * 接收 null exception，跳过 {@code isRecoverableException} 判定，所有异常都按 retryCount 无条件重试至耗尽。
 *
 * <p>修复后：{@code exception(Throwable)} 设置 {@code this.exception = exp}；{@code fail(Throwable, ITaskRuntime)}
 * 委托 {@code exception(exp)}。{@code exception()} 返回最近一次保存的异常引用，使 {@link RetryPolicy} 能据此分类。
 *
 * <p>本测试在 Java 层直接验证分类联动（不经 xpl 函数调用包装——xpl 的
 * {@code AbstractObjFunctionExecutable.doInvoke*} 会把被调用方法抛出的异常包装为
 * {@code NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)}，丢失 bizFatal 标记，这是 xpl 既有行为，
 * 非本计划 scope）。
 */
public class TestTaskStepStateBeanExceptionPersistence {

    private static final ErrorCode ERR_TEST_SAMPLE =
            ErrorCode.define("nop.err.test.state.sample", "test sample");
    private static final ErrorCode ERR_TEST_FIRST =
            ErrorCode.define("nop.err.test.state.first", "test first");
    private static final ErrorCode ERR_TEST_SECOND =
            ErrorCode.define("nop.err.test.state.second", "test second");
    private static final ErrorCode ERR_TEST_FAIL_BIZ =
            ErrorCode.define("nop.err.test.state.fail-biz", "test fail-biz");
    private static final ErrorCode ERR_TEST_BIZ_FATAL =
            ErrorCode.define("nop.err.test.state.biz-fatal", "test bizFatal unrecoverable");
    private static final ErrorCode ERR_TEST_RECOVERABLE =
            ErrorCode.define("nop.err.test.state.recoverable", "test recoverable");

    // ==================== exception 持久化 ====================

    @Test
    public void exceptionSetter_assignsField() {
        TaskStepStateBean state = new TaskStepStateBean();
        assertNull(state.exception(), "fresh state should have null exception");

        NopException exp = new NopException(ERR_TEST_SAMPLE);
        state.exception(exp);

        assertSame(exp, state.exception(),
                "exception(Throwable) must save the exact reference for exception() to return");
    }

    @Test
    public void fail_savesExceptionAndGetterReturnsIt() {
        TaskStepStateBean state = new TaskStepStateBean();
        assertNull(state.exception(), "fresh state should have null exception");

        NopException exp = new NopException(ERR_TEST_FAIL_BIZ).bizFatal(true);
        // fail(Throwable, ITaskRuntime) 委托 exception(exp)，taskRt 在本测试中不被读取，可传 null
        state.fail(exp, null);

        assertSame(exp, state.exception(),
                "fail(e, taskRt) must save e so exception() returns the same reference");
    }

    @Test
    public void fail_overwritesPreviousException() {
        // 多次失败（retry 循环场景）：每次 fail 都应覆盖前一次异常，保证 state.exception() 反映最近一次失败
        TaskStepStateBean state = new TaskStepStateBean();

        NopException first = new NopException(ERR_TEST_FIRST);
        state.fail(first, null);
        assertSame(first, state.exception());

        NopException second = new NopException(ERR_TEST_SECOND);
        state.fail(second, null);
        assertSame(second, state.exception(),
                "subsequent fail() must overwrite previous exception (retry loop semantics)");
    }

    // ==================== RetryPolicy 分类联动（plan 247 核心价值主张） ====================
    //
    // 以下测试证明：fail() 真实保存异常后，RetryPolicy.getRetryDelay(state.exception(), ...) 能据此分类。
    // 修复前 state.exception() 恒为 null → getRetryDelay 跳过 isRecoverableException → 无法分类。
    // 修复后 state.exception() 返回真实异常 → 分类生效。

    @Test
    public void retryPolicy_classifiesBizFatalAsUnrecoverable_afterFail() {
        // 模拟 retry 循环：第一次失败 → fail() 保存 bizFatal 异常 → 下一轮 getRetryDelay 据此分类。
        TaskStepStateBean state = new TaskStepStateBean();
        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(2);
        policy.setRetryDelay(0);

        NopException bizFatal = new NopException(ERR_TEST_BIZ_FATAL).bizFatal(true);
        state.fail(bizFatal, null);

        // state.exception() 返回 bizFatal 异常（修复后），RetryPolicy 据此分类
        assertSame(bizFatal, state.exception(), "fail() must save bizFatal exception");

        long delay = policy.getRetryDelay(state.exception(), 1, null);
        assertTrue(delay < 0,
                "bizFatal exception must be classified as unrecoverable (delay < 0), "
                        + "so retry loop honest-throws immediately without retrying. "
                        + "Pre-fix this returned >= 0 because state.exception() was null.");
    }

    @Test
    public void retryPolicy_classifiesRecoverableAsRecoverable_afterFail() {
        // 对偶验证：非 bizFatal NopException → 可恢复 → getRetryDelay 返回 >= 0（继续重试）。
        TaskStepStateBean state = new TaskStepStateBean();
        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(2);
        policy.setRetryDelay(0);

        NopException recoverable = new NopException(ERR_TEST_RECOVERABLE);
        state.fail(recoverable, null);

        assertSame(recoverable, state.exception());

        // retryAttempt=1, maxRetryCount=2 → 未超限，且可恢复 → delay = 0（retryDelay 配置）
        long delay = policy.getRetryDelay(state.exception(), 1, null);
        assertTrue(delay >= 0,
                "recoverable (non-bizFatal) exception must NOT be rejected at retryAttempt=1 "
                        + "(delay >= 0, will be retried)");

        // retryAttempt=3 > maxRetryCount=2 → 超限 → delay = -1（honest throw after exhausted）
        long delayExceeded = policy.getRetryDelay(state.exception(), 3, null);
        assertTrue(delayExceeded < 0,
                "recoverable exception must honest-throw after retry count exhausted (delay < 0 at retryAttempt=3)");
    }

    @Test
    public void retryPolicy_nullExceptionSkipsClassification_baselineBehavior() {
        // 修复前 baseline 对照：state.exception() 为 null 时，RetryPolicy 跳过 isRecoverableException 判定，
        // 仅按 retryCount 决定。这是修复前的退化行为（无法分类），本测试固化该行为作为对照。
        TaskStepStateBean state = new TaskStepStateBean();
        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(2);
        policy.setRetryDelay(0);

        // 未调用 fail() → state.exception() 为 null
        assertNull(state.exception());

        // null exception → 跳过 isRecoverableException → 仅检查 retryCount
        long delayWithin = policy.getRetryDelay(state.exception(), 1, null);
        assertTrue(delayWithin >= 0,
                "null exception bypasses classification; within retry count → delay >= 0");

        long delayExceeded = policy.getRetryDelay(state.exception(), 3, null);
        assertTrue(delayExceeded < 0,
                "null exception bypasses classification; retry count exceeded → delay < 0");
    }
}
