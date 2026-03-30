/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.environment;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.datastream.DataStreamSource;
import io.nop.stream.core.datastream.SingleOutputStreamOperatorImpl;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.ChainingOutput;
import io.nop.stream.core.operators.Input;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.transformation.PartitionTransformation;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.SourceTransformation;
import io.nop.stream.core.transformation.Transformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The StreamExecutionEnvironment is the context in which a streaming program is executed.
 * It provides methods to create data streams from sources and to execute the pipeline.
 *
 * <p>Execution model: single-JVM, synchronous, single-threaded. The transformation DAG is
 * walked from sinks back to sources, operators are instantiated and wired together via
 * {@link ChainingOutput}, and the source is run to push data through the chain.
 */
public class StreamExecutionEnvironment {

    private final List<Transformation<?>> transformations = new ArrayList<>();

    private int parallelism = 1;

    private boolean executed = false;

    // ------------------------------------------------------------------------
    //  Factory Methods
    // ------------------------------------------------------------------------

    public static StreamExecutionEnvironment getExecutionEnvironment() {
        return new StreamExecutionEnvironment();
    }

    public static StreamExecutionEnvironment createLocalEnvironment(int parallelism) {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        env.setParallelism(parallelism);
        return env;
    }

    public StreamExecutionEnvironment() {
    }

    // ------------------------------------------------------------------------
    //  Configuration
    // ------------------------------------------------------------------------

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

    // ------------------------------------------------------------------------
    //  Data Stream Creation
    // ------------------------------------------------------------------------

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

    // ------------------------------------------------------------------------
    //  Source Addition
    // ------------------------------------------------------------------------

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

    // ------------------------------------------------------------------------
    //  Execution
    // ------------------------------------------------------------------------

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

            for (SinkTransformation<?> sink : sinks) {
                executePipeline(sink);
            }

            executed = true;
            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute job: " + jobName, e);
        }
    }

    // ------------------------------------------------------------------------
    //  Transformation Registration
    // ------------------------------------------------------------------------

    public void addTransformation(Transformation<?> transformation) {
        transformations.add(transformation);
    }

    List<Transformation<?>> getTransformations() {
        return transformations;
    }

    // ------------------------------------------------------------------------
    //  Pipeline Execution Internals
    // ------------------------------------------------------------------------

    private List<SinkTransformation<?>> findSinkTransformations() {
        List<SinkTransformation<?>> sinks = new ArrayList<>();
        for (Transformation<?> t : transformations) {
            if (t instanceof SinkTransformation) {
                sinks.add((SinkTransformation<?>) t);
            }
        }
        return sinks;
    }

    /**
     * Walks the transformation chain from sink back to source, builds the operator chain,
     * wires them with {@link ChainingOutput}, and runs the source.
     */
    private void executePipeline(SinkTransformation<?> sinkTransform) throws Exception {
        List<Transformation<?>> chain = buildTransformationChain(sinkTransform);
        List<Object> operators = instantiateOperators(chain);
        wireOperatorChain(operators);
        runSource(operators);
    }

    /**
     * Builds an ordered list of transformations from source to sink by walking
     * backwards from the sink through input references. Skips PartitionTransformations
     * for single-threaded execution.
     */
    private List<Transformation<?>> buildTransformationChain(SinkTransformation<?> sink) {
        List<Transformation<?>> chain = new ArrayList<>();
        Transformation<?> current = sink;
        while (current != null) {
            if (current instanceof PartitionTransformation) {
                current = ((PartitionTransformation<?>) current).getInput();
                continue;
            }
            chain.add(0, current);
            List<Transformation<?>> inputs = current.getInputs();
            if (inputs.isEmpty()) {
                break;
            }
            current = inputs.get(0);
        }
        return chain;
    }

    /**
     * Creates operator instances from the transformation chain.
     */
    private List<Object> instantiateOperators(List<Transformation<?>> chain) {
        List<Object> operators = new ArrayList<>();
        for (Transformation<?> t : chain) {
            if (t instanceof SourceTransformation) {
                operators.add(new StreamSourceOperator<>(((SourceTransformation<?>) t).getSourceFunction()));
            } else if (t instanceof OneInputTransformation) {
                io.nop.stream.core.operator.SimpleStreamOperatorFactory simpleFactory =
                        (io.nop.stream.core.operator.SimpleStreamOperatorFactory)
                                ((OneInputTransformation<?, ?>) t).getOperatorFactory();
                if (simpleFactory != null) {
                    operators.add(simpleFactory.getRawOperator());
                } else {
                    operators.add(((OneInputTransformation<?, ?>) t).getOperatorFactory().createStreamOperator(null));
                }
            } else if (t instanceof SinkTransformation) {
                operators.add(new StreamSinkOperator<>(((SinkTransformation<?>) t).getSinkFunction()));
            }
        }
        return operators;
    }

    /**
     * Wires operators together: sets each operator's {@code output} field (except the sink)
     * to a {@link ChainingOutput} that forwards to the next operator.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void wireOperatorChain(List<Object> operators) throws Exception {
        for (int i = 0; i < operators.size() - 1; i++) {
            Object current = operators.get(i);
            Object next = operators.get(i + 1);

            if (current instanceof AbstractStreamOperator) {
                AbstractStreamOperator<?> currentOp = (AbstractStreamOperator<?>) current;
                Input<?> nextInput = (Input<?>) next;
                currentOp.setOutput(new ChainingOutput(nextInput));
            }
        }
    }

    /**
     * Opens all operators (tail-to-head order) and then runs the source operator.
     */
    private void runSource(List<Object> operators) throws Exception {
        for (int i = operators.size() - 1; i >= 0; i--) {
            Object op = operators.get(i);
            if (op instanceof StreamOperator) {
                ((StreamOperator<?>) op).open();
            }
        }

        Object head = operators.get(0);
        if (head instanceof StreamSourceOperator) {
            ((StreamSourceOperator<?>) head).run();
        }
    }

    // ------------------------------------------------------------------------
    //  Inner Classes
    // ------------------------------------------------------------------------

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

    /**
     * A DataStreamSource implementation backed by a SourceTransformation and the
     * real DataStreamImpl chain. Delegates map/filter/flatMap/sink to DataStreamImpl.
     */
    private static class SourceDataStream<T> extends SingleOutputStreamOperatorImpl<T>
            implements DataStreamSource<T> {

        private static final long serialVersionUID = 1L;

        SourceDataStream(StreamExecutionEnvironment environment, Transformation<T> transformation) {
            super(environment, transformation);
        }
    }
}
