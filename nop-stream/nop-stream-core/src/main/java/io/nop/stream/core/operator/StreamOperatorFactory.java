/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operator;

import io.nop.stream.core.common.typeinfo.TypeInformation;

/**
 * Factory for creating stream operators. This is a simplified version for the nop-stream
 * implementation that provides the basic factory interface for creating operators
 * that process data streams.
 * 
 * @param <OUT> the type of elements produced by the operator
 */
public interface StreamOperatorFactory<OUT> {
    
    /**
     * Creates a new stream operator instance.
     * 
     * @param outputType the output type information for the operator
     * @return a new stream operator instance
     */
    StreamOperator<OUT> createStreamOperator(TypeInformation<OUT> outputType);
    
    /**
     * Gets the parallelism of the operator.
     * 
     * @return the parallelism
     */
    int getParallelism();
    
    /**
     * Gets the name of the operator factory.
     * 
     * @return the factory name
     */
    String getName();
}