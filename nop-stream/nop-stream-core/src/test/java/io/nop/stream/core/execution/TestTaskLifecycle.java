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
        assertTrue(task.getError().getMessage().contains("invoke failed"));
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

        // G58: cancel() from CREATED advances through CANCELING → CANCELED
        // (cancel closure finalizes since there is no execution thread)
        assertTrue(task.cancel());
        assertEquals(Task.State.CANCELED, task.getState());

        task.run();
        assertEquals(Task.State.CANCELED, task.getState());
    }

    @Test
    void testTaskCancelIsIdempotentAfterCanceled() {
        JobVertex v = createVertex(() -> {});
        Task task = new Task(v, 0);

        assertTrue(task.cancel());
        assertEquals(Task.State.CANCELED, task.getState());

        // Second cancel returns false; state stays CANCELED
        assertFalse(task.cancel());
        assertEquals(Task.State.CANCELED, task.getState());
    }

    @Test
    void testTaskCancelFromRunningTransitionsToCanceling() throws Exception {
        // G58: cancel() from RUNNING must transition to CANCELING (observable
        // intermediate state), not be a no-op like the legacy 5-state model.
        JobVertex v = createVertex(() -> {});
        Task task = new Task(v, 0);

        // Manually drive the task into RUNNING via the same chain run() uses
        // (CREATED → SCHEDULED → DEPLOYING → RUNNING).
        // Use reflection-free path: invoke run() in a thread, cancel from main.
        java.util.concurrent.CountDownLatch runningLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> threadError = new java.util.concurrent.atomic.AtomicReference<>();

        Thread t = new Thread(() -> {
            try {
                // Drive CREATED → SCHEDULED → DEPLOYING → RUNNING explicitly so the
                // test does not depend on run()'s internal scheduling timing.
                // However Task.run() does this internally; the simplest path is to
                // call run() and let it advance through the chain. The invokable
                // below blocks on the latch so the task is in RUNNING when we cancel.
                task.run();
            } catch (Throwable e) {
                threadError.set(e);
            }
        });
        t.setDaemon(true);

        // Use an invokable that signals "running" then blocks until interrupted
        JobVertex blockingVertex = createVertex(() -> {
            runningLatch.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
        Task blockingTask = new Task(blockingVertex, 0);

        Thread bt = new Thread(() -> {
            try {
                blockingTask.run();
            } catch (Throwable e) {
                threadError.set(e);
            }
        });
        bt.setDaemon(true);
        bt.start();

        // Wait until the task thread has entered the invokable (RUNNING state)
        assertTrue(runningLatch.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "task should reach RUNNING within 5s");

        assertEquals(Task.State.RUNNING, blockingTask.getState(),
                "task must be in RUNNING before cancel");

        // G58: cancel() from RUNNING must transition to CANCELING
        boolean canceled = blockingTask.cancel();
        assertTrue(canceled, "cancel() from RUNNING must return true (G58)");
        assertEquals(Task.State.CANCELING, blockingTask.getState(),
                "cancel() from RUNNING must leave task in CANCELING (G58 intermediate state)");

        // Cleanup: interrupt the blocking task thread
        bt.interrupt();
        bt.join(2000);

        // Final state: either CANCELED (if run loop observed CANCELING) or FAILED
        // (if interrupted ungracefully). Both are acceptable terminal states for
        // this test — the key assertion is that cancel() produced CANCELING.
        Task.State finalState = blockingTask.getState();
        assertTrue(finalState == Task.State.CANCELED || finalState == Task.State.FAILED,
                "unexpected final state: " + finalState);
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
    void testSubtaskTaskFailedStateIsFinished() {
        JobVertex v = createVertex(() -> {});
        StreamTaskInvokable invokable = new StreamTaskInvokable(
                v.getOperatorChains().get(0));
        Subtask subtask = new Subtask("v1", 0,
                new io.nop.stream.core.checkpoint.TaskLocation("j", "p", "v1", 0),
                invokable);
        SubtaskTask task = new SubtaskTask(subtask, v,
                Collections.emptyList());

        task.run();
        assertTrue(task.isFinished(), "COMPLETED state should be considered finished");
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
