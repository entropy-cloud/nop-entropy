package io.nop.task.impl;

import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 P2-2 回归测试：父任务取消传播到子任务 runtime。
 *
 * <p>修复前：{@code newChildRuntime} 把 {@code this::cancel}（父 runtime 自引用）注册到父自己的
 * 监听器上（重入直接返回，无效果），本意是 {@code taskRt::cancel}。svcCtx 为 null 时
 * （newTaskRuntime(task, saveState, null)）不存在经 svcCtx 的间接传播路径，
 * CallTaskStep 子任务在父任务 cancel 后继续运行。
 */
public class TestChildRuntimeCancelPropagation {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void parentCancel_propagatesToChildRuntime_whenSvcCtxIsNull() {
        TaskFlowManagerImpl taskFlowManager = new TaskFlowManagerImpl();
        ITask task = taskFlowManager.getTask("test/sequential-01", 0);

        TaskRuntimeImpl parent = (TaskRuntimeImpl) taskFlowManager.newTaskRuntime(task, false, null);
        ITaskRuntime child = parent.newChildRuntime(task, false);

        assertFalse(child.isCancelled(), "child runtime must not be cancelled initially");

        parent.cancel("kill");

        assertTrue(child.isCancelled(),
                "parent cancel must propagate to the child runtime. Pre-fix: the self-referencing "
                        + "this::cancel listener was a no-op and the child kept running after parent cancel.");
    }
}
