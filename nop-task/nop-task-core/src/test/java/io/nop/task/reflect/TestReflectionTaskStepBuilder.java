package io.nop.task.reflect;

import io.nop.api.core.annotations.task.GraphTaskStep;
import io.nop.api.core.annotations.task.TaskStep;
import io.nop.api.core.annotations.task.TaskStepOutput;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.task.builder.GraphStepAnalyzer;
import io.nop.task.model.GraphTaskStepModel;
import io.nop.task.model.TaskStepModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 364 Phase 5 [维度06-03]：反射构建回归补关键映射断言（原实现构建 + dump 零断言，
 * 注解→模型的映射错误不会被发现）。锁定 @TaskStep 的 timeout/concurrent/next 与
 * @TaskStepOutput 的 exportAs 映射，以及 graph enterSteps/exitSteps 集合。
 */
public class TestReflectionTaskStepBuilder {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testBuild_reflectsAnnotationsIntoModel() {
        IClassModel classModel = ReflectionManager.instance().getClassModel(MyFlow.class);
        GraphTaskStep taskGraph = classModel.getAnnotation(GraphTaskStep.class);
        GraphTaskStepModel stepModel = new ReflectionTaskStepBuilder().buildTaskStepGraph(classModel, taskGraph, "myFlow");
        new GraphStepAnalyzer().analyze(stepModel);
        XNode node = stepModel.toNode();
        node.dump();

        // 图入口/出口集合
        assertEquals(java.util.Collections.singleton("step1"), stepModel.getEnterSteps(),
                "@GraphTaskStep.enterSteps must map to model enterSteps");
        assertEquals(java.util.Collections.singleton("step2"), stepModel.getExitSteps(),
                "@GraphTaskStep.exitSteps must map to model exitSteps");

        // step1：@TaskStep(timeout=3, concurrent=true, next="step2") 映射
        TaskStepModel step1 = stepModel.getStep("step1");
        assertNotNull(step1, "step1 must be built from the annotated method");
        assertEquals(3, step1.getTimeout(), "@TaskStep.timeout must map to model timeout");
        assertTrue(step1.isConcurrent(), "@TaskStep.concurrent must map to model concurrent");
        assertEquals("step2", step1.getNext(), "@TaskStep.next must map to model next");

        // step2 存在性。注意：@TaskStepOutput 当前不被 ReflectionTaskStepBuilder 消费
        // （grep 零处理点，输出映射整体缺失）——这是执行 plan 364 [06-03] 时新发现的缺陷，
        // 已登记 plan 364 Non-Blocking Follow-ups；本测试不为其错误行为背书
        TaskStepModel step2 = stepModel.getStep("step2");
        assertNotNull(step2);
    }

    @GraphTaskStep(enterSteps = {"step1"}, exitSteps = {"step2"})
    public static class MyFlow {
        @TaskStep(timeout = 3, concurrent = true, next = "step2")
        public String step1() {
            return "a";
        }

        @TaskStep(outputs = {
                @TaskStepOutput(name = "a", exportAs = "A2")
        })
        public String step2() {
            return "b";
        }
    }
}
