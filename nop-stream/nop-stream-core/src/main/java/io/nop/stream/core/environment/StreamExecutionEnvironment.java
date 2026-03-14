/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.environment;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.functions.SinkFunction;

import io.nop.stream.core.common.typeinfo.TypeInformation;

import io.nop.stream.core.datastream.DataStreamSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The StreamExecutionEnvironment is the context in which a streaming program is executed.
 * It provides methods to control the job execution and to create data streams from various sources.
 * <p>
 * This is a simplified version based on Apache Flink's StreamExecutionEnvironment, designed
 * for single-JVM, synchronous execution without distributed features.
 * <p>
 * Example usage:
 * <pre>
 * StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
 * DataStreamSource<String> source = env.fromElements("a", "b", "c");
 * // ... add transformations
 * env.execute("My Job");
 * </pre>
 */
public class StreamExecutionEnvironment {

    /**
     * The list of transformations in this environment
     */
    private final List<Object> transformations = new ArrayList<>();

    /**
     * The default parallelism for operations
     */
    private int parallelism = 1;

    /**
     * Flag indicating if the job has been executed
     */
    private boolean executed = false;

    // ------------------------------------------------------------------------
    //  Factory Methods
    // ------------------------------------------------------------------------

    /**
     * Creates an execution environment that represents the context in which the
     * program is currently executed. This is a local environment by default.
     *
     * @return The execution environment of the context
     */
    public static StreamExecutionEnvironment getExecutionEnvironment() {
        return new StreamExecutionEnvironment();
    }

    /**
     * Creates a local execution environment with the specified parallelism.
     *
     * @param parallelism The parallelism for the environment
     * @return A local execution environment with the specified parallelism
     */
    public static StreamExecutionEnvironment createLocalEnvironment(int parallelism) {
        StreamExecutionEnvironment env = new StreamExecutionEnvironment();
        env.setParallelism(parallelism);
        return env;
    }

    // ------------------------------------------------------------------------
    //  Constructor
    // ------------------------------------------------------------------------

    /**
     * Creates a new StreamExecutionEnvironment.
     */
    public StreamExecutionEnvironment() {
        // Default constructor
    }

    // ------------------------------------------------------------------------
    //  Configuration
    // ------------------------------------------------------------------------

    /**
     * Sets the parallelism for operations executed through this environment.
     *
     * @param parallelism The parallelism
     * @return The execution environment
     */
    public StreamExecutionEnvironment setParallelism(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("Parallelism must be at least 1");
        }
        this.parallelism = parallelism;
        return this;
    }

    /**
     * Gets the parallelism with which operations are executed by default.
     *
     * @return The parallelism used by operations
     */
    public int getParallelism() {
        return parallelism;
    }

    // ------------------------------------------------------------------------
    //  Data Stream Creation - Collection Sources
    // ------------------------------------------------------------------------

    /**
     * Creates a new data stream that contains the given elements.
     * The elements must all be of the same type.
     * <p>
     * This creates a non-parallel data stream source by default (parallelism of one).
     *
     * @param data The array of elements to create the data stream from
     * @param <T>  The type of the returned data stream
     * @return The data stream representing the given array of elements
     */
    @SafeVarargs
    public final <T> DataStreamSource<T> fromElements(T... data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("fromElements needs at least one element as argument");
        }
        return fromCollection(Arrays.asList(data));
    }

    /**
     * Creates a new data stream from the given non-empty collection.
     * <p>
     * Note that this operation will result in a non-parallel data stream source,
     * i.e., a data stream source with parallelism one.
     *
     * @param data The collection of elements to create the data stream from
     * @param <T>  The generic type of the returned data stream
     * @return The data stream representing the given collection
     */
    public <T> DataStreamSource<T> fromCollection(Collection<T> data) {
        if (data == null) {
            throw new IllegalArgumentException("Collection must not be null");
        }
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Collection must not be empty");
        }

        // Create a source function from the collection
        SourceFunction<T> sourceFunction = new CollectionSourceFunction<>(data);

        // Add the source transformation
        return addSource(sourceFunction, "Collection Source");
    }

    /**
     * Creates a new data stream from the given non-empty collection with explicit type information.
     *
     * @param data     The collection of elements to create the data stream from
     * @param typeInfo The TypeInformation for the produced data stream
     * @param <T>      The type of the returned data stream
     * @return The data stream representing the given collection
     */
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

    /**
     * Adds a data source to the topology.
     *
     * @param function     The source function
     * @param sourceName   The name of the source
     * @param <T>          The type of the returned data stream
     * @return The data stream representing the source
     */
    public <T> DataStreamSource<T> addSource(SourceFunction<T> function, String sourceName) {
        // Create a simple DataStreamSource wrapper
        // In a full implementation, this would create a proper transformation
        SimpleDataStreamSource<T> source = new SimpleDataStreamSource<>(function, sourceName, this);
        transformations.add(source);
        return source;
    }

    /**
     * Adds a data source to the topology with explicit type information.
     *
     * @param function     The source function
     * @param sourceName   The name of the source
     * @param typeInfo     The type information for the output type
     * @param <T>          The type of the returned data stream
     * @return The data stream representing the source
     */
    public <T> DataStreamSource<T> addSource(SourceFunction<T> function, String sourceName, TypeInformation<T> typeInfo) {
        SimpleDataStreamSource<T> source = new SimpleDataStreamSource<>(function, sourceName, typeInfo, this);
        transformations.add(source);
        return source;
    }

    // ------------------------------------------------------------------------
    //  Execution
    // ------------------------------------------------------------------------

    /**
     * Triggers the program execution. The environment will execute all parts of
     * the program that have resulted in a "sink" operation.
     * <p>
     * This is a simplified synchronous execution. In a full implementation,
     * this would build the job graph and submit it to a cluster.
     *
     * @return The result of the job execution
     * @throws Exception If an error occurs during execution
     */
    public StreamExecutionResult execute() throws Exception {
        return execute("Streaming Job");
    }

    /**
     * Triggers the program execution with a job name.
     *
     * @param jobName The name of the job
     * @return The result of the job execution
     * @throws Exception If an error occurs during execution
     */
    public StreamExecutionResult execute(String jobName) throws Exception {
        if (executed) {
            throw new IllegalStateException("A streaming job can only be executed once");
        }

        long startTime = System.currentTimeMillis();

        try {
            // Execute each source transformation
            for (Object transformation : transformations) {
                if (transformation instanceof SimpleDataStreamSource) {
                    @SuppressWarnings("unchecked")
                    SimpleDataStreamSource<?> source = (SimpleDataStreamSource<?>) transformation;
                    executeSource(source);
                }
            }

            executed = true;
            long executionTime = System.currentTimeMillis() - startTime;
            return new StreamExecutionResult(jobName, executionTime);

        } catch (Exception e) {
            throw new RuntimeException("Failed to execute job: " + jobName, e);
        }
    }

    /**
     * Executes a single source by running its source function.
     *
     * @param source The source to execute
     * @param <T> The type of elements produced by the source
     * @throws Exception If execution fails
     */
    private <T> void executeSource(SimpleDataStreamSource<T> source) throws Exception {
        SourceFunction<T> sourceFunction = source.getSourceFunction();
        
        // Create a simple source context that collects elements
        SimpleSourceContext<T> ctx = new SimpleSourceContext<>();
        
        // Run the source function
        sourceFunction.run(ctx);
        
        // Cancel the source to ensure cleanup
        sourceFunction.cancel();
    }

    // ------------------------------------------------------------------------
    //  Internal Methods
    // ------------------------------------------------------------------------

    /**
     * Gets the list of transformations in this environment.
     *
     * @return The list of transformations
     */
    List<Object> getTransformations() {
        return transformations;
    }

    /**
     * Adds a transformation to the execution environment. This method is called by
     * DataStream operations to register their transformations in the execution plan.
     *
     * @param transformation the transformation to add
     */
    public void addTransformation(Object transformation) {
        transformations.add(transformation);
    }

    // ------------------------------------------------------------------------
    //  Inner Classes
    // ------------------------------------------------------------------------

    /**
     * A simple implementation of SourceFunction that emits elements from a collection.
     *
     * @param <T> The type of elements
     */
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
     * A simple DataStreamSource implementation for this environment.
     *
     * @param <T> The type of elements
     */
    private static class SimpleDataStreamSource<T> implements DataStreamSource<T> {
        private static final long serialVersionUID = 1L;

        private final SourceFunction<T> sourceFunction;
        private final String name;
        private final TypeInformation<T> typeInfo;
        private final StreamExecutionEnvironment environment;

        @SuppressWarnings("unchecked")
        SimpleDataStreamSource(SourceFunction<T> sourceFunction, String name, StreamExecutionEnvironment environment) {
            this.sourceFunction = sourceFunction;
            this.name = name;
            this.typeInfo = null; // Would infer type in real implementation
            this.environment = environment;
        }

        SimpleDataStreamSource(SourceFunction<T> sourceFunction, String name, TypeInformation<T> typeInfo, StreamExecutionEnvironment environment) {
            this.sourceFunction = sourceFunction;
            this.name = name;
            this.typeInfo = typeInfo;
            this.environment = environment;
        }

        @Override
        public TypeInformation<T> getType() {
            return typeInfo;
        }

        @Override
        public <K> io.nop.stream.core.datastream.KeyedStream<T, K> keyBy(io.nop.stream.core.common.functions.KeySelector<T, K> key) {
            return new io.nop.stream.core.datastream.KeyedStreamImpl<>(this, key);
        }

        @Override
        public <R> io.nop.stream.core.datastream.SingleOutputStreamOperator<R> transform(
                String operatorName,
                TypeInformation<R> outTypeInfo,
                io.nop.stream.core.operators.OneInputStreamOperator<T, R> operator) {
            // Simplified - would add transformation in full implementation
            return null;
        }



        public <R> io.nop.stream.core.datastream.SingleOutputStreamOperator<R> map(
                io.nop.stream.core.common.functions.MapFunction<T, R> mapper) {
            // Simplified - would add map transformation in full implementation
            return null;
        }

        public io.nop.stream.core.datastream.SingleOutputStreamOperator<T> filter(
                io.nop.stream.core.common.functions.FilterFunction<T> filter) {
            // Simplified - would add filter transformation in full implementation
            return null;
        }

        public <R> io.nop.stream.core.datastream.SingleOutputStreamOperator<R> flatMap(
                io.nop.stream.core.common.functions.FlatMapFunction<T, R> flatMapper) {
            // Simplified - would add flatMap transformation in full implementation
            return null;
        }

        @Override
        public void sink(SinkFunction<T> sinkFunction) {
            // Simplified - would add sink operation in full implementation
        }

        @Override
        public void collect(SinkFunction<T> collectorFunction) {
            // Simplified - would add collect operation in full implementation
        }

        @Override
        public void print() {
            // Simplified - would add print operation in full implementation
        }

        @Override
        public void print(SinkFunction<T> toStringFunction) {
            // Simplified - would add print operation in full implementation
        }

        @Override
        public SimpleDataStreamSource<T> forceNonParallel() {
            return this;
        }

        public SourceFunction<T> getSourceFunction() {
            return sourceFunction;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * A simple implementation of SourceContext that collects elements from sources.
     *
     * @param <T> The type of elements
     */
    private static class SimpleSourceContext<T> implements SourceFunction.SourceContext<T> {
        private static final long serialVersionUID = 1L;

        @Override
        public void collect(T element) {
            // In a full implementation, this would forward to downstream operators
            // For now, we just collect the elements (could be used by sinks)
        }

        @Override
        public void collectWithTimestamp(T element, long timestamp) {
            // Collect with timestamp - simplified implementation
            collect(element);
        }

        @Override
        public void emitWatermark(long mark) {
            // Watermarks are not used in simplified execution
        }

        @Override
        public void markAsTemporarilyIdle() {
            // Not used in simplified execution
        }

        @Override
        public long getProcessingTime() {
            return System.currentTimeMillis();
        }
    }

}
