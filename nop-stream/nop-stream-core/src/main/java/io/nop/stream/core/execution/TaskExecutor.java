/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.jobgraph.JobVertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the execution of streaming tasks using a thread pool.
 *
 * <p>TaskExecutor is responsible for:
 * <ul>
 *   <li>Managing a pool of worker threads for task execution</li>
 *   <li>Submitting tasks for execution based on JobVertex parallelism</li>
 *   <li>Tracking running and completed tasks</li>
 *   <li>Providing graceful shutdown mechanism</li>
 * </ul>
 *
 * <p><strong>Task Execution Model:</strong>
 * <ol>
 *   <li>Each JobVertex can have multiple parallel task instances (determined by parallelism)</li>
 *   <li>For each JobVertex, the executor creates one Task instance per parallel subtask</li>
 *   <li>Tasks are submitted to the thread pool and executed concurrently</li>
 *   <li>The executor tracks all submitted tasks and their completion status</li>
 * </ol>
 *
 * <p><strong>Thread Pool Management:</strong>
 * <ul>
 *   <li>Thread pool size is configurable (default: number of available processors)</li>
 *   <li>Tasks are queued if all threads are busy</li>
 *   <li>Shutdown is graceful: waits for running tasks to complete</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> TaskExecutor is thread-safe and can handle
 * concurrent task submissions from multiple threads.
 *
 * @see Task
 * @see JobVertex
 */
public class TaskExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(TaskExecutor.class);

    /**
     * Default thread pool size based on available processors.
     */
    private static final int DEFAULT_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    /**
     * The underlying thread pool for executing tasks.
     */
    private final ExecutorService executorService;

    /**
     * Map of all submitted tasks, keyed by a unique task identifier.
     */
    private final Map<String, Task> submittedTasks;

    /**
     * Map of task futures for tracking execution status.
     */
    private final Map<String, Future<?>> taskFutures;

    /**
     * Flag indicating whether the executor has been shut down.
     */
    private final AtomicBoolean isShutdown;

    /**
     * Creates a TaskExecutor with the default thread pool size.
     */
    public TaskExecutor() {
        this(DEFAULT_POOL_SIZE);
    }

    /**
     * Creates a TaskExecutor with the specified thread pool size.
     *
     * @param poolSize the number of threads in the pool (must be positive)
     * @throws IllegalArgumentException if poolSize is not positive
     */
    public TaskExecutor(int poolSize) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("Pool size must be positive, got: " + poolSize);
        }

        this.executorService = Executors.newFixedThreadPool(poolSize);
        this.submittedTasks = new ConcurrentHashMap<>();
        this.taskFutures = new ConcurrentHashMap<>();
        this.isShutdown = new AtomicBoolean(false);

        LOG.info("TaskExecutor created with pool size: {}", poolSize);
    }

    /**
     * Submits all parallel tasks for a JobVertex and returns the list of submitted tasks.
     *
     * <p>This method creates one Task instance per parallel subtask of the JobVertex
     * and submits them to the thread pool for execution. The tasks are tracked
     * internally and can be queried later.
     *
     * @param jobVertex the JobVertex to execute (must not be null)
     * @return unmodifiable list of submitted tasks
     * @throws IllegalArgumentException if jobVertex is null
     * @throws IllegalStateException if executor has been shut down
     */
    public List<Task> submitJobVertex(JobVertex jobVertex) {
        if (jobVertex == null) {
            throw new IllegalArgumentException("JobVertex cannot be null");
        }

        if (isShutdown.get()) {
            throw new IllegalStateException("TaskExecutor has been shut down");
        }

        LOG.info("Submitting JobVertex: {} with parallelism {}",
            jobVertex.getName(), jobVertex.getParallelism());

        List<Task> tasks = new ArrayList<>();
        int parallelism = jobVertex.getParallelism();

        // Create and submit one task per parallel instance
        for (int i = 0; i < parallelism; i++) {
            Task task = new Task(jobVertex, i);
            String taskId = generateTaskId(jobVertex.getId(), i);

            submittedTasks.put(taskId, task);
            Future<?> future = executorService.submit(task);
            taskFutures.put(taskId, future);

            tasks.add(task);

            LOG.debug("Submitted task: {}", task.getTaskName());
        }

        LOG.info("Submitted {} tasks for JobVertex: {}", tasks.size(), jobVertex.getName());

        return Collections.unmodifiableList(tasks);
    }

    /**
     * Submits a single task for execution.
     *
     * @param task the task to submit (must not be null)
     * @return the submitted task
     * @throws IllegalArgumentException if task is null
     * @throws IllegalStateException if executor has been shut down
     */
    public Task submitTask(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }

        if (isShutdown.get()) {
            throw new IllegalStateException("TaskExecutor has been shut down");
        }

        String taskId = generateTaskId(task.getJobVertex().getId(), task.getTaskIndex());
        submittedTasks.put(taskId, task);
        Future<?> future = executorService.submit(task);
        taskFutures.put(taskId, future);

        LOG.info("Submitted task: {}", task.getTaskName());

        return task;
    }

    /**
     * Gets a task by its vertex ID and task index.
     *
     * @param vertexId the vertex ID
     * @param taskIndex the task index
     * @return the task, or null if not found
     */
    public Task getTask(String vertexId, int taskIndex) {
        String taskId = generateTaskId(vertexId, taskIndex);
        return submittedTasks.get(taskId);
    }

    /**
     * Gets all submitted tasks.
     *
     * @return unmodifiable map of task ID to task
     */
    public Map<String, Task> getAllTasks() {
        return Collections.unmodifiableMap(submittedTasks);
    }

    /**
     * Gets the number of submitted tasks.
     *
     * @return the number of submitted tasks
     */
    public int getTaskCount() {
        return submittedTasks.size();
    }

    /**
     * Gets the number of running tasks.
     *
     * @return the number of tasks in RUNNING state
     */
    public int getRunningTaskCount() {
        return (int) submittedTasks.values().stream()
            .filter(task -> task.getState() == Task.State.RUNNING)
            .count();
    }

    /**
     * Gets the number of completed tasks.
     *
     * @return the number of tasks in COMPLETED state
     */
    public int getCompletedTaskCount() {
        return (int) submittedTasks.values().stream()
            .filter(task -> task.getState() == Task.State.COMPLETED)
            .count();
    }

    /**
     * Gets the number of failed tasks.
     *
     * @return the number of tasks in FAILED state
     */
    public int getFailedTaskCount() {
        return (int) submittedTasks.values().stream()
            .filter(task -> task.getState() == Task.State.FAILED)
            .count();
    }

    /**
     * Waits for all submitted tasks to complete.
     *
     * <p>This method blocks until all tasks have finished (either completed, failed, or canceled).
     * It does not shut down the executor, allowing new tasks to be submitted afterwards.
     *
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public void awaitCompletion() throws InterruptedException {
        LOG.info("Waiting for {} tasks to complete", submittedTasks.size());

        for (Map.Entry<String, Future<?>> entry : taskFutures.entrySet()) {
            try {
                entry.getValue().get();
            } catch (Exception e) {
                LOG.debug("Task {} completed with exception", entry.getKey(), e);
            }
        }

        LOG.info("All tasks completed");
    }

    /**
     * Waits for all submitted tasks to complete with a timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return true if all tasks completed, false if timeout elapsed
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        LOG.info("Waiting for {} tasks to complete (timeout: {} {})",
            submittedTasks.size(), timeout, unit);

        long startTime = System.nanoTime();
        long timeoutNanos = unit.toNanos(timeout);

        for (Map.Entry<String, Future<?>> entry : taskFutures.entrySet()) {
            long remainingNanos = timeoutNanos - (System.nanoTime() - startTime);
            if (remainingNanos <= 0) {
                LOG.warn("Timeout elapsed while waiting for tasks to complete");
                return false;
            }

            try {
                entry.getValue().get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (Exception e) {
                LOG.debug("Task {} completed with exception", entry.getKey(), e);
            }
        }

        LOG.info("All tasks completed within timeout");
        return true;
    }

    /**
     * Shuts down the executor gracefully.
     *
     * <p>This method:
     * <ol>
     *   <li>Prevents new tasks from being submitted</li>
     *   <li>Waits for currently running tasks to complete</li>
     *   <li>Does not interrupt running tasks</li>
     * </ol>
     */
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            LOG.info("Shutting down TaskExecutor");
            executorService.shutdown();
        }
    }

    /**
     * Shuts down the executor immediately.
     *
     * <p>This method:
     * <ol>
     *   <li>Prevents new tasks from being submitted</li>
     *   <li>Attempts to stop all running tasks by interrupting them</li>
     *   <li>Returns list of tasks that were waiting to execute</li>
     * </ol>
     *
     * @return list of tasks that were not started
     */
    public List<Runnable> shutdownNow() {
        if (isShutdown.compareAndSet(false, true)) {
            LOG.info("Shutting down TaskExecutor immediately");
            return executorService.shutdownNow();
        }
        return Collections.emptyList();
    }

    /**
     * Waits for the executor to terminate after shutdown.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return true if executor terminated, false if timeout elapsed
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executorService.awaitTermination(timeout, unit);
    }

    /**
     * Checks if the executor has been shut down.
     *
     * @return true if the executor has been shut down
     */
    public boolean isShutdown() {
        return isShutdown.get();
    }

    /**
     * Checks if all tasks have completed following shutdown.
     *
     * @return true if all tasks have completed
     */
    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    /**
     * Generates a unique task identifier.
     *
     * @param vertexId the vertex ID
     * @param taskIndex the task index
     * @return unique task identifier in format "vertexId-taskIndex"
     */
    private String generateTaskId(String vertexId, int taskIndex) {
        return vertexId + "-" + taskIndex;
    }

    @Override
    public String toString() {
        return "TaskExecutor{" +
            "totalTasks=" + submittedTasks.size() +
            ", running=" + getRunningTaskCount() +
            ", completed=" + getCompletedTaskCount() +
            ", failed=" + getFailedTaskCount() +
            ", isShutdown=" + isShutdown.get() +
            '}';
    }
}
