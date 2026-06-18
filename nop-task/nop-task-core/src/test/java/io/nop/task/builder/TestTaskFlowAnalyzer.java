package io.nop.task.builder;

import io.nop.api.core.exceptions.NopException;
import io.nop.task.model.SimpleTaskStepModel;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.model.TaskStepModel;
import org.junit.jupiter.api.Test;

import static io.nop.task.TaskErrors.ARG_NEXT_STEP;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_NEXT_STEP;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 255: focused regression for {@link TaskFlowAnalyzer#checkStepRef} nextOnError validation.
 * Covers three nextOnError scenarios plus {@code next} branch zero-regression.
 */
public class TestTaskFlowAnalyzer {

    private static TaskFlowModel newFlow(TaskStepModel... subSteps) {
        TaskFlowModel flow = new TaskFlowModel();
        for (TaskStepModel step : subSteps) {
            flow.addStep(step);
        }
        return flow;
    }

    private static SimpleTaskStepModel step(String name) {
        SimpleTaskStepModel step = new SimpleTaskStepModel();
        step.setName(name);
        return step;
    }

    private static void assertUnknownNextStep(NopException ex, String expectedNextStep) {
        assertEquals(ERR_TASK_UNKNOWN_NEXT_STEP.getErrorCode(), ex.getErrorCode());
        assertEquals(expectedNextStep, ex.getParam(ARG_NEXT_STEP));
    }

    /**
     * Regression (false-negative) case: valid {@code nextOnError} + null {@code next} must pass analyze.
     * Before the fix the nextOnError branch validated {@code getNext()} (null here), so
     * {@code hasStep(null)} returned false and spuriously threw.
     */
    @Test
    public void nextOnError_valid_andNextNull_analyzePasses() {
        TaskStepModel src = step("src");
        src.setNextOnError("handler");
        TaskStepModel handler = step("handler");

        TaskFlowModel flow = newFlow(src, handler);

        assertDoesNotThrow(() -> new TaskFlowAnalyzer().analyze(flow));
    }

    /**
     * Regression (false-positive) case: invalid {@code nextOnError} (points to nonexistent step)
     * must be rejected even when {@code next} points to a valid step. Before the fix the
     * {@code nextOnError} branch validated {@code getNext()} and silently let invalid refs through.
     */
    @Test
    public void nextOnError_invalid_andNextValid_analyzeRejects() {
        TaskStepModel src = step("src");
        src.setNext("handler");
        src.setNextOnError("nonexistent");
        TaskStepModel handler = step("handler");

        TaskFlowModel flow = newFlow(src, handler);

        NopException ex = assertThrows(NopException.class, () -> new TaskFlowAnalyzer().analyze(flow));
        assertUnknownNextStep(ex, "nonexistent");
    }

    /**
     * nextOnError pointing at a valid step must pass even when nextOnError differs from next.
     */
    @Test
    public void nextOnError_valid_distinctFromNext_analyzePasses() {
        TaskStepModel src = step("src");
        src.setNext("normal");
        src.setNextOnError("handler");
        TaskStepModel normal = step("normal");
        TaskStepModel handler = step("handler");

        TaskFlowModel flow = newFlow(src, normal, handler);

        assertDoesNotThrow(() -> new TaskFlowAnalyzer().analyze(flow));
    }

    /**
     * Zero-regression for the {@code next} branch: valid {@code next} passes analyze.
     */
    @Test
    public void next_valid_analyzePasses() {
        TaskStepModel src = step("src");
        src.setNext("target");
        TaskStepModel target = step("target");

        TaskFlowModel flow = newFlow(src, target);

        assertDoesNotThrow(() -> new TaskFlowAnalyzer().analyze(flow));
    }

    /**
     * Zero-regression for the {@code next} branch: invalid {@code next} is rejected.
     */
    @Test
    public void next_invalid_analyzeRejects() {
        TaskStepModel src = step("src");
        src.setNext("nonexistent");

        TaskFlowModel flow = newFlow(src);

        NopException ex = assertThrows(NopException.class, () -> new TaskFlowAnalyzer().analyze(flow));
        assertUnknownNextStep(ex, "nonexistent");
    }
}
