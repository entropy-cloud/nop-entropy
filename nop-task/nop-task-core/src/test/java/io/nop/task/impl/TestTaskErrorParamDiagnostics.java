package io.nop.task.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.utils.TaskStepHelper;
import org.junit.jupiter.api.Test;

import static io.nop.task.TaskErrors.ARG_NEXT_STEP;
import static io.nop.task.TaskErrors.ARG_STEP_NAME;
import static io.nop.task.TaskErrors.ARG_STEP_PATH;
import static io.nop.task.TaskErrors.ARG_GRAPH_STEP_NAME;
import static io.nop.task.TaskErrors.ARG_TASK_NAME;
import static io.nop.task.TaskErrors.ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_NEXT_STEP;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_STEP_IN_GRAPH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 364 Phase 2 回归：错误诊断参数可达且消息已渲染。
 *
 * <p>[维度01-01] {@code TaskStepHelper.newError} 此前方法体直接 throw，调用点的 {@code .param(...)}
 * 链全部不可达（plan 255 的 TestTaskFlowAnalyzer 只覆盖 analyzer 自建的参数链，未覆盖这 5 个
 * newError 调用点）；修复后 newError 真正返回 NopException，参数进入异常且消息模板渲染出实际值。
 * 静态 next 引用在构建期已被 plan 255 的 TaskFlowAnalyzer.checkStepRef 校验拦截（自带参数），
 * newError 链路是运行期动态名字的防线，直接以单元契约锁定。
 *
 * <p>[维度01-02] GraphStepAnalyzer enter/exit 两处 unknown-step 校验此前只绑定图名到
 * {@code ARG_STEP_NAME} 且缺 {@code ARG_GRAPH_STEP_NAME}，修复后按 {@code addInputDepend}
 * 的正确用法绑定两个参数。
 *
 * <p>[维度01-03] {@code TaskImpl.checkInputs} 此前把 stepType 的值绑给 {@code ARG_STEP_PATH}，
 * 修复后传主步骤真实路径（{@code TaskConstants.MAIN_STEP_NAME}）。
 */
public class TestTaskErrorParamDiagnostics extends AbstractTaskTestCase {

    /**
     * [维度01-01] newError 返回语义直接契约：返回而非抛出、.param 链可达、
     * 基础诊断参数（taskName/stepPath/runId）由 newError 自身填充、消息渲染出实际值。
     */
    @Test
    public void newError_returnsException_paramsReachable_messageRendered() {
        ITaskStepState state = new io.nop.task.state.TaskStepStateBean();
        ITaskStepRuntime stepRt = FakeStepRt.of(state);

        NopException ex = TaskStepHelper.newError(null, stepRt, ERR_TASK_UNKNOWN_NEXT_STEP)
                .param(ARG_NEXT_STEP, "missing-step");

        assertEquals(ERR_TASK_UNKNOWN_NEXT_STEP.getErrorCode(), ex.getErrorCode(),
                "newError must return the exception (pre-fix the method body threw, making .param chains unreachable)");
        assertEquals("missing-step", ex.getParam(ARG_NEXT_STEP), "caller .param chain must be reachable (01-01)");
        assertEquals("fake-task", ex.getParam(ARG_TASK_NAME), "newError fills base diagnostic params");
        assertEquals("/fake", ex.getParam(ARG_STEP_PATH));
        String message = ex.getMessage();
        assertFalse(message.contains("{nextStep}"),
                "rendered message must not contain unreplaced placeholder, got: " + message);
    }

    /**
     * [维度01-01] 带 cause 的重载同样返回语义；流程级回归：静态 next 引用构建期即被
     * analyzer 拒绝且消息渲染（ analyzer 与 newError 链路各自携带参数）。
     */
    @Test
    public void unknownNextStep_rejectedAtBuildTime_withRenderedMessage() {
        NopException err = assertThrows(NopException.class,
                () -> taskFlowManager.getTask("test/next-step-error", 0),
                "next pointing to a missing step must be rejected at build time");

        assertEquals(ERR_TASK_UNKNOWN_NEXT_STEP.getErrorCode(), err.getErrorCode());
        assertEquals("missing-step", err.getParam(ARG_NEXT_STEP));
        assertFalse(err.getMessage().contains("{nextStep}"),
                "rendered message must not contain unreplaced placeholder, got: " + err.getMessage());
    }

    /** [维度01-02] exit 循环：图名与缺失步骤名分别绑定到 graphStepName/stepName。 */
    @Test
    public void unknownGraphExitStep_errorCarriesBothNames() {
        NopException err = assertThrows(NopException.class,
                () -> taskFlowManager.getTask("test/graph-unknown-exit", 0));

        assertEquals(ERR_TASK_UNKNOWN_STEP_IN_GRAPH.getErrorCode(), err.getErrorCode());
        assertEquals("test", err.getParam(ARG_GRAPH_STEP_NAME), "graph name must bind to graphStepName (01-02)");
        assertEquals("missing-exit", err.getParam(ARG_STEP_NAME), "missing step name must bind to stepName (01-02)");
    }

    /** [维度01-02] enter 循环与 exit 循环同病，同口径回归。 */
    @Test
    public void unknownGraphEnterStep_errorCarriesBothNames() {
        NopException err = assertThrows(NopException.class,
                () -> taskFlowManager.getTask("test/graph-unknown-enter", 0));

        assertEquals(ERR_TASK_UNKNOWN_STEP_IN_GRAPH.getErrorCode(), err.getErrorCode());
        assertEquals("test", err.getParam(ARG_GRAPH_STEP_NAME));
        assertEquals("missing-enter", err.getParam(ARG_STEP_NAME));
    }

    /** [维度01-03] ARG_STEP_PATH 必须是主步骤真实路径（@main），而非 stepType（"task"）。 */
    @Test
    public void mandatoryInputError_stepPathIsMainStepPath() {
        ITask task = taskFlowManager.getTask("test/task-mandatory-input-empty", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);

        NopException err = assertThrows(NopException.class,
                () -> task.execute(taskRt).syncGetOutputs());

        assertEquals(ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY.getErrorCode(), err.getErrorCode());
        assertEquals(TaskConstants.MAIN_STEP_NAME, err.getParam(ARG_STEP_PATH),
                "ARG_STEP_PATH must carry the main step path, not the stepType value (01-03)");
    }
}
