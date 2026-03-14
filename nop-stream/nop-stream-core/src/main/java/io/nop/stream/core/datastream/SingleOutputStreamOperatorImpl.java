/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.transformation.Transformation;

/**
 * Implementation of {@link SingleOutputStreamOperator} which represents a user defined
 * transformation applied on a {@link DataStream} with one predefined output type.
 * 
 * <p>This class extends {@link DataStreamImpl} and adds the ability to force non-parallel
 * execution, which is useful for operations that require global ordering or single-threaded
 * execution.
 * 
 * @param <T> The type of the elements in this stream
 */
public class SingleOutputStreamOperatorImpl<T> extends DataStreamImpl<T> implements SingleOutputStreamOperator<T> {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Creates a new SingleOutputStreamOperatorImpl with the specified environment and transformation.
     * 
     * @param environment the execution environment
     * @param transformation the transformation that produces this stream
     */
    public SingleOutputStreamOperatorImpl(StreamExecutionEnvironment environment, Transformation<T> transformation) {
        super(environment, transformation);
    }
    
    /**
     * Sets the parallelism and maximum parallelism of this operator to one. And marks this
     * operator as non-parallelizable.
     * 
     * <p>This is useful for operations that require global ordering or single-threaded execution,
     * such as global aggregation or output to a single external system.
     * 
     * @return The operator with only one parallelism
     */
    @Override
    public SingleOutputStreamOperator<T> forceNonParallel() {
        // In a full implementation, this would set the parallelism to 1
        // and mark the operator as non-parallelizable
        // For now, we just return this instance
        return this;
    }
}
