package io.nop.task.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.task.TaskConstants;
import org.junit.jupiter.api.Test;

import static io.nop.task.TaskErrors.ERR_TASK_STEP_CONFIG_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 364 Phase 2 [维度02-06]：first-class 模型属性与 decorator 两条路径对同一非法配置值
 * 的行为对齐矩阵（first-class 侧此前静默不生效，decorator 侧硬失败——同一取值两种结局）。
 *
 * <p>矩阵（first-class 侧，本测试锁定）：
 * <ul>
 *   <li>timeout &lt; 0 → 构建期抛 ERR_TASK_STEP_CONFIG_INVALID；timeout=0 → 未配置，正常运行</li>
 *   <li>rate-limit 节点存在但 requestPerSecond&lt;=0 → 抛 ERR_TASK_STEP_CONFIG_INVALID</li>
 *   <li>retry 负值（maxRetryCount/retryDelay/maxRetryDelay）→ 抛 ERR_TASK_STEP_CONFIG_INVALID</li>
 * </ul>
 * decorator 侧行为由 nop-task-ext 的 TestReliabilityDecorators honestFail_* 用例锁定
 * （timeout&lt;=0 / retry 负值 / 缺配置均抛 ERR_TASK_DECORATOR_INVALID_CONFIG）。
 */
public class TestFirstClassConfigValidation extends AbstractTaskTestCase {

    @Test
    public void negativeTimeout_failsAtBuildTime() {
        NopException err = assertThrows(NopException.class,
                () -> taskFlowManager.getTask("test/fc-timeout-negative", 0),
                "negative first-class timeout must hard-fail like the decorator path (02-06)");
        assertEquals(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode(), err.getErrorCode());
        assertEquals("timeout", err.getParam("attrName"));
    }

    @Test
    public void zeroTimeout_meansNotConfigured() {
        var ret = runTask("test/fc-timeout-zero");
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "timeout=0/absent means 'not configured' on the first-class path (explicit 0 not representable)");
    }

    @Test
    public void nonPositiveRateLimit_configuredNode_failsAtBuildTime() {
        NopException err = assertThrows(NopException.class,
                () -> taskFlowManager.getTask("test/fc-ratelimit-nonpositive", 0),
                "rateLimit node present with requestPerSecond<=0 must hard-fail like the decorator path (02-06)");
        assertEquals(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode(), err.getErrorCode());
    }

    @Test
    public void negativeRetryConfig_failsAtBuildTime() {
        NopException err = assertThrows(NopException.class,
                () -> taskFlowManager.getTask("test/fc-retry-negative", 0),
                "negative first-class retry config must hard-fail like the decorator path (02-06)");
        assertEquals(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode(), err.getErrorCode());
    }
}
