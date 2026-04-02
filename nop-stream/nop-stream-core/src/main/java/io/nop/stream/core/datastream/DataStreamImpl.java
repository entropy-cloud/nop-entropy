/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.common.functions.*;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.common.functions.sink.PrintSinkFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operator.SimpleStreamOperatorFactory;
import io.nop.stream.core.operator.StreamOperatorFactory;
import io.nop.stream.core.operator.StreamOperator;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.StreamFilter;
import io.nop.stream.core.operators.StreamFlatMap;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.transformation.PartitionTransformation;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.Transformation;

import java.io.Serializable;

/**
 * The implementation of the {@link DataStream} interface. This class represents a data stream
 * in the streaming program and provides methods to apply transformations to the stream.
 * 
 * <p>Each DataStreamImpl instance holds a reference to the execution environment and a
 * transformation that represents the current state of the stream. When operations are applied,
 * new transformations are created and registered with the environment.
 * 
 * <p>This class follows the builder pattern where each operation returns a new DataStream
 * instance, allowing for method chaining.
 * 
 * @param <T> The type of the elements in this stream
 */
public class DataStreamImpl<T> implements DataStream<T>, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * The execution environment that this data stream belongs to
     */
    protected final StreamExecutionEnvironment environment;
    
    /**
     * The transformation that produces this data stream
     */
    protected final Transformation<T> transformation;
    
    /**
     * Creates a new DataStreamImpl with the specified environment and transformation.
     * 
     * @param environment the execution environment
     * @param transformation the transformation that produces this stream
     */
    public DataStreamImpl(StreamExecutionEnvironment environment, Transformation<T> transformation) {
        this.environment = environment;
        this.transformation = transformation;
    }
    
    /**
     * Gets the type information for the elements in this stream.
     * 
     * @return the type information for the elements
     */
    @Override
    public TypeInformation<T> getType() {
        return transformation.getOutputType();
    }
    
    /**
     * Applies a transformation to the data stream. This is the low-level method for adding
     * operators to the stream. All higher-level operations (map, filter, flatMap) use this
     * method internally.
     * 
     * @param operatorName the name of the operator for logging and debugging
     * @param outTypeInfo the type information for the output elements
     * @param operator the operator to apply to the stream
     * @param <R> the type of the output elements
     * @return a new SingleOutputStreamOperator representing the transformed stream
     */
    @Override
    public <R> SingleOutputStreamOperator<R> transform(
            String operatorName,
            TypeInformation<R> outTypeInfo,
            OneInputStreamOperator<T, R> operator) {
        
        // Create the transformation
        StreamOperatorFactory<R> operatorFactory = new SimpleStreamOperatorFactory<R>(
            operator, operatorName, environment.getParallelism());
        OneInputTransformation<T, R> transform = new OneInputTransformation<>(
            this.transformation,
            operatorName,
            operatorFactory,
            outTypeInfo,
            environment.getParallelism()
        );
        
        // Register the transformation with the environment
        environment.addTransformation(transform);
        
        // Return a new DataStream wrapping the new transformation
        return new SingleOutputStreamOperatorImpl<>(environment, transform);
    }
    
    /**
     * Applies a map transformation to this data stream. The map function is called for each
     * element in the stream and produces exactly one output element for each input element.
     * 
     * @param mapper the map function to apply
     * @param <R> the type of the output elements
     * @return a new data stream with the mapped elements
     */
    public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper) {
        return transform(
            "Map",
            (TypeInformation<R>) UnknownTypeInformation.INSTANCE,
            new StreamMap<>(mapper)
        );
    }
    
    /**
     * Applies a filter transformation to this data stream. The filter function is called for
     * each element and decides whether to keep or discard the element.
     * 
     * @param filter the filter function to apply
     * @return a new data stream with only the elements that passed the filter
     */
    public SingleOutputStreamOperator<T> filter(FilterFunction<T> filter) {
        return transform(
            "Filter",
            getType(),
            new StreamFilter<>(filter)
        );
    }
    
    /**
     * Applies a flat map transformation to this data stream. The flat map function is called
     * for each element and can produce zero, one, or more output elements for each input element.
     * 
     * @param flatMapper the flat map function to apply
     * @param <R> the type of the output elements
     * @return a new data stream with the flat mapped elements
     */
    public <R> SingleOutputStreamOperator<R> flatMap(FlatMapFunction<T, R> flatMapper) {
        return transform(
            "FlatMap",
            (TypeInformation<R>) UnknownTypeInformation.INSTANCE,
            new StreamFlatMap<>(flatMapper)
        );
    }
    
    /**
     * Creates a new {@link KeyedStream} that uses the provided key for partitioning its
     * operator states.
     * 
     * @param key the KeySelector to be used for extracting the key for partitioning
     * @param <K> the type of the key
     * @return the {@link KeyedStream} with partitioned state
     */
    @Override
    public <K> KeyedStream<T, K> keyBy(KeySelector<T, K> key) {
        // Create a partition transformation based on the key selector
        // For now, we use a simple hash partitioner
        IPartitioner<T> partitioner = new KeySelectorPartitioner<>(key);
        
        PartitionTransformation<T> partitionTransform = new PartitionTransformation<>(
            this.transformation,
            "KeyBy",
            partitioner,
            key,
            getType(),
            environment.getParallelism()
        );
        
        // Register the transformation
        environment.addTransformation(partitionTransform);
        
        // Return a new KeyedStream (simplified - would need KeyedStreamImpl)
        return new KeyedStreamImpl<>(environment, partitionTransform, key);
    }
    
    /**
     * Prints the elements of the DataStream to the standard output.
     */
    @Override
    public void print() {
        print(new PrintSinkFunction<>());
    }
    
    /**
     * Prints the elements of the DataStream to the standard output using a custom sink function.
     */
    @Override
    public void print(SinkFunction<T> sinkFunction) {
        sink(sinkFunction);
    }
    
    /**
     * Collects the elements of the DataStream using a collector function.
     */
    @Override
    public void collect(SinkFunction<T> collectorFunction) {
        sink(collectorFunction);
    }
    
    /**
     * Sends the elements of the DataStream to a sink function.
     */
    @Override
    public void sink(SinkFunction<T> sinkFunction) {
        // Create a sink transformation
        SinkTransformation<T> sinkTransform = new SinkTransformation<>(
            this.transformation,
            "Sink",
            sinkFunction,
            null, // Void type for sinks
            environment.getParallelism()
        );
        
        // Register the sink transformation
        environment.addTransformation(sinkTransform);
    }
    
    /**
     * Gets the execution environment for this data stream.
     * 
     * @return the execution environment
     */
    public StreamExecutionEnvironment getEnvironment() {
        return environment;
    }
    
    /**
     * Gets the transformation that produces this data stream.
     * 
     * @return the transformation
     */
    public Transformation<T> getTransformation() {
        return transformation;
    }
    
    /**
     * A partitioner that uses a KeySelector to extract keys for partitioning.
     * 
     * @param <T> the type of elements being partitioned
     */
    private static class KeySelectorPartitioner<T> implements IPartitioner<T>, Serializable {
        private static final long serialVersionUID = 1L;
        
        private final KeySelector<T, ?> keySelector;
        
        KeySelectorPartitioner(KeySelector<T, ?> keySelector) {
            this.keySelector = keySelector;
        }
        
        @Override
        public int partition(T value, int numPartitions) {
            try {
                Object key = keySelector.getKey(value);
                return Math.abs(key.hashCode()) % numPartitions;
            } catch (Exception e) {
                throw new RuntimeException("Failed to extract key for partitioning", e);
            }
        }
    }
}
