package io.nop.task.ext.reliability;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * Plan 247/249 测试辅助 bean：从 xpl step 中抛出 NopException，使 retry decorator 端到端路径可观测。
 *
 * <p>xpl 源码无法直接构造 {@code new NopException(ErrorCode)}（公开构造器只接受 ErrorCode，
 * xpl 字符串字面量无法自动转 ErrorCode）。本 bean 提供 Java 端异常构造方法，经
 * {@code inject("testFailureSimulator")} 从 xpl 调用。
 *
 * <p>plan 249 修复后，{@code AbstractObjFunctionExecutable.doInvoke*} 包装异常时会保留
 * 被调用方法抛出的 bizFatal 标记，因此 {@link #throwBizFatal()} 经 xpl 调用后包装的
 * NopEvalException 仍报告 isBizFatal()==true → RetryPolicy 判定不可恢复 → 立即 honest throw（不重试）。
 */
public class FailureSimulatorBean {
    public static final String BEAN_NAME = "testFailureSimulator";

    private static final ErrorCode ERR_TEST_RECOVERABLE =
            ErrorCode.define("nop.err.test.plan-247.recoverable", "plan 247 test: recoverable");

    private static final ErrorCode ERR_TEST_BIZ_FATAL =
            ErrorCode.define("nop.err.test.plan-249.biz-fatal", "plan 249 test: bizFatal");

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

    /**
     * 抛出 bizFatal NopException（RetryPolicy 默认分类为不可恢复 → 立即 honest throw，不重试）。
     * plan 249 修复后，经 xpl {@code doInvoke*} 包装的 NopEvalException 保留 bizFatal 标记，
     * 故 RetryPolicy.isRecoverableException 返回 false → 执行次数 = 1（立即 fail-fast）。
     * 同时递增 execution counter，供测试断言执行次数。
     */
    public void throwBizFatal() {
        counter.incrementAndGet();
        throw new NopException(ERR_TEST_BIZ_FATAL).bizFatal(true);
    }

    /**
     * 属性 getter 抛 bizFatal NopException（plan 250 attr 路径 fixture）。
     *
     * <p>区别于 {@link #throwBizFatal()}（方法调用 → {@code doInvoke*} 包装，plan 249 路径）：
     * 本 getter 经 xpl <b>bracket 记法</b> {@code sim['bizFatalAttr']}（编译为 {@code GetAttrExecutable}
     * → {@code AbstractExecutable.readAttr}）访问时触发。plan 250 修复后，{@code readAttr} 包装异常保留
     * bizFatal 标记 → {@code RetryPolicy.isRecoverableException} 判定不可恢复 → 执行次数 = 1。
     *
     * <p>同时递增 execution counter，供测试断言执行次数。
     */
    public Object getBizFatalAttr() {
        counter.incrementAndGet();
        throw new NopException(ERR_TEST_BIZ_FATAL).bizFatal(true);
    }

    /**
     * 属性 getter 抛非 bizFatal NopException（plan 250 attr 路径对偶回归 fixture）。
     * 经 xpl bracket 记法 {@code sim['recoverableAttr']}（{@code GetAttrExecutable} → {@code readAttr}）访问触发，
     * 包装后仍非 bizFatal → 可恢复 → 按 maxRetryCount 重试至耗尽。
     */
    public Object getRecoverableAttr() {
        counter.incrementAndGet();
        throw new NopException(ERR_TEST_RECOVERABLE);
    }

    /**
     * 属性 getter 抛 bizFatal NopException（plan 251 prop/dot 路径 fixture）。
     *
     * <p>区别于 {@link #getBizFatalAttr()}（bracket 记法 {@code sim['bizFatalAttr']} → {@code GetAttrExecutable}
     * → {@code readAttr}，plan 250 路径）和 {@link #throwBizFatal()}（方法调用 → {@code doInvoke*}，plan 249 路径）：
     * 本 getter 经 xpl <b>dot 记法</b> {@code sim.bizFatalProp}（编译为 {@code GetPropertyExecutable}
     * → 基类 {@code AbstractPropertyExecutable.readProp}，经 {@code IPropertyGetter} 反射调用）访问时触发。
     * plan 251 修复后，{@code readProp} 包装异常保留 bizFatal 标记 →
     * {@code RetryPolicy.isRecoverableException} 判定不可恢复 → 执行次数 = 1。
     *
     * <p>同时递增 execution counter，供测试断言执行次数。
     */
    public Object getBizFatalProp() {
        counter.incrementAndGet();
        throw new NopException(ERR_TEST_BIZ_FATAL).bizFatal(true);
    }

    /**
     * 属性 getter 抛非 bizFatal NopException（plan 251 prop/dot 路径对偶回归 fixture）。
     * 经 xpl dot 记法 {@code sim.recoverableProp}（{@code GetPropertyExecutable} → 基类 {@code readProp}）访问触发，
     * 包装后仍非 bizFatal → 可恢复 → 按 maxRetryCount 重试至耗尽。
     */
    public Object getRecoverableProp() {
        counter.incrementAndGet();
        throw new NopException(ERR_TEST_RECOVERABLE);
    }
}
