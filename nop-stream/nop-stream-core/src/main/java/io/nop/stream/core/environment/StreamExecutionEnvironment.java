/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.environment;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.datastream.DataStreamSource;
import io.nop.stream.core.datastream.SingleOutputStreamOperatorImpl;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.ICheckpointExecutorFactory;
import io.nop.stream.core.execution.Task;
import io.nop.stream.core.execution.TaskExecutor;
import io.nop.stream.core.graph.StreamGraph;
import io.nop.stream.core.graph.StreamGraphGenerator;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobGraphGenerator;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.SourceTransformation;
import io.nop.stream.core.transformation.Transformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class StreamExecutionEnvironment {

    private final List<Transformation<?>> transformations = new ArrayList<>();

    private int parallelism = 1;

    private boolean executed = false;

    private final CheckpointConfig checkpointConfig = new CheckpointConfig();

    private static ICheckpointExecutorFactory defaultCheckpointExecutorFactory;

    private ICheckpointExecutorFactory checkpointExecutorFactory;

    public static StreamExecutionEnvironment getExecutionEnvironment() {
        return new StreamExecutionEnvironment();
    }

    public static StreamExecutionEnvironment createLocalEnvironment(int parallelism) {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        env.setParallelism(parallelism);
        return env;
    }

    public StreamExecutionEnvironment() {
        this.checkpointExecutorFactory = defaultCheckpointExecutorFactory;
    }

    public StreamExecutionEnvironment setParallelism(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("Parallelism must be at least 1");
        }
        this.parallelism = parallelism;
        return this;
    }

    public int getParallelism() {
        return parallelism;
    }

    public CheckpointConfig getCheckpointConfig() {
        return checkpointConfig;
    }

    public StreamExecutionEnvironment enableCheckpointing(long interval) {
        checkpointConfig.setCheckpointEnabled(true);
        checkpointConfig.setCheckpointInterval(interval);
        return this;
    }

    public static void setCheckpointExecutorFactory(ICheckpointExecutorFactory factory) {
        defaultCheckpointExecutorFactory = factory;
    }

    public ICheckpointExecutorFactory getCheckpointExecutorFactory() {
        return checkpointExecutorFactory;
    }

    @SafeVarargs
    public final <T> DataStreamSource<T> fromElements(T... data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("fromElements needs at least one element as argument");
        }
        return fromCollection(Arrays.asList(data));
    }

    public <T> DataStreamSource<T> fromCollection(Collection<T> data) {
        if (data == null) {
            throw new IllegalArgumentException("Collection must not be null");
        }
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Collection must not be empty");
        }
        SourceFunction<T> sourceFunction = new CollectionSourceFunction<>(data);
        return addSource(sourceFunction, "Collection Source");
    }

    public <T> DataStreamSource<T> fromCollection(Collection<T> data, TypeInformation<T> typeInfo) {
        if (data == null) {
            throw new IllegalArgumentException("Collection must not be null");
        }
        if (typeInfo == null) {
            throw new IllegalArgumentException("TypeInformation must not be null");
        }
        SourceFunction<T> sourceFunction = new CollectionSourceFunction<>(data);
        return addSource(sourceFunction, "Collection Source", typeInfo);
    }

    public <T> DataStreamSource<T> addSource(SourceFunction<T> function, String sourceName) {
        SourceTransformation<T> sourceTransform = new SourceTransformation<>(
                sourceName, function, null, parallelism);
        transformations.add(sourceTransform);
        return new SourceDataStream<>(this, sourceTransform);
    }

    public <T> DataStreamSource<T> addSource(SourceFunction<T> function, String sourceName,
                                              TypeInformation<T> typeInfo) {
        SourceTransformation<T> sourceTransform = new SourceTransformation<>(
                sourceName, function, typeInfo, parallelism);
        transformations.add(sourceTransform);
        return new SourceDataStream<>(this, sourceTransform);
    }

    public StreamExecutionResult execute() throws Exception {
        return execute("Streaming Job");
    }

    public StreamExecutionResult execute(String jobName) throws Exception {
        if (executed) {
            throw new IllegalStateException("A streaming job can only be executed once");
        }

        long startTime = System.currentTimeMillis();

        try {
            List<SinkTransformation<?>> sinks = findSinkTransformations();
            if (sinks.isEmpty()) {
                throw new IllegalStateException("No sinks found in the streaming job");
            }

            StreamGraphGenerator graphGenerator = new StreamGraphGenerator();
            @SuppressWarnings("unchecked")
            List<Transformation<?>> sinkList = (List<Transformation<?>>) (List<?>) sinks;
            StreamGraph streamGraph = graphGenerator.generate(sinkList);

            JobGraphGenerator jobGraphGenerator = new JobGraphGenerator();
            JobGraph jobGraph = jobGraphGenerator.generate(streamGraph);

            if (checkpointConfig.isCheckpointEnabled() && checkpointExecutorFactory != null) {
                StreamExecutionResult result = checkpointExecutorFactory.executeWithCheckpoint(
                    jobGraph, jobName, checkpointConfig);
                executed = true;
                return result;
            }

            GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph);

            TaskExecutor executor = new TaskExecutor();
            List<Task> tasks = new ArrayList<>();

            for (String vertexId : plan.getSortedVertexIds()) {
                JobVertex vertex = plan.getExecutionVertices().get(vertexId);
                Task task = new Task(vertex, 0);
                tasks.add(task);
                executor.submitTask(task);
            }

            executor.awaitCompletion();

            for (Task task : tasks) {
                if (task.getState() == Task.State.FAILED) {
                    throw new RuntimeException("Task failed", task.getError());
                }
            }

            executed = true;
            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute job: " + jobName, e);
        }
    }

    public String triggerSavepoint(String targetPath) throws Exception {
        if (checkpointExecutorFactory == null) {
            throw new IllegalStateException(
                    "No checkpoint executor factory registered. "
                  + "Ensure the runtime module is on the classpath and the factory has been set.");
        }

        JobGraph jobGraph = buildJobGraph("Savepoint Job");
        return checkpointExecutorFactory.triggerSavepoint(jobGraph, checkpointConfig, targetPath);
    }

    public StreamExecutionResult executeWithSavepoint(String savepointPath) throws Exception {
        if (executed) {
            throw new IllegalStateException("A streaming job can only be executed once");
        }

        if (checkpointExecutorFactory == null) {
            throw new IllegalStateException(
                    "No checkpoint executor factory registered. "
                  + "Ensure the runtime module is on the classpath and the factory has been set.");
        }

        if (savepointPath == null || savepointPath.isEmpty()) {
            throw new IllegalArgumentException("Savepoint path must not be null or empty");
        }

        JobGraph jobGraph = buildJobGraph("Streaming Job (Savepoint Recovery)");
        StreamExecutionResult result = checkpointExecutorFactory.executeWithSavepoint(
                jobGraph, "Streaming Job (Savepoint Recovery)", checkpointConfig, savepointPath);
        executed = true;
        return result;
    }

    public StreamExecutionResult execute(String jobName, String savepointPath) throws Exception {
        if (savepointPath == null || savepointPath.isEmpty()) {
            return execute(jobName);
        }
        if (executed) {
            throw new IllegalStateException("A streaming job can only be executed once");
        }

        if (checkpointExecutorFactory == null) {
            throw new IllegalStateException(
                    "No checkpoint executor factory registered. "
                  + "Ensure the runtime module is on the classpath and the factory has been set.");
        }

        JobGraph jobGraph = buildJobGraph(jobName);
        StreamExecutionResult result = checkpointExecutorFactory.executeWithSavepoint(
                jobGraph, jobName, checkpointConfig, savepointPath);
        executed = true;
        return result;
    }

    public void addTransformation(Transformation<?> transformation) {
        transformations.add(transformation);
    }

    List<Transformation<?>> getTransformations() {
        return transformations;
    }

    private List<SinkTransformation<?>> findSinkTransformations() {
        List<SinkTransformation<?>> sinks = new ArrayList<>();
        for (Transformation<?> t : transformations) {
            if (t instanceof SinkTransformation) {
                sinks.add((SinkTransformation<?>) t);
            }
        }
        return sinks;
    }

    private JobGraph buildJobGraph(String jobName) {
        List<SinkTransformation<?>> sinks = findSinkTransformations();
        if (sinks.isEmpty()) {
            throw new IllegalStateException("No sinks found in the streaming job");
        }

        if (!checkpointConfig.isCheckpointEnabled()) {
            checkpointConfig.setCheckpointEnabled(true);
        }

        StreamGraphGenerator graphGenerator = new StreamGraphGenerator();
        @SuppressWarnings("unchecked")
        List<Transformation<?>> sinkList = (List<Transformation<?>>) (List<?>) sinks;
        StreamGraph streamGraph = graphGenerator.generate(sinkList);

        JobGraphGenerator jobGraphGenerator = new JobGraphGenerator();
        return jobGraphGenerator.generate(streamGraph);
    }

    private static class CollectionSourceFunction<T> implements SourceFunction<T> {
        private static final long serialVersionUID = 1L;

        private final Collection<T> data;
        private volatile boolean isRunning = true;

        CollectionSourceFunction(Collection<T> data) {
            this.data = data;
        }

        @Override
        public void run(SourceContext<T> ctx) throws Exception {
            for (T element : data) {
                if (!isRunning) {
                    break;
                }
                ctx.collect(element);
            }
        }

        @Override
        public void cancel() {
            isRunning = false;
        }
    }

    private static class SourceDataStream<T> extends SingleOutputStreamOperatorImpl<T>
            implements DataStreamSource<T> {

        private static final long serialVersionUID = 1L;

        SourceDataStream(StreamExecutionEnvironment environment, Transformation<T> transformation) {
            super(environment, transformation);
        }
    }
}
