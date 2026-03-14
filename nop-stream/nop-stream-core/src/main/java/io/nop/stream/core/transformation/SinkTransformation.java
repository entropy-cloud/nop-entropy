/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * A transformation that represents a sink operation in the streaming DAG.
 * Sink transformations are terminal operations that consume elements from a stream
 * and send them to an external system (like printing, writing to a file, database, etc.).
 * They have a single input transformation but no output transformations, making them
 * leaf nodes in the streaming graph.
 * 
 * This class follows the same pattern as OneInputTransformation but is specifically
 * designed for sink operations, which are terminal in nature.
 * 
 * @param <T> the type of elements consumed by the sink
 */
public class SinkTransformation<T> extends PhysicalTransformation<Void> {
    
    private static final long serialVersionUID = 1L;
    
    private final Transformation<T> input;
    private final SinkFunction<T> sinkFunction;
    
    /**
     * Creates a new sink transformation with the specified parameters.
     * 
     * @param input the input transformation that provides the data to be consumed
     * @param name the name of the transformation
     * @param sinkFunction the sink function that consumes the elements
     * @param outputType the output type information (Void for sinks)
     * @param parallelism the parallelism for the transformation
     */
    public SinkTransformation(Transformation<T> input, String name, 
                              SinkFunction<T> sinkFunction, 
                              TypeInformation<Void> outputType, int parallelism) {
        super(name, outputType, parallelism);
        this.input = input;
        this.sinkFunction = sinkFunction;
    }
    
    /**
     * Returns the input transformation.
     * 
     * @return the input transformation
     */
    public Transformation<T> getInput() {
        return input;
    }
    
    /**
     * Returns the sink function.
     * 
     * @return the sink function
     */
    public SinkFunction<T> getSinkFunction() {
        return sinkFunction;
    }
    
    /**
     * Returns the input transformations that this transformation depends on.
     * For SinkTransformation, this always returns a single-element list containing the input transformation.
     * 
     * @return the list of input transformations
     */
    @Override
    public List<Transformation<?>> getInputs() {
        return Collections.singletonList(input);
    }
}