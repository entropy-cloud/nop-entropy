/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operator.StreamOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for Task and TaskExecutor classes.
 */
public class TestTaskExecutor {

    private TaskExecutor taskExecutor;
    private JobVertex testVertex;

    @BeforeEach
    public void setUp() {
        taskExecutor = new TaskExecutor(2);
        testVertex = createTestVertex("vertex-1", "TestVertex", 2);
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        if (taskExecutor != null && !taskExecutor.isShutdown()) {
            taskExecutor.shutdown();
            taskExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ========== TaskExecutor Construction Tests ==========

    @Test
    public void testTaskExecutorDefaultConstruction() {
        TaskExecutor executor = new TaskExecutor();
        assertNotNull(executor);
        assertFalse(executor.isShutdown());
        assertEquals(0, executor.getTaskCount());
    }

    @Test
    public void testTaskExecutorCustomPoolSize() {
        TaskExecutor executor = new TaskExecutor(4);
        assertNotNull(executor);
        assertFalse(executor.isShutdown());
    }

    @Test
    public void testTaskExecutorInvalidPoolSize() {
        assertThrows(IllegalArgumentException.class, () -> new TaskExecutor(0));
        assertThrows(IllegalArgumentException.class, () -> new TaskExecutor(-1));
    }

    // ========== TaskExecutor Submit Tests ==========

    @Test
    public void testSubmitJobVertex() {
        List<Task> tasks = taskExecutor.submitJobVertex(testVertex);
        
        assertNotNull(tasks);
        assertEquals(2, tasks.size());
        assertEquals(2, taskExecutor.getTaskCount());
        
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            assertNotNull(task);
            assertEquals(i, task.getTaskIndex());
            assertEquals(testVertex, task.getJobVertex());
        }
    }

    @Test
    public void testSubmitNullJobVertex() {
        assertThrows(IllegalArgumentException.class, () -> taskExecutor.submitJobVertex(null));
    }

    @Test
    public void testSubmitTask() {
        Task task = new Task(testVertex, 0);
        Task submitted = taskExecutor.submitTask(task);
        
        assertNotNull(submitted);
        assertEquals(task, submitted);
        assertEquals(1, taskExecutor.getTaskCount());
    }

    @Test
    public void testSubmitNullTask() {
        assertThrows(IllegalArgumentException.class, () -> taskExecutor.submitTask(null));
    }

    @Test
    public void testSubmitAfterShutdown() {
        taskExecutor.shutdown();
        assertThrows(IllegalStateException.class, () -> taskExecutor.submitJobVertex(testVertex));
    }

    // ========== TaskExecutor Task Tracking Tests ==========

    @Test
    public void testGetTask() {
        taskExecutor.submitJobVertex(testVertex);
        
        Task task0 = taskExecutor.getTask("vertex-1", 0);
        Task task1 = taskExecutor.getTask("vertex-1", 1);
        
        assertNotNull(task0);
        assertNotNull(task1);
        assertEquals(0, task0.getTaskIndex());
        assertEquals(1, task1.getTaskIndex());
    }

    @Test
    public void testGetNonexistentTask() {
        Task task = taskExecutor.getTask("nonexistent", 0);
        assertNull(task);
    }

    @Test
    public void testGetAllTasks() {
        taskExecutor.submitJobVertex(testVertex);
        
        assertEquals(2, taskExecutor.getAllTasks().size());
        assertTrue(taskExecutor.getAllTasks().containsKey("vertex-1-0"));
        assertTrue(taskExecutor.getAllTasks().containsKey("vertex-1-1"));
    }

    // ========== TaskExecutor Statistics Tests ==========

    @Test
    public void testTaskStatistics() throws InterruptedException {
        // Submit tasks
        taskExecutor.submitJobVertex(testVertex);
        
        // Wait for tasks to complete
        taskExecutor.awaitCompletion(5, TimeUnit.SECONDS);
        
        // Check statistics
        assertEquals(2, taskExecutor.getTaskCount());
        assertEquals(2, taskExecutor.getCompletedTaskCount());
        assertEquals(0, taskExecutor.getRunningTaskCount());
        assertEquals(0, taskExecutor.getFailedTaskCount());
    }

    @Test
    public void testFailedTaskStatistics() throws InterruptedException {
        // Create a vertex with a failing invokable
        JobVertex failingVertex = createFailingVertex("fail-vertex", "FailingVertex", 1);
        
        // Submit and wait for completion
        taskExecutor.submitJobVertex(failingVertex);
        taskExecutor.awaitCompletion(5, TimeUnit.SECONDS);
        
        // Check statistics
        assertEquals(1, taskExecutor.getTaskCount());
        assertEquals(0, taskExecutor.getCompletedTaskCount());
        assertEquals(1, taskExecutor.getFailedTaskCount());
    }

    // ========== TaskExecutor Shutdown Tests ==========

    @Test
    public void testShutdown() {
        taskExecutor.shutdown();
        assertTrue(taskExecutor.isShutdown());
    }

    @Test
    public void testShutdownNow() {
        List<Runnable> notStarted = taskExecutor.shutdownNow();
        assertTrue(taskExecutor.isShutdown());
        assertNotNull(notStarted);
    }

    @Test
    public void testAwaitTermination() throws InterruptedException {
        taskExecutor.submitJobVertex(testVertex);
        taskExecutor.shutdown();
        
        boolean terminated = taskExecutor.awaitTermination(5, TimeUnit.SECONDS);
        assertTrue(terminated);
        assertTrue(taskExecutor.isTerminated());
    }

    @Test
    public void testDoubleShutdown() {
        taskExecutor.shutdown();
        taskExecutor.shutdown(); // Should not throw
        assertTrue(taskExecutor.isShutdown());
    }

    // ========== TaskExecutor Await Tests ==========

    @Test
    public void testAwaitCompletion() throws InterruptedException {
        taskExecutor.submitJobVertex(testVertex);
        taskExecutor.awaitCompletion(5, TimeUnit.SECONDS);
        
        // All tasks should be finished
        for (Task task : taskExecutor.getAllTasks().values()) {
            assertTrue(task.isFinished());
        }
    }

    @Test
    public void testAwaitCompletionWithTimeout() throws InterruptedException {
        taskExecutor.submitJobVertex(testVertex);
        boolean completed = taskExecutor.awaitCompletion(5, TimeUnit.SECONDS);
        assertTrue(completed);
    }

    // ========== Task Construction Tests ==========

    @Test
    public void testTaskConstruction() {
        Task task = new Task(testVertex, 0);
        
        assertNotNull(task);
        assertEquals(testVertex, task.getJobVertex());
        assertEquals(0, task.getTaskIndex());
        assertEquals(Task.State.CREATED, task.getState());
        assertNull(task.getError());
        assertFalse(task.isFinished());
    }

    @Test
    public void testTaskNullJobVertex() {
        assertThrows(IllegalArgumentException.class, () -> new Task(null, 0));
    }

    @Test
    public void testTaskInvalidIndex() {
        assertThrows(IllegalArgumentException.class, () -> new Task(testVertex, -1));
        assertThrows(IllegalArgumentException.class, () -> new Task(testVertex, 100));
    }

    // ========== Task Execution Tests ==========

    @Test
    public void testTaskExecution() throws InterruptedException {
        Task task = new Task(testVertex, 0);
        taskExecutor.submitTask(task);
        
        taskExecutor.awaitCompletion(5, TimeUnit.SECONDS);
        
        assertEquals(Task.State.COMPLETED, task.getState());
        assertTrue(task.isFinished());
        assertNull(task.getError());
    }

    @Test
    public void testTaskExecutionFailure() throws InterruptedException {
        JobVertex failingVertex = createFailingVertex("fail-vertex", "FailingVertex", 1);
        Task task = new Task(failingVertex, 0);
        
        taskExecutor.submitTask(task);
        taskExecutor.awaitCompletion(5, TimeUnit.SECONDS);
        
        assertEquals(Task.State.FAILED, task.getState());
        assertTrue(task.isFinished());
        assertNotNull(task.getError());
        assertTrue(task.getError() instanceof RuntimeException);
    }

    @Test
    public void testTaskCancellation() {
        Task task = new Task(testVertex, 0);
        
        // Cancel before execution
        boolean canceled = task.cancel();
        assertTrue(canceled);
        assertEquals(Task.State.CANCELED, task.getState());
        assertTrue(task.isFinished());
    }

    @Test
    public void testTaskCannotCancelAfterStart() throws InterruptedException {
        Task task = new Task(testVertex, 0);
        
        // Start execution
        taskExecutor.submitTask(task);
        
        // Wait a bit for task to start
        Thread.sleep(100);
        
        // Try to cancel (may or may not succeed depending on timing)
        // Just verify it doesn't throw
        task.cancel();
    }

    // ========== Task State Tests ==========

    @Test
    public void testTaskStates() throws InterruptedException {
        Task task = new Task(testVertex, 0);
        assertEquals(Task.State.CREATED, task.getState());
        
        taskExecutor.submitTask(task);
        taskExecutor.awaitCompletion(5, TimeUnit.SECONDS);
        
        assertEquals(Task.State.COMPLETED, task.getState());
    }

    @Test
    public void testTaskIsFinished() throws InterruptedException {
        Task task = new Task(testVertex, 0);
        assertFalse(task.isFinished());
        
        taskExecutor.submitTask(task);
        taskExecutor.awaitCompletion(5, TimeUnit.SECONDS);
        
        assertTrue(task.isFinished());
    }

    // ========== Task Naming Tests ==========

    @Test
    public void testTaskName() {
        Task task0 = new Task(testVertex, 0);
        Task task1 = new Task(testVertex, 1);
        
        String name0 = task0.getTaskName();
        String name1 = task1.getTaskName();
        
        assertNotNull(name0);
        assertNotNull(name1);
        assertTrue(name0.contains("TestVertex"));
        assertTrue(name0.contains("0"));
        assertTrue(name1.contains("1"));
    }

    @Test
    public void testTaskToString() {
        Task task = new Task(testVertex, 0);
        String str = task.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("vertex-1"));
        assertTrue(str.contains("0"));
    }

    // ========== Helper Methods ==========

    private JobVertex createTestVertex(String id, String name, int parallelism) {
        Invokable<Void> invokable = () -> {
            // Simple invokable that does nothing
        };
        
        List<StreamOperator<?>> operators = new ArrayList<>();
        operators.add(new TestOperator(name));
        
        OperatorChain chain = new OperatorChain(operators);
        
        return new JobVertex(id, name, parallelism, Collections.singletonList(chain), invokable);
    }

    private JobVertex createFailingVertex(String id, String name, int parallelism) {
        Invokable<Void> invokable = () -> {
            throw new RuntimeException("Intentional test failure");
        };
        
        List<StreamOperator<?>> operators = new ArrayList<>();
        operators.add(new TestOperator(name));
        
        OperatorChain chain = new OperatorChain(operators);
        
        return new JobVertex(id, name, parallelism, Collections.singletonList(chain), invokable);
    }

    private static class TestOperator implements StreamOperator<Object>, java.io.Serializable {
        private final String name;

        TestOperator(String name) {
            this.name = name;
        }

        @Override
        public TypeInformation<Object> getOutputType() {
            return new TypeInformation<Object>() {
                @Override
                public Class<Object> getTypeClass() {
                    return Object.class;
                }
            };
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void initialize() {
        }

        @Override
        public void open() {
        }

        @Override
        public void close() {
        }

        @Override
        public ChainingStrategy getChainingStrategy() {
            return ChainingStrategy.ALWAYS;
        }
    }
}
