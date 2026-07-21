/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

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

    /**
     * Returns whether this operator can be chained with adjacent operators.
     *
     * <p>Operators that return {@code false} will never be chained with upstream or
     * downstream operators, forcing a separate task vertex.
     *
     * <p>Default implementation returns {@code true}.
     */
    default boolean isChainable() {
        return true;
    }

    /**
     * Returns the chaining strategy for this operator.
     *
     * <p>The chaining strategy defines whether this operator can be chained to its
     * predecessor or successor operators. Operators that return {@link ChainingStrategy#NEVER}
     * will always be isolated in their own task vertex.
     *
     * <p>Default implementation returns {@link ChainingStrategy#ALWAYS}.
     */
    default ChainingStrategy getChainingStrategy() {
        return ChainingStrategy.ALWAYS;
    }
}
