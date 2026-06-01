/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.datastream.DataStreamSource;
import io.nop.stream.core.datastream.SingleOutputStreamOperatorImpl;
import io.nop.stream.core.execution.DeploymentMode;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.ICheckpointExecutorFactory;
import io.nop.stream.core.execution.IDeploymentPlanProvider;
import io.nop.stream.core.execution.IStreamExecutionDispatcher;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.Subtask;
import io.nop.stream.core.execution.SubtaskTask;
import io.nop.stream.core.execution.TaskExecutor;
import io.nop.stream.core.graph.PartitionedPlanGenerator;
import io.nop.stream.core.graph.StreamGraph;
import io.nop.stream.core.graph.StreamGraphGenerator;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobGraphGenerator;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.model.StreamBackendCapability;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.model.StreamRequirementValidator;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.SourceTransformation;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.*;

public class StreamExecutionEnvironment {

    private final List<Transformation<?>> transformations = new ArrayList<>();

    private int parallelism = 1;

    private long watermarkInterval = 200L;

    private boolean executed = false;

    private final CheckpointConfig checkpointConfig = new CheckpointConfig();

    private static ICheckpointExecutorFactory defaultCheckpointExecutorFactory;

    private ICheckpointExecutorFactory checkpointExecutorFactory;

    private DeploymentMode deploymentMode = DeploymentMode.LOCAL;

    private IStreamExecutionDispatcher executionDispatcher;

    public static StreamExecutionEnvironment getExecutionEnvironment() {
        return new StreamExecutionEnvironment();
    }

    /**
     * Creates a test environment with AT_LEAST_ONCE processing guarantee,
     * suitable for in-memory pipelines that use BEST_EFFORT/AT_LEAST_ONCE connectors.
     */
    public static StreamExecutionEnvironment createTestEnvironment() {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        env.getCheckpointConfig().setProcessingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE);
        return env;
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
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "parallelism").param(ARG_DETAIL, "must be at least 1");
        }
        this.parallelism = parallelism;
        return this;
    }

    public int getParallelism() {
        return parallelism;
    }

    public StreamExecutionEnvironment setWatermarkInterval(long watermarkInterval) {
        if (watermarkInterval < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "watermarkInterval").param(ARG_DETAIL, "must be >= 0");
        }
        this.watermarkInterval = watermarkInterval;
        return this;
    }

    public long getWatermarkInterval() {
        return watermarkInterval;
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

    public StreamExecutionEnvironment setDeploymentMode(DeploymentMode deploymentMode) {
        if (deploymentMode == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "deploymentMode");
        }
        this.deploymentMode = deploymentMode;
        return this;
    }

    public DeploymentMode getDeploymentMode() {
        return deploymentMode;
    }

    public StreamExecutionEnvironment setExecutionDispatcher(IStreamExecutionDispatcher dispatcher) {
        this.executionDispatcher = dispatcher;
        return this;
    }

    public IStreamExecutionDispatcher getExecutionDispatcher() {
        return executionDispatcher;
    }

    @SafeVarargs
    public final <T> DataStreamSource<T> fromElements(T... data) {
        if (data == null || data.length == 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "data").param(ARG_DETAIL, "needs at least one element");
        }
        return fromCollection(Arrays.asList(data));
    }

    public <T> DataStreamSource<T> fromCollection(Collection<T> data) {
        if (data == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "data");
        }
        if (data.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "data").param(ARG_DETAIL, "must not be empty");
        }
        SourceFunction<T> sourceFunction = new CollectionSourceFunction<>(data);
        return addSource(sourceFunction, "Collection Source");
    }

    public <T> DataStreamSource<T> fromCollection(Collection<T> data, TypeInformation<T> typeInfo) {
        if (data == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "data");
        }
        if (typeInfo == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "typeInfo");
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
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "A streaming job can only be executed once");
        }

        long startTime = System.currentTimeMillis();

        try {
            List<SinkTransformation<?>> sinks = findSinkTransformations();
            if (sinks.isEmpty()) {
                throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "No sinks found in the streaming job");
            }

            StreamModel streamModel = buildStreamModel(sinks);
            StreamRequirementValidator.validate(streamModel, StreamBackendCapability.localRuntime());
            StreamRequirementValidator.validateConnectorConsistency(
                    checkpointConfig.getProcessingGuarantee(),
                    streamModel.getSourceCapabilities(),
                    streamModel.getSinkCapabilities()
            );

            StreamGraphGenerator graphGenerator = new StreamGraphGenerator();
            @SuppressWarnings("unchecked")
            List<Transformation<?>> sinkList = (List<Transformation<?>>) (List<?>) sinks;
            StreamGraph streamGraph = graphGenerator.generate(sinkList);

            JobGraphGenerator jobGraphGenerator = new JobGraphGenerator();
            JobGraph jobGraph = jobGraphGenerator.generate(streamGraph);

            // Generate PartitionedPlan and DeploymentPlan for execution planning
            PartitionedPlanGenerator partitionedPlanGenerator = new PartitionedPlanGenerator();
            PartitionedPlan partitionedPlan = partitionedPlanGenerator.generate(
                    jobGraph, streamModel.computeFingerprint());
            DeploymentPlan deploymentPlan = generateDeploymentPlan(partitionedPlan);

            if (checkpointConfig.isCheckpointEnabled() && checkpointExecutorFactory != null) {
                StreamExecutionResult result = checkpointExecutorFactory.executeWithCheckpoint(
                    streamModel, partitionedPlan, deploymentPlan);
                executed = true;
                return result;
            }

            if (deploymentMode == DeploymentMode.DISTRIBUTED && executionDispatcher != null) {
                StreamExecutionResult result = executionDispatcher.execute(
                    jobGraph, partitionedPlan, deploymentPlan);
                executed = true;
                return result;
            }

            if (deploymentMode == DeploymentMode.DISTRIBUTED && executionDispatcher == null) {
                throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "DISTRIBUTED mode requires an IStreamExecutionDispatcher. "
                  + "Ensure the runtime module is on the classpath and the dispatcher has been configured.");
            }

            boolean barrierAlignment = checkpointConfig.getProcessingGuarantee().isBarrierAlignment();
            GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph, deploymentPlan, barrierAlignment);

            TaskExecutor executor = new TaskExecutor();
            List<SubtaskTask> subtaskTasks = new ArrayList<>();

            for (String vertexId : plan.getSortedVertexIds()) {
                List<Subtask> vertexSubtasks = plan.getSubtasks(vertexId);
                for (Subtask subtask : vertexSubtasks) {
                    SubtaskTask subtaskTask = new SubtaskTask(subtask, plan.getExecutionVertices().get(vertexId));
                    subtaskTasks.add(subtaskTask);
                    executor.submitTask(subtaskTask);
                }
            }

            executor.awaitCompletion();

            for (SubtaskTask task : subtaskTasks) {
                if (task.getState() == SubtaskTask.State.FAILED) {
                    throw new StreamException(ERR_STREAM_TASK_FAILED, task.getError());
                }
            }

            executed = true;
            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED, e).param(ARG_JOB_NAME, jobName);
        }
    }

    public String triggerSavepoint(String targetPath) throws Exception {
        if (checkpointExecutorFactory == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "No checkpoint executor factory registered. "
                  + "Ensure the runtime module is on the classpath and the factory has been set.");
        }

        JobGraph jobGraph = buildJobGraph("Savepoint Job");
        return checkpointExecutorFactory.triggerSavepoint(jobGraph, checkpointConfig, targetPath);
    }

    public StreamExecutionResult executeWithSavepoint(String savepointPath) throws Exception {
        if (executed) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "A streaming job can only be executed once");
        }

        if (checkpointExecutorFactory == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "No checkpoint executor factory registered. "
                  + "Ensure the runtime module is on the classpath and the factory has been set.");
        }

        if (savepointPath == null || savepointPath.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "savepointPath");
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
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "A streaming job can only be executed once");
        }

        if (checkpointExecutorFactory == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
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

    private StreamModel buildStreamModel(List<SinkTransformation<?>> sinks) {
        StreamComponents components = new StreamComponents();
        Map<String, Transformation<?>> transformMap = new LinkedHashMap<>();

        for (Transformation<?> t : transformations) {
            transformMap.put(String.valueOf(t.getId()), t);
        }

        return new StreamModel(components, transformMap);
    }

    /**
     * Generates a DeploymentPlan from a PartitionedPlan.
     * Uses a ServiceLoader-based approach to find the DeploymentPlanGenerator
     * in the runtime module, or creates a minimal local plan if unavailable.
     */
    private DeploymentPlan generateDeploymentPlan(PartitionedPlan partitionedPlan) {
        return IDeploymentPlanProvider.getProvider()
                .generateLocal(partitionedPlan);
    }

    private JobGraph buildJobGraph(String jobName) {
        List<SinkTransformation<?>> sinks = findSinkTransformations();
        if (sinks.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "No sinks found in the streaming job");
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
