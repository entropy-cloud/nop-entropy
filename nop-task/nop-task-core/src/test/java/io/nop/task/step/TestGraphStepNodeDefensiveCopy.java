package io.nop.task.step;

import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 364 Phase 4 [维度02-13/01-10]：GraphStepNode 构造器防御性复制。
 *
 * <p>缺陷（修复前）：构造器对入参 Set 原地 removeAll，而调用方传入的是
 * _TaskStepModel.getWaitSteps() 返回的模型自有活引用——图构建后模型的 waitSteps/
 * waitErrorSteps 交集被永久移除，序列化展示与 DSL 原文不一致、二次构建时
 * completeSteps 为空导致图等待语义被静默改变。
 */
public class TestGraphStepNodeDefensiveCopy {

    @Test
    public void constructor_mustNotMutateCallerSuppliedSets() {
        Set<String> waitSteps = new HashSet<>(Set.of("a", "shared", "b"));
        Set<String> waitErrorSteps = new HashSet<>(Set.of("c", "shared"));

        GraphTaskStep.GraphStepNode node = new GraphTaskStep.GraphStepNode(
                waitSteps, waitErrorSteps, new NoopExecution(), false, false);

        // 入参集合（模拟模型自有字段）必须原样保留，包括交集元素 "shared"
        assertTrue(waitSteps.contains("shared"),
                "caller-supplied waitSteps must not be mutated by the constructor (02-13)");
        assertTrue(waitErrorSteps.contains("shared"),
                "caller-supplied waitErrorSteps must not be mutated by the constructor (02-13)");
        assertEquals(3, waitSteps.size());
        assertEquals(2, waitErrorSteps.size());

        // 归一化结果正确：交集进入 waitCompleteSteps，各自剩余部分进入 success/error
        assertEquals(Set.of("shared"), node.getWaitCompleteSteps());
        assertEquals(Set.of("a", "b"), node.getWaitSuccessSteps());
        assertEquals(Set.of("c"), node.getWaitErrorSteps());
    }

    static class NoopExecution extends AbstractTaskStep implements io.nop.task.ITaskStepExecution {
        @Override
        public String getStepName() {
            return "noop";
        }

        @Override
        public TaskStepReturn execute(ITaskStepRuntime stepRt) {
            return TaskStepReturn.CONTINUE;
        }

        @Override
        public TaskStepReturn executeWithParentRt(ITaskStepRuntime stepRt) {
            return TaskStepReturn.CONTINUE;
        }
    }
}
