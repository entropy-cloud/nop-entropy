package io.nop.task.builder;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.impl.TaskFlowManagerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_NEXT_STEP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * check2 P3-3 回归测试：TaskFlowAnalyzer.forEachStep 对 if/then/else 分支递归遍历。
 *
 * <p>修复前：then/else 只用 {@code action.accept} 处理一层，其内部再嵌套 if/choose/sequential 的
 * 孙级步骤不被 normalize/checkStepRef/forceUseParentScope 覆盖（choose 分支则是正确递归的）。
 *
 * <p>用嵌套 if 孙级步骤的 next=ghost 非法引用验证：修复前模型加载期不报错（构建期错误延后到运行期），
 * 修复后立即抛 ERR_TASK_UNKNOWN_NEXT_STEP。
 */
public class TestTaskFlowAnalyzerIfRecursion {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void nestedIfGrandchildStepRef_detectedAtModelLoad() {
        TaskFlowManagerImpl taskFlowManager = new TaskFlowManagerImpl();
        try {
            taskFlowManager.getTask("test/nested-if-bad-ref", 0);
            fail("invalid step ref inside nested if branch must be rejected at model load time. "
                    + "Pre-fix: forEachStep only visited one level of if/then/else and 'ghost' was never checked.");
        } catch (NopException e) {
            assertEquals(ERR_TASK_UNKNOWN_NEXT_STEP.getErrorCode(), e.getErrorCode(),
                    "nested if grandchild step ref must fail with ERR_TASK_UNKNOWN_NEXT_STEP");
        }
    }
}
