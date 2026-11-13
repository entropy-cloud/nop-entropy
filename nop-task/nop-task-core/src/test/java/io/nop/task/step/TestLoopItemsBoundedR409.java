package io.nop.task.step;

import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.impl.AbstractTaskTestCase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.49 R4-09（plan 2542）W-07 有界性回归：items 进程内载体必须随步骤释放。
 *
 * <p>缺陷（closure audit R2 Major-1）：修复 Blocker-1 后的中间形态把缓存放 task 级 runtime
 * 并按 {@code stepPath+runId} 限定——隔离正确，但条目永不释放：loop 体内 fork 每轮留一份
 * items，task 全程持有 O(Σ各轮 items)，与 W-07「有界化」目标相反。
 *
 * <p>最终形态：载体回到 stepRt 作用域（stateBean 的 {@code @JsonIgnore} 字段），生命周期随
 * 步骤结束自动释放，runtime attribute 表零累积。
 */
public class TestLoopItemsBoundedR409 extends AbstractTaskTestCase {

    /**
     * 多轮 loop（含内嵌 fork）跑完后，task runtime 的 attribute 表不得累积 items 条目。
     */
    @Test
    public void loopWithFork_doesNotAccumulateRuntimeAttributes() {
        ITask task = taskFlowManager.getTask("test/r409-loop-fork-scope", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        Map<String, Object> ret = task.execute(taskRt).syncGetOutputs();

        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "loop-with-fork scenario must execute correctly");

        long itemAttrs = taskRt.getAttributeKeys().stream()
                .filter(k -> k.startsWith(LoopTaskStep.ITEMS_ATTR_PREFIX)
                        || k.startsWith(ForkTaskStep.ITEMS_ATTR_PREFIX))
                .count();
        assertTrue(itemAttrs == 0,
                "items must not accumulate on the task runtime across iterations (W-07 bounded-memory; "
                        + "pre-fix: one entry per iteration retained for the whole task) - found " + itemAttrs);
    }
}
