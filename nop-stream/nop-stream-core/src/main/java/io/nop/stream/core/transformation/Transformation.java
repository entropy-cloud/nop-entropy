/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import io.nop.stream.core.common.typeinfo.TypeInformation;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class representing a transformation operation in the streaming DAG.
 * This is a simplified version of Flink's Transformation class, providing the core
 * functionality for nop-stream processing operations.
 * 
 * @param <T> the output type of the transformation
 */
public abstract class Transformation<T> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private static final AtomicInteger idCounter = new AtomicInteger(0);
    
    private final int id;
    private final String name;
    private final int parallelism;
    private final TypeInformation<T> outputType;
    
    /**
     * Creates a new transformation with the specified name and output type.
     * 
     * @param name the name of the transformation
     * @param outputType the output type information
     * @param parallelism the parallelism for the transformation
     */
    protected Transformation(String name, TypeInformation<T> outputType, int parallelism) {
        this.id = idCounter.incrementAndGet();
        this.name = name;
        this.outputType = outputType;
        this.parallelism = parallelism;
    }
    
    /**
     * Returns the unique ID of this transformation.
     * 
     * @return the transformation ID
     */
    public int getId() {
        return id;
    }
    
    /**
     * Returns the name of this transformation.
     * 
     * @return the transformation name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns the parallelism for this transformation.
     * 
     * @return the parallelism
     */
    public int getParallelism() {
        return parallelism;
    }
    
    /**
     * Returns the output type information for this transformation.
     * 
     * @return the output type information
     */
    public TypeInformation<T> getOutputType() {
        return outputType;
    }
    
    /**
     * Returns the input transformations that this transformation depends on.
     * 
     * @return the list of input transformations
     */
    public abstract List<Transformation<?>> getInputs();
}