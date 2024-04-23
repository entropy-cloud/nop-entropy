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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    public void testBuild() {
        IClassModel classModel = ReflectionManager.instance().getClassModel(MyFlow.class);
        GraphTaskStep taskGraph = classModel.getAnnotation(GraphTaskStep.class);
        GraphTaskStepModel stepModel = new ReflectionTaskStepBuilder().buildTaskStepGraph(classModel, taskGraph, "myFlow");
        new GraphStepAnalyzer().analyze(stepModel);
        XNode node = stepModel.toNode();
        node.dump();
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