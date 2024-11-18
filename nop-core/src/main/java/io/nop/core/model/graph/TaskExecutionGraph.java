package io.nop.core.model.graph;

import io.nop.api.core.time.CoreMetrics;
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
import java.util.concurrent.Executor;

public class TaskExecutionGraph {
    static final Logger LOG = LoggerFactory.getLogger(TaskExecutionGraph.class);
    private final String taskGraphName;

    private final Map<String, Runnable> tasks = new LinkedHashMap<>();
    private final Dag dag = new Dag(Dag.DEFAULT_ROOT_NAME);

    public TaskExecutionGraph(String taskGraphName) {
        this.taskGraphName = taskGraphName;
    }

    public TaskExecutionGraph addTask(String taskName, Runnable task) {
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

    public TaskExecutionGraph addTaskWithDepends(String taskName, Runnable task, Collection<String> depends) {
        return addTask(taskName, task).addDepends(taskName, depends);
    }

    public TaskExecutionGraph addDepends(String taskName, Collection<String> depends) {
        if (depends == null || depends.isEmpty())
            return this;

        for (String depend : depends) {
            dag.addNextNode(Dag.DEFAULT_ROOT_NAME, depend);
            dag.addNextNode(depend, taskName);
        }
        return this;
    }

    public Set<String> getDepends(String taskName) {
        return dag.getNode(taskName).getPrevNodeNames();
    }

    public TaskExecutionGraph addDepend(String taskName, String depend) {
        Guard.notEmpty(taskName, "taskName");
        Guard.notEmpty(depend, "depend");

        dag.addNextNode(depend, taskName);
        return this;
    }

    public TaskExecutionGraph analyze() {
        dag.analyze();
        return this;
    }

    public CompletableFuture<Void> runOnExecutor(Executor executor, ICancelToken cancelToken) {
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
                    future.complete(null);
                    return;
                }
            }
            LOG.info("nop.task.run.start:taskName={}", taskName);
            long beginTime = CoreMetrics.currentTimeMillis();
            try {
                tasks.get(taskName).run();
                LOG.info("nop.task.run.finish:taskName={},usedTime={}", taskName, CoreMetrics.currentTimeMillis() - beginTime);
                if (LOG.isDebugEnabled())
                    LOG.debug("nop.task.unfinished-count:{}", getUnfinishedCount(futures));
                future.complete(null);
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
