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
 * Interface for stream operators that process data streams. This is a simplified
 * version for the nop-stream implementation that provides the basic operator interface.
 * 
 * @param <OUT> the type of elements produced by the operator
 */
public interface StreamOperator<OUT> {
    
    /**
     * Gets the output type information for this operator.
     * 
     * @return the output type information
     */
    TypeInformation<OUT> getOutputType();
    
    /**
     * Gets the name of the operator.
     * 
     * @return the operator name
     */
    String getName();
    
    /**
     * Initializes the operator.
     */
    void initialize();
    
    /**
     * Opens the operator to start processing.
     */
    void open();
    
    /**
     * Closes the operator and releases any resources.
     */
    void close();
    
    /**
     * Gets the chaining strategy for this operator.
     * 
     * @return the chaining strategy
     */
    ChainingStrategy getChainingStrategy();
    
    /**
     * Enumeration of possible chaining strategies.
     */
    enum ChainingStrategy {
        ALWAYS,
        NEVER,
        HEAD
    }
}