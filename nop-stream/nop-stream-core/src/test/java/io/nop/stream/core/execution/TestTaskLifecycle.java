package io.nop.stream.core.execution;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamMap;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static org.junit.jupiter.api.Assertions.*;

class TestTaskLifecycle {

    private JobVertex createVertex(Invokable<?> invokable) {
        OperatorChain chain = new OperatorChain(Collections.singletonList(
                new StreamMap<>(x -> x)));
        return new JobVertex("test-vertex", "test", 1,
                Collections.singletonList(chain), invokable);
    }

    @Test
    void testTaskCompletesWithCAS() {
        AtomicReference<String> invoked = new AtomicReference<>("no");
        JobVertex v = createVertex(() -> invoked.set("yes"));
        Task task = new Task(v, 0);

        task.run();

        assertEquals(Task.State.COMPLETED, task.getState());
        assertEquals("yes", invoked.get());
        assertNull(task.getError());
    }

    @Test
    void testTaskFailsOnError() {
        JobVertex v = createVertex(() -> {
            throw new StreamException(ARG_DETAIL).param(ARG_DETAIL, "invoke failed");
        });
        Task task = new Task(v, 0);

        task.run();

        assertEquals(Task.State.FAILED, task.getState());
        assertNotNull(task.getError());
        assertEquals("invoke failed", task.getError().getMessage());
    }

    @Test
    void testTaskCannotRunTwice() {
        JobVertex v = createVertex(() -> {});
        Task task = new Task(v, 0);

        task.run();
        assertEquals(Task.State.COMPLETED, task.getState());

        task.run();
        assertEquals(Task.State.COMPLETED, task.getState());
    }

    @Test
    void testTaskCancelBeforeRun() {
        JobVertex v = createVertex(() -> {});
        Task task = new Task(v, 0);

        assertTrue(task.cancel());
        assertEquals(Task.State.CANCELED, task.getState());

        task.run();
        assertEquals(Task.State.CANCELED, task.getState());
    }

    @Test
    void testSubtaskTaskIsFinishedExcludesCanceling() {
        JobVertex v = createVertex(() -> {});
        StreamTaskInvokable invokable = new StreamTaskInvokable(
                v.getOperatorChains().get(0));
        Subtask subtask = new Subtask("v1", 0,
                new io.nop.stream.core.checkpoint.TaskLocation("j", "p", "v1", 0),
                invokable);
        SubtaskTask task = new SubtaskTask(subtask, v,
                Collections.emptyList());

        assertFalse(task.isFinished(), "CREATED should not be finished");
    }

    @Test
    void testSubtaskTaskIsFinishedForCompleted() throws Exception {
        JobVertex v = createVertex(() -> {});
        StreamTaskInvokable invokable = new StreamTaskInvokable(
                v.getOperatorChains().get(0));
        Subtask subtask = new Subtask("v1", 0,
                new io.nop.stream.core.checkpoint.TaskLocation("j", "p", "v1", 0),
                invokable);
        SubtaskTask task = new SubtaskTask(subtask, v,
                Collections.emptyList());

        task.run();
        assertTrue(task.isFinished());
        assertEquals(SubtaskTask.State.COMPLETED, task.getState());
    }

    @Test
    void testSubtaskTaskIsFinishedForFailed() {
        JobVertex v = createVertex(() -> {
            throw new StreamException(ARG_DETAIL).param(ARG_DETAIL, "fail");
        });
        OperatorChain chain = v.getOperatorChains().get(0);
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        Subtask subtask = new Subtask("v1", 0,
                new io.nop.stream.core.checkpoint.TaskLocation("j", "p", "v1", 0),
                invokable);
        SubtaskTask task = new SubtaskTask(subtask, v,
                Collections.emptyList());

        task.run();
        assertTrue(task.isFinished());
        assertEquals(SubtaskTask.State.FAILED, task.getState());
    }

    @Test
    void testSubtaskTaskCancelingIsNotFinished() {
        SubtaskTask.State[] states = SubtaskTask.State.values();
        for (SubtaskTask.State s : states) {
            if (s == SubtaskTask.State.CANCELING) {
                JobVertex v = createVertex(() -> {});
                StreamTaskInvokable invokable = new StreamTaskInvokable(
                        v.getOperatorChains().get(0));
                Subtask subtask = new Subtask("v1", 0,
                        new io.nop.stream.core.checkpoint.TaskLocation("j", "p", "v1", 0),
                        invokable);
                SubtaskTask task = new SubtaskTask(subtask, v,
                        Collections.emptyList());
                task.cancel();
                if (task.getState() == SubtaskTask.State.CANCELING) {
                    assertFalse(task.isFinished(),
                            "CANCELING should not be considered finished");
                }
            }
        }
    }
}
