package io.nop.task.step;

import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.impl.TaskFlowManagerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * check2 P3-5 回归测试：ParallelTaskStep 聚合对未完成分支补 ERR_TASK_CANCELLED 占位
 * （与 fork 族 AbstractForkTaskStep.buildAggResult 行为对齐）。
 *
 * <p>joinType=ANY + autoCancelUnfinished=false 时，聚合时刻未完成的分支
 * 修复前在 MultiStepResultBean 中直接缺席（aggregator 无法区分"分支被取消"与"分支不存在"）。
 */
public class TestParallelAggregationPlaceholder {

    private TaskFlowManagerImpl taskFlowManager;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        taskFlowManager = new TaskFlowManagerImpl();
    }

    @Test
    public void anyJoin_unfinishedBranchGetsCancelledPlaceholder() {
        ITask task = taskFlowManager.getTask("test/parallel-any-placeholder", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);

        TaskStepReturn ret = task.execute(taskRt);
        Map<String, Object> outputs = ret.syncGetOutputs();

        assertEquals("OK", outputs.get(TaskConstants.VAR_RESULT),
                "aggregation over ANY-join must include a cancelled placeholder for the unfinished branch "
                        + "(aggResults.size()==2). Pre-fix: the unfinished branch was silently dropped "
                        + "and aggResults.size()==1.");
    }
}
