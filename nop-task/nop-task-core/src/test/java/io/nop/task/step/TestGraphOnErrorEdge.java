package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * check2 P1-3 回归测试：GraphTaskStep 的错误边（nextOnError / waitErrorSteps）可达性。
 *
 * <p>修复前：任何节点出错时 runStep 直接 {@code future.completeExceptionally(e)} + cancel 全图，
 * 且失败节点的 stepFuture 从未异常完成——error-join 依赖 stepFuture 的异常完成信号，永远不触发。
 *
 * <ul>
 *   <li>{@code graphErrorEdge_handlerRuns_taskCompletes}：修复前红（图以原始异常失败）。</li>
 *   <li>{@code graphErrorWithoutEdge_stillFailsFast}：回归守卫——无错误边消费的失败保持 fail-fast，
 *       以原始异常（非 ERR_TASK_GRAPH_NO_ACTIVE_STEP）终结。</li>
 * </ul>
 */
public class TestGraphOnErrorEdge extends AbstractGraphTestCase {

    @Test
    public void graphErrorEdge_handlerRuns_taskCompletes() {
        runTask("test/graph-on-error");
    }

    @Test
    public void graphErrorWithoutEdge_stillFailsFast() {
        ITask task = taskFlowManager.getTask("test/graph-fail-fast", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        try {
            task.execute(taskRt).syncGetOutputs();
            fail("graph without error edge must fail fast on step error");
        } catch (Exception e) {
            assertTrue(containsErrorCode(e, "nop.err.test.graph-fail-fast"),
                    "fail-fast must surface the original step error, got: " + e);
        }
    }

    static boolean containsErrorCode(Throwable e, String errorCode) {
        while (e != null) {
            if (e instanceof NopException && errorCode.equals(((NopException) e).getErrorCode()))
                return true;
            if (e.getMessage() != null && e.getMessage().contains(errorCode))
                return true;
            e = e.getCause();
        }
        return false;
    }
}
