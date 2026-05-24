package io.nop.stream.core.execution;

import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.environment.StreamExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TestSubtaskExecution {

    @Test
    public void testParallelism2_runsMultipleSubtasks() throws Exception {
        AtomicInteger subtaskCount = new AtomicInteger(0);
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.fromElements("a", "b").map(x -> {
            subtaskCount.incrementAndGet();
            return x;
        }).sink(x -> {});
        env.execute("parallelism2-test");

        assertTrue(subtaskCount.get() >= 2,
                "Expected at least 2 invocations (one per subtask), got: " + subtaskCount.get());
    }

    @Test
    public void testParallelism1_backwardCompat() throws Exception {
        List<String> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(1);
        env.fromElements("x", "y").map(String::toUpperCase).sink(results::add);
        env.execute("parallelism1-test");

        assertEquals(Arrays.asList("X", "Y"), results);
    }

    @Test
    public void testDistributed_withoutDispatcher_throws() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.fromElements("a").sink(x -> {});

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> env.execute("test"));
        assertTrue(ex.getCause().getMessage().contains("DISTRIBUTED mode requires an IStreamExecutionDispatcher"));
    }

    @Test
    public void testSubtaskTask_lifecycle() throws Exception {
        List<io.nop.stream.core.jobgraph.OperatorChain> chains = Collections.singletonList(
                new io.nop.stream.core.jobgraph.OperatorChain(
                        Collections.singletonList(new StubOp())));
        io.nop.stream.core.jobgraph.JobVertex vertex = new io.nop.stream.core.jobgraph.JobVertex(
                "v1", "test", 1, chains, (io.nop.stream.core.jobgraph.Invokable<Void>) () -> {});
        io.nop.stream.core.checkpoint.TaskLocation loc = new io.nop.stream.core.checkpoint.TaskLocation(
                "job", "pipe", "v1", 0);

        Subtask subtask = new Subtask("v1", 0, loc, new StreamTaskInvokable(chains.get(0)));
        SubtaskTask task = new SubtaskTask(subtask, vertex);

        assertEquals(SubtaskTask.State.CREATED, task.getState());
        task.run();
        assertEquals(SubtaskTask.State.COMPLETED, task.getState());
        assertNull(task.getError());
        assertTrue(task.isFinished());
    }

    @Test
    public void testSubtaskTask_failed() {
        io.nop.stream.core.jobgraph.OperatorChain chain =
                new io.nop.stream.core.jobgraph.OperatorChain(
                        Collections.singletonList(new StubOp()));
        io.nop.stream.core.checkpoint.TaskLocation loc = new io.nop.stream.core.checkpoint.TaskLocation(
                "job", "pipe", "v1", 0);

        StreamTaskInvokable failInvokable = new StreamTaskInvokable(chain) {
            @Override
            public void invoke() throws Exception {
                throw new RuntimeException("test failure");
            }
        };
        Subtask subtask = new Subtask("v1", 0, loc, failInvokable);

        io.nop.stream.core.jobgraph.JobVertex vertex = new io.nop.stream.core.jobgraph.JobVertex(
                "v1", "test", 1,
                Collections.singletonList(chain), failInvokable);

        SubtaskTask task = new SubtaskTask(subtask, vertex);
        task.run();
        assertEquals(SubtaskTask.State.FAILED, task.getState());
        assertNotNull(task.getError());
        assertTrue(task.getError().getMessage().contains("test failure"));
    }

    private static class StubOp implements io.nop.stream.core.operators.StreamOperator<Object> {
        @Override public void open() throws Exception {}
        @Override public void finish() throws Exception {}
        @Override public void close() throws Exception {}
        @Override public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {}
        @Override public void setKeyContextElement1(io.nop.stream.core.streamrecord.StreamRecord<?> record) throws Exception {}
        @Override public void setKeyContextElement2(io.nop.stream.core.streamrecord.StreamRecord<?> record) throws Exception {}
        @Override public void notifyCheckpointComplete(long checkpointId) throws Exception {}
        @Override public void setCurrentKey(Object key) {}
        @Override public Object getCurrentKey() { return null; }
    }
}
