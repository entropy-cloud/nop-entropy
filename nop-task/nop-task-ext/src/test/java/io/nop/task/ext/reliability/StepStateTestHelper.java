package io.nop.task.ext.reliability;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plan 253 Phase 3 测试辅助 bean：为 retry async + delay&gt;0 scheduled-retry + 非 retry step
 * succeed-driver E2E 提供 async 返回值和 fail-then-succeed 行为。
 *
 * <p>经 {@code inject("testStepStateHelper")} 从 xpl step 中调用，使 step 返回 CompletionStage
 * → {@link io.nop.task.step.EvalTaskStep} 经 {@link io.nop.task.TaskStepReturn#of} 包装为 async
 * TaskStepReturn → 触发 {@link io.nop.task.utils.TaskStepHelper} async 成功路径。
 */
public class StepStateTestHelper {
    public static final String BEAN_NAME = "testStepStateHelper";

    private static final ErrorCode ERR_TEST_FAIL_ONCE =
            ErrorCode.define("nop.err.test.plan-253.fail-once", "plan 253 test: fail once then succeed");

    private final AtomicInteger failOnceAttempt = new AtomicInteger(0);

    private ExecutionCounterBean counter;

    public void setCounter(ExecutionCounterBean counter) {
        this.counter = counter;
    }

    /**
     * 返回一个异步完成的 CompletableFuture（经 supplyAsync 在独立线程执行，短延迟后完成）。
     *
     * <p>这使 xpl step 的返回值经 EvalTaskStep → TaskStepReturn.of → async TaskStepReturn，
     * 触发 TaskStepHelper.retry 的 primary async 路径（:172-173 thenCompose → doRetry）。
     * delayMs > 0 确保 isDone() 在 retry 检查时返回 false（future 尚未完成），
     * 从而走 thenCompose 路径而非 already-done 快捷路径。
     */
    public CompletableFuture<String> asyncSuccess(String value, long delayMs) {
        counter.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return value;
        });
    }

    /**
     * 第一次调用抛出非 bizFatal 异常（触发 retry），后续调用返回 "OK"。
     *
     * <p>用于测试 delay&gt;0 scheduled-retry 成功路径：
     * 第一次执行失败 → state.fail → retryAttempt++ → getRetryDelay 返回 delay &gt; 0 →
     * 进入 scheduled-retry 分支 → 延迟后 action 执行成功 → doRetry(success) → state.succeed。
     */
    public Object failOnceThenSucceed() {
        counter.incrementAndGet();
        if (failOnceAttempt.incrementAndGet() == 1) {
            throw new NopException(ERR_TEST_FAIL_ONCE);
        }
        return "OK";
    }

    /**
     * 同步返回成功值（用于非 retry step 的简单成功验证）。
     */
    public Object syncSuccess(String value) {
        counter.incrementAndGet();
        return value;
    }

    public void reset() {
        failOnceAttempt.set(0);
    }
}
