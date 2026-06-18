package io.nop.task.ext.reliability;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * Plan 247 测试辅助 bean：从 xpl step 中抛出 NopException，使 retry decorator 端到端路径可观测。
 *
 * <p>xpl 源码无法直接构造 {@code new NopException(ErrorCode)}（公开构造器只接受 ErrorCode，
 * xpl 字符串字面量无法自动转 ErrorCode）。本 bean 提供 Java 端异常构造方法，经
 * {@code inject("testFailureSimulator")} 从 xpl 调用。
 *
 * <p>注：xpl 函数调用（{@code AbstractObjFunctionExecutable.doInvoke*}）会把本 bean 方法抛出的
 * NopException 包装为 {@code NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)}，丢失 bizFatal 标记。
 * 这是 xpl 既有行为（非 plan 247 scope）。因此本 bean 仅用于验证可恢复路径（包装后仍为非 bizFatal → 可恢复）。
 * bizFatal 分类的 focused 验证在 nop-task-core 单元测试层完成（不经 xpl 包装）。
 */
public class FailureSimulatorBean {
    public static final String BEAN_NAME = "testFailureSimulator";

    private static final ErrorCode ERR_TEST_RECOVERABLE =
            ErrorCode.define("nop.err.test.plan-247.recoverable", "plan 247 test: recoverable");

    private ExecutionCounterBean counter;

    public void setCounter(ExecutionCounterBean counter) {
        this.counter = counter;
    }

    /**
     * 抛出非 bizFatal NopException（RetryPolicy 默认分类为可恢复 → 按 maxRetryCount 重试）。
     * 经 xpl 调用后包装为 NopEvalException（仍非 bizFatal → 仍可恢复）。
     * 同时递增 execution counter，供测试断言执行次数。
     */
    public void throwRecoverable() {
        counter.incrementAndGet();
        throw new NopException(ERR_TEST_RECOVERABLE);
    }
}
