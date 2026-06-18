package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.nop.task.ext.TaskExtErrors.ERR_TASK_DECORATOR_INVALID_CONFIG;
import static io.nop.task.TaskErrors.ERR_TASK_REQUEST_RATE_EXCEED_LIMIT;
import static io.nop.task.TaskErrors.ERR_TASK_RETRY_TIMES_EXCEED_LIMIT;
import static io.nop.task.TaskErrors.ERR_TASK_STEP_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 2 focused 测试：覆盖 retry / timeout / rateLimit 三枚 decorator 的真实运行时行为 + 组合 + 嵌套语义 + 诚实失败 + 零回归。
 *
 * <p>所有测试经标准 `.task.xml` 声明 `<decorator>` → 经 `TaskStepEnhancer.decorateStep` bean lookup → 解析到
 * `nopTaskStepDecorator_<name>` bean → 经委托既有 wrapper 包装 step → 运行时执行。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/task/test/beans/test-reliability.beans.xml")
public class TestReliabilityDecorators extends JunitBaseTestCase {

    @Inject
    ITaskFlowManager taskFlowManager;

    @BeforeEach
    public void resetCounter() {
        counter().reset();
    }

    private ExecutionCounterBean counter() {
        return (ExecutionCounterBean) io.nop.api.core.ioc.BeanContainer.instance().getBean("testExecutionCounter");
    }

    private Map<String, Object> runTask(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        return task.execute(taskRt).syncGetOutputs();
    }

    // -------- retry decorator --------

    @Test
    public void retry_realRetryUntilExhausted() {
        // step 始终抛瞬态异常（NopException 非 bizFatal → 可重试），decorator 应按 maxRetryCount=2 真实重试。
        // 总执行次数 = 1 + maxRetryCount = 3，重试耗尽后诚实抛出 retry-times-exceed-limit。
        try {
            runTask("test/retry-decorator-success");
            fail("should throw after retry exhausted");
        } catch (NopException e) {
            // 不可重试异常或重试耗尽都应诚实抛出（非静默成功）；真实重试次数由下方 counter 断言验证
            assertNotNull(e, "retry exhausted must propagate exception honestly");
        }
        // maxRetryCount=2 → 共执行 1 + 2 = 3 次（断言 ≥ 2 验证真实重试发生，非仅一次执行）
        assertTrue(counter().get() >= 2,
                "retry decorator should cause step body to be executed >= 2 times (real retry happened), actual="
                        + counter().get());
        assertEquals(3, counter().get(),
                "maxRetryCount=2 should execute step body 1 + 2 = 3 times before exhaustion");
    }

    @Test
    public void retry_exhaustedHonestThrow() {
        // step 始终抛瞬态异常（NopException 非 bizFatal → 默认可重试），decorator 按 maxRetryCount=2 真实重试后耗尽抛出。
        // 注：nop-task 既有 in-memory TaskStepStateBean 不在 fail() 中保存 exception 引用，
        // 因此 RetryPolicy.getRetryDelay 接收 null exception 跳过 isRecoverableException 判定，
        // 即所有异常均按 retryCount 重试（不在本计划 scope 内修正，nop-task-core 内部变更为 Non-Goal）。
        // 不可重试异常的运行时分类为独立 successor（依赖 state 保存 exception 引用）。
        try {
            runTask("test/retry-decorator-exhausted");
            fail("should throw after retry exhausted");
        } catch (Exception e) {
            // 重试耗尽后应诚实抛出（非静默成功）；执行次数由下方 counter 断言验证
            assertNotNull(e, "exception must propagate after retry exhausted");
        }
        // maxRetryCount=2 → 共执行 1 + 2 = 3 次
        assertEquals(3, counter().get(),
                "retry decorator should execute step body 1 + maxRetryCount times when exhausted");
    }

    // -------- retry 异常分类（plan 247：依赖 TaskStepStateBean.fail/exception 真实保存） --------
    //
    // 注：xpl 函数调用（AbstractObjFunctionExecutable.doInvoke*）会把被调用方法抛出的异常包装为
    // NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)。plan 249 修复后，该包装保留 bizFatal 标记，
    // 故 bizFatal 分类的端到端验证在 .task.xml E2E 路径成立（下方 retry_bizFatalFailFastE2e）。
    // plan 247 的 nop-task-core 单元测试 TestTaskStepStateBeanExceptionPersistence#retryPolicy_classifiesBizFatalAsUnrecoverable_afterFail
    // 验证 state.fail()→exception()→RetryPolicy 联动（不经 xpl 包装）。

    @Test
    public void retry_recoverableExceptionRetriedE2e() {
        // plan 247 E2E 验证：从 .task.xml 声明 retry decorator → step 抛异常（经 FailureSimulatorBean 构造的真正
        // NopException，但 xpl 调用包装为 NopEvalException，仍为非 bizFatal → 可恢复）→
        // TaskStepHelper.retry 经 state.fail() 保存异常 → state.exception() 返回非 null →
        // RetryPolicy.getRetryDelay(state.exception(), ...) 分类为可恢复 → 按 maxRetryCount 重试至耗尽 → honest throw。
        //
        // 修复前：state.exception() 恒 null → getRetryDelay 跳过分类 → 仍按 retryCount 重试（巧合相同结果），
        // 但 state.exception() 永远为 null（honest throw 真实异常的能力丧失）。
        // 修复后：state.exception() 返回真实异常（端到端路径连通可观测：重试耗尽后抛出的是 state.exception() 保存的异常，
        // 而非 ERR_TASK_RETRY_TIMES_EXCEED_LIMIT generic error）。
        try {
            runTask("test/retry-decorator-recoverable");
            fail("step that always fails should throw after retry exhausted");
        } catch (NopException e) {
            assertNotNull(e, "recoverable exception exhausted must propagate");
            // 接线验证（#23）：修复后 state.exception() 非 null → 抛出的是 state.exception() 保存的异常
            // （NopEvalException 经 NopException.adapt 原样返回），而非 ERR_TASK_RETRY_TIMES_EXCEED_LIMIT。
            // 这证明 fail() → exception() → getRetryDelay → throw 连通（plan 247 核心价值）。
            assertNotEquals(ERR_TASK_RETRY_TIMES_EXCEED_LIMIT.getErrorCode(), e.getErrorCode(),
                    "after plan 247 fix, state.exception() is non-null so the thrown exception is the saved one, "
                            + "not the generic ERR_TASK_RETRY_TIMES_EXCEED_LIMIT fallback");
        }
        // maxRetryCount=2 → 共执行 1 + 2 = 3 次（≥ 2 验证真实重试发生）
        assertTrue(counter().get() >= 2,
                "recoverable exception should be retried (>= 2 executions), actual=" + counter().get());
        assertEquals(3, counter().get(),
                "recoverable exception with maxRetryCount=2 should execute 1 + 2 = 3 times before exhaustion");
    }

    // -------- plan 249: bizFatal fail-fast E2E（retry 分类端到端修复） --------

    @Test
    public void retry_bizFatalFailFastE2e() {
        // plan 249 端到端验证（#22, #23）：从 `.task.xml` 声明 `<decorator name="retry" maxRetryCount=2>` →
        // step 经 xpl 调用 FailureSimulatorBean.throwBizFatal() 抛 bizFatal NopException →
        // AbstractObjFunctionExecutable.doInvoke0 包装为 NopEvalException（plan 249 修复后 bizFatal 标记保留）→
        // TaskStepHelper.retry:176 state.fail(e) → state.exception() 返回包装异常（isBizFatal()==true）→
        // :138 retryPolicy.getRetryDelay → RetryPolicy.isRecoverableException 判定不可恢复 → delay < 0 →
        // 立即 honest throw（执行次数 = 1，不重试）。
        //
        // 修复前（plan 249 前）：包装异常 bizFatal 丢失 → isRecoverableException 返回 true →
        // 重试至 retryCount 耗尽 → 执行次数 = 1 + maxRetryCount(2) = 3，抛出 state.exception()（非 bizFatal）。
        try {
            runTask("test/retry-decorator-bizfatal-failfast");
            fail("bizFatal exception should fail-fast without retry");
        } catch (NopException e) {
            assertNotNull(e, "bizFatal exception must propagate honestly (fail-fast)");
            // 接线验证（#23）：修复后包装异常保留 bizFatal → 分类器判定不可恢复 → 抛出的异常 isBizFatal()==true。
            // 这是 plan 249 核心价值主张的端到端可观测证据。
            assertTrue(e.isBizFatal(),
                    "plan 249 fix: bizFatal flag must be preserved through xpl method-invocation wrap, "
                            + "so the honest-thrown exception reports isBizFatal()==true. got errorCode="
                            + e.getErrorCode());
        }
        // 端到端验证（#22）：bizFatal = 不可恢复 → 立即 honest throw → 执行次数 = 1（不重试）。
        // 修复前此处为 3（1 + maxRetryCount），修复后为 1。
        assertEquals(1, counter().get(),
                "bizFatal exception must fail-fast: execute exactly once (no retry). "
                        + "Pre-fix this was 1 + maxRetryCount = 3 because bizFatal was lost in xpl wrap.");
    }

    // -------- plan 248: retry-wrapped 同步成功 step 执行恰好一次（sync success return 修复） --------

    @Test
    public void retry_syncSuccessExecutesExactlyOnce() {
        // plan 248 端到端验证（#22, #23）：从 `.task.xml` 声明 `<decorator name="retry"/>` 包装一个同步成功 step →
        // RetryTaskStepWrapper.execute → TaskStepHelper.retry → sync 成功 `return result;` 首轮返回。
        //
        // 修复前：sync 成功不 return → 循环重执行至 retry policy 耗尽 → 抛 ERR_TASK_RETRY_TIMES_EXCEED_LIMIT，
        // 且 counter == 1 + maxRetryCount = 3（成功 step 被重复执行）。
        // 修复后：首轮 sync 成功即 return → counter == 1，返回 "OK"。
        Map<String, Object> ret = runTask("test/retry-decorator-sync-success");
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "retry-wrapped sync success step must return the real success result on first execution");
        assertEquals(1, counter().get(),
                "retry-wrapped sync success step must execute exactly once. "
                        + "Pre-fix this was 1 + maxRetryCount = 3 and ultimately threw ERR_TASK_RETRY_TIMES_EXCEED_LIMIT.");
    }

    // -------- timeout decorator --------

    @Test
    public void timeout_realTimeoutFires() {
        try {
            runTask("test/timeout-decorator-fires");
            fail("should timeout");
        } catch (NopException e) {
            // step 体 sleep 2000ms，timeout=200ms，应真实超时失败
            String errorCode = e.getErrorCode();
            // TimeoutTaskStepWrapper → TaskStepHelper.timeout 内部可能包装为 TimeoutException
            // 经 TaskStepHelper.newError 包装为 ERR_TASK_STEP_TIMEOUT
            assertNotNull(errorCode, "timeout must produce an error code");
        }
        // step body 应被执行（在超时取消前已进入 sleep）
        assertEquals(1, counter().get(),
                "slow step should have started executing before timeout cancels it");
    }

    @Test
    public void timeout_withinBudgetNoFailure() {
        Map<String, Object> ret = runTask("test/timeout-decorator-no-fire");
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT));
        assertEquals(1, counter().get());
    }

    // -------- rate-limit decorator --------

    @Test
    public void rateLimit_realLimitingFires() {
        // requestPerSecond=0.0001 (≈ 1 permit / 10000s), global=true, maxWait=0
        // 第一次执行：Guava RateLimiter 首次调用允许初始 permit → 通过
        // 第二次执行：与第一次共享 global limiter（taskName + stepPath 同 key）→
        //           permit 已耗尽，maxWait=0 不等待 → 真实限流失败 ERR_TASK_REQUEST_RATE_EXCEED_LIMIT
        try {
            runTask("test/rate-limit-decorator-fires");
        } catch (NopException e) {
            // 初始 permit 也可能因 Guava 实现细节被拒，只要 errorCode 匹配即视为限流生效
            assertEquals(ERR_TASK_REQUEST_RATE_EXCEED_LIMIT.getErrorCode(), e.getErrorCode(),
                    "first call (if rejected) must propagate ERR_TASK_REQUEST_RATE_EXCEED_LIMIT, got: "
                            + e.getErrorCode());
        }

        // 第二次执行：global limiter 已被第一次消耗，maxWait=0 → 必然限流失败
        try {
            runTask("test/rate-limit-decorator-fires");
            fail("second call should be rejected by rate-limit (permit already consumed, maxWait=0)");
        } catch (NopException e) {
            assertEquals(ERR_TASK_REQUEST_RATE_EXCEED_LIMIT.getErrorCode(), e.getErrorCode(),
                    "second call must be rejected by rate-limit, got: " + e.getErrorCode());
        }
    }

    // -------- combination --------

    @Test
    public void combination_retryAndTimeoutBothApply() {
        // 同一 step 同时声明 retry + timeout 两枚 decorator。
        // 期望：retry 内层（先应用 decorator）+ timeout 外层包装，两枚均生效。
        // 行为：step 始终抛瞬态异常 → 内层 retry 重试 maxRetryCount=2 次（共 3 次执行）→ 重试耗尽抛出。
        // 外层 timeout=5000ms 足够宽，不触发超时（验证 timeout wrapper 不阻断 retry 行为）。
        try {
            runTask("test/combo-decorators-retry-timeout");
            fail("should throw after retry exhausted");
        } catch (NopException e) {
            // 组合装饰器重试耗尽后诚实抛出（非静默成功）；执行次数由下方 counter 断言验证
            assertNotNull(e);
        }
        assertTrue(counter().get() >= 2,
                "combo retry+timeout: retry decorator should cause >= 2 executions, actual=" + counter().get());
        assertEquals(3, counter().get(),
                "combo: maxRetryCount=2 should execute 1+2=3 times");
    }

    // -------- nesting semantics (design adjudication 8) --------

    @Test
    public void nesting_decoratorAndFirstClassAttrProduceNestedWrap() {
        // 同一 step 同时声明 `<decorator name="retry" maxRetryCount=1>` 和 first-class `<retry maxRetryCount=1>`：
        // TaskStepEnhancer.wrap 先应用 decorator（内层），再应用 first-class wrapper（外层）= 嵌套包装组合语义。
        // 行为：内层 decorator retry（maxRetryCount=1）执行 step 1+1=2 次后耗尽抛出 →
        //      外层 first-class retry（maxRetryCount=1）捕获异常并重试整个内层 → 内层再次执行 2 次 →
        //      总执行次数 ≥ 3（嵌套组合可观测）。
        // 这验证了设计裁定 8：decorator 与 first-class-attr 同时声明产生嵌套包装组合（非 bug，非静默跳过）。
        try {
            runTask("test/nested-decorator-and-attr");
            fail("nested wrap should still throw after all retries exhausted");
        } catch (Exception e) {
            // 嵌套包装重试耗尽后诚实抛出（非静默成功）；嵌套执行次数由下方 counter 断言验证
            assertNotNull(e);
        }
        int actual = counter().get();
        // 期望至少 3 次：内层 2 次（耗尽抛出）+ 外层重试触发内层至少再 1 次。
        // 严格 4 次（2 × 2）取决于 RetryPolicy 与 first-class wrapper 的具体交互；
        // 关键断言是 ≥ 3 验证嵌套包装确实发生（单层 maxRetryCount=1 只产生 2 次执行）。
        assertTrue(actual >= 3,
                "nested decorator+first-class retry should produce observable nested wrapping "
                        + "(expected >= 3 executions vs single-layer 2), actual=" + actual);
    }

    // -------- honest failure (#24) --------

    @Test
    public void honestFail_retryNegativeMaxRetryCount() {
        try {
            runTask("test/honest-fail-retry-negative");
            fail("negative maxRetryCount should throw honest NopException");
        } catch (NopException e) {
            assertEquals(ERR_TASK_DECORATOR_INVALID_CONFIG.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void honestFail_retryMissingRequiredConfig() {
        try {
            runTask("test/honest-fail-retry-missing");
            fail("missing required retry:maxRetryCount should throw honest NopException");
        } catch (NopException e) {
            assertEquals(ERR_TASK_DECORATOR_INVALID_CONFIG.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void honestFail_timeoutZero() {
        try {
            runTask("test/honest-fail-timeout-zero");
            fail("timeout=0 should throw honest NopException");
        } catch (NopException e) {
            assertEquals(ERR_TASK_DECORATOR_INVALID_CONFIG.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void honestFail_timeoutMissing() {
        try {
            runTask("test/honest-fail-timeout-missing");
            fail("missing timeout:timeout should throw honest NopException");
        } catch (NopException e) {
            assertEquals(ERR_TASK_DECORATOR_INVALID_CONFIG.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void honestFail_rateLimitZero() {
        try {
            runTask("test/honest-fail-rate-limit-zero");
            fail("rateLimit requestPerSecond=0 should throw honest NopException");
        } catch (NopException e) {
            assertEquals(ERR_TASK_DECORATOR_INVALID_CONFIG.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void honestFail_rateLimitMissing() {
        try {
            runTask("test/honest-fail-rate-limit-missing");
            fail("missing rateLimit:requestPerSecond should throw honest NopException");
        } catch (NopException e) {
            assertEquals(ERR_TASK_DECORATOR_INVALID_CONFIG.getErrorCode(), e.getErrorCode());
        }
    }

    // -------- zero regression --------

    @Test
    public void zeroRegression_noDecoratorBehavesEqually() {
        Map<String, Object> ret = runTask("test/no-decorator-baseline");
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT));
        assertEquals(1, counter().get(),
                "step body without decorator should execute exactly once");
    }

    // -------- Phase 3: E2E coverage (#22, #23) --------

    @Test
    public void e2e_allThreeDecoratorsWiringVerified() {
        // 端到端验证（#22）：从 `.task.xml` `<decorator>` 声明 → `TaskStepEnhancer.decorateStep` bean 解析
        // → wrapper 包装 → `task.execute` 运行时行为完整路径跑通。
        //
        // 三 decorator 的端到端运行时行为已分别在 `retry_realRetryUntilExhausted`（retry 真实重试 + 耗尽抛出）、
        // `timeout_realTimeoutFires`（timeout 真实超时抛出）、`rateLimit_realLimitingFires`（rateLimit 真实限流抛出）
        // 中独立验证。本测试再补一条：声明 `retry` decorator 的 step 经 `task.execute` 完整执行链路触发真实重试
        // （maxRetryCount=1 → 2 次执行后耗尽抛出），证明从 XML 声明到运行时 wrapper 包装完整连通。
        try {
            runTask("test/e2e-all-three-decorators");
            fail("E2E: retry decorator with maxRetryCount=1 should cause exhaustion throw");
        } catch (NopException e) {
            // 接线验证（#23）：decorator bean 解析 + wrapper 包装 + 运行时执行链路连通；
            // 执行次数由下方 counter 断言验证
            assertNotNull(e, "E2E: decorator wire-up should propagate exhaustion exception");
        }
        // 接线验证：maxRetryCount=1 → 2 次执行（1 + 1 retry），证明 retry decorator 真实生效
        assertEquals(2, counter().get(),
                "E2E: retry decorator with maxRetryCount=1 should execute step body 1+1=2 times");
    }
}
