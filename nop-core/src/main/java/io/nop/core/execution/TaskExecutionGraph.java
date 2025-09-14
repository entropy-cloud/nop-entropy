package io.nop.core.execution;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.model.graph.dag.Dag;
import io.nop.core.model.graph.dag.DagNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public class TaskExecutionGraph implements IExecution<Void> {
    static final Logger LOG = LoggerFactory.getLogger(TaskExecutionGraph.class);
    private final String taskGraphName;

    private final Map<String, IExecution<?>> tasks = new LinkedHashMap<>();
    private final Dag dag = new Dag(Dag.DEFAULT_ROOT_NAME);

    private final Executor executor;

    private boolean analyzed;

    public TaskExecutionGraph(Executor executor, String taskGraphName) {
        this.taskGraphName = taskGraphName;
        this.executor = executor;
    }

    void checkAllowChange() {
        if (analyzed)
            throw new IllegalStateException("nop.task.graph.not-allow-change");
    }

    public TaskExecutionGraph addTask(String taskName, IExecution<?> task) {
        checkAllowChange();
        Guard.checkArgument(!tasks.containsKey(taskName), "duplicate task name");
        this.tasks.put(taskName, task);
        this.dag.addNextNode(Dag.DEFAULT_ROOT_NAME, taskName);
        return this;
    }

    public boolean isRootNode(String taskName) {
        return Dag.DEFAULT_ROOT_NAME.equals(taskName);
    }

    public boolean containsTask(String taskName) {
        return tasks.containsKey(taskName);
    }

    public TaskExecutionGraph addTaskWithDepends(String taskName, IExecution<?> task, Collection<String> depends) {
        return addTask(taskName, task).addDepends(taskName, depends);
    }

    public TaskExecutionGraph addDepends(String taskName, Collection<String> depends) {
        checkAllowChange();
        if (depends == null || depends.isEmpty())
            return this;

        for (String depend : depends) {
            dag.addNextNode(Dag.DEFAULT_ROOT_NAME, depend);
            dag.addNextNode(depend, taskName);
        }
        return this;
    }

    public Set<String> getDepends(String taskName) {
        DagNode node = dag.getNode(taskName);
        return node == null ? null : node.getPrevNodeNames();
    }

    public TaskExecutionGraph addDepend(String taskName, String depend) {
        checkAllowChange();
        Guard.notEmpty(taskName, "taskName");
        Guard.notEmpty(depend, "depend");

        dag.addNextNode(depend, taskName);
        return this;
    }

    public synchronized TaskExecutionGraph analyze() {
        if (!analyzed) {
            dag.analyze();
            analyzed = true;
        }
        return this;
    }

    @Override
    public CompletableFuture<Void> executeAsync(ICancelToken cancelToken) {
        analyze();

        long beginTime = CoreMetrics.currentTimeMillis();

        Map<String, CompletableFuture<Void>> futures = new HashMap<>();
        for (String taskName : tasks.keySet()) {
            futures.put(taskName, new CompletableFuture<>());
        }

        Set<String> noDepends = new LinkedHashSet<>();
        for (String taskName : tasks.keySet()) {
            CompletableFuture<Void> future = waitPrevTasks(futures, taskName);
            if (future != null) {
                future.whenComplete((ret, err) -> {
                    if (err != null) {
                        futures.get(taskName).completeExceptionally(err);
                    } else {
                        runTask(executor, cancelToken, futures, taskName);
                    }
                });
            } else {
                noDepends.add(taskName);
            }
        }

        for (String taskName : noDepends) {
            runTask(executor, cancelToken, futures, taskName);
        }

        CompletableFuture<?>[] endFutures = new CompletableFuture[dag.getEndNodeNames().size()];
        int index = 0;
        for (String endNode : dag.getEndNodeNames()) {
            endFutures[index++] = futures.get(endNode);
        }
        return CompletableFuture.allOf(endFutures).whenComplete((ret, err) -> {
            LOG.info("nop.task.graph-execute-finished:taskGraphName={}, usedTime={}",
                    taskGraphName, CoreMetrics.currentTimeMillis() - beginTime);
        });
    }

    private CompletableFuture<Void> waitPrevTasks(Map<String, CompletableFuture<Void>> futures, String taskName) {
        if (!tasks.containsKey(taskName))
            return null;

        DagNode node = dag.getNode(taskName);
        if(node == null)
            return null;

        Set<String> prevNames = node.getPrevNodeNames();
        if (prevNames == null || prevNames.isEmpty())
            return null;

        if (prevNames.contains(Dag.DEFAULT_ROOT_NAME)) {
            if (prevNames.size() == 1)
                return null;

            prevNames = new HashSet<>(prevNames);
            prevNames.retainAll(futures.keySet());
            if (prevNames.isEmpty())
                return null;
        }

        LOG.debug("nop.task.wait-prev:taskName={},prevNames={}", taskName, prevNames);
        if (prevNames.size() == 1)
            return futures.get(prevNames.iterator().next());

        CompletableFuture<?>[] prevFutures = new CompletableFuture[prevNames.size()];
        int index = 0;
        for (String prevName : prevNames) {
            prevFutures[index++] = futures.get(prevName);
        }
        return CompletableFuture.allOf(prevFutures);
    }

    private void runTask(Executor executor, ICancelToken cancelToken, Map<String, CompletableFuture<Void>> futures, String taskName) {
        CompletableFuture<Void> future = futures.get(taskName);
        executor.execute(() -> {
            if (cancelToken != null) {
                if (cancelToken.isCancelled()) {
                    LOG.info("nop.task.skip-cancelled:taskName={}", taskName);
                    future.cancel(false);
                    return;
                }
            }
            LOG.info("nop.task.run.start:taskName={}", taskName);
            long beginTime = CoreMetrics.currentTimeMillis();
            try {
                CompletionStage<?> promise = tasks.get(taskName).executeAsync(cancelToken);
                if (promise == null)
                    promise = FutureHelper.success(null);

                promise.whenComplete((ret, err) -> {
                    if (err != null) {
                        LOG.error("nop.task.run.error:taskName={}", taskName, err);
                        future.completeExceptionally(err);
                    } else {
                        LOG.info("nop.task.run.finish:taskName={},usedTime={}", taskName, CoreMetrics.currentTimeMillis() - beginTime);
                        if (LOG.isDebugEnabled())
                            LOG.debug("nop.task.unfinished-count:{}", getUnfinishedCount(futures));
                        future.complete(null);
                    }
                });

            } catch (Exception e) {
                LOG.error("nop.task.run.error:taskName={}", taskName, e);
                future.completeExceptionally(e);
            }
        });
    }

    private int getUnfinishedCount(Map<String, CompletableFuture<Void>> futures) {
        int count = 0;
        for (Map.Entry<String, CompletableFuture<Void>> entry : futures.entrySet()) {
            CompletableFuture<Void> future = entry.getValue();
            if (!future.isDone())
                count++;
        }
        return count;
    }
}
