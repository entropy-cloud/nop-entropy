package io.nop.task.step;

import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.impl.AbstractTaskTestCase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CRA 8.49 R4-09（plan 2542）W-07 行为回归：items 进程内缓存键必须按步骤隔离。
 *
 * <p>缺陷（closure audit Blocker-1）：初版把 items 缓存在 task 级 runtime 的类级常量子键上——
 * 同一 task 内两个 loop 步骤会互相复用对方的 items；loop 体内 fork 跨迭代会复用首轮 items。
 * 仓内 shipped 模型 {@code nop-ai-coder/.../ai-file-dir-summary.task.xml}（loop 内嵌 fork，
 * producer 依赖每轮 {@code dir}）直接受害。
 *
 * <p>修复：缓存键 = 前缀 + stepPath + ":" + runId——不同步骤天然独立，同一 stepPath 的多次
 * 实例化（loop 每轮新建 stepRt）也各自重算。
 */
public class TestLoopItemsKeyScopeR409 extends AbstractTaskTestCase {

    /**
     * 两个顺序 loop 各自的 items 必须独立解析——第二个 loop 不得复用第一个的集合。
     */
    @Test
    public void twoSequentialLoops_useTheirOwnItems() {
        ITask task = taskFlowManager.getTask("test/r409-loop-key-scope", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        Map<String, Object> ret = task.execute(taskRt).syncGetOutputs();

        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "each loop must resolve its own itemsExpr — pre-fix the second loop reused the first "
                        + "loop's cached items (shared class-level attribute key on the task runtime)");
    }

    /**
     * loop 体内嵌 fork：每轮迭代必须按当轮 {@code d} 重算 producer 的 items。
     */
    @Test
    public void forkInsideLoop_recomputesItemsEachIteration() {
        ITask task = taskFlowManager.getTask("test/r409-loop-fork-scope", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        Map<String, Object> ret = task.execute(taskRt).syncGetOutputs();

        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "fork inside loop must recompute producer per iteration — pre-fix it reused the "
                        + "first iteration's items (class-level cache key on the task runtime)");
    }
}
