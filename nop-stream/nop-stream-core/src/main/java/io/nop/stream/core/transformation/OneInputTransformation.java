/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.operator.StreamOperatorFactory;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * A transformation that takes a single input stream and applies an operator to produce an output stream.
 * This is a fundamental building block in stream processing pipelines, representing operations
 * like map, filter, flatMap, etc. that take one input stream and produce one output stream.
 * 
 * @param <IN> the type of the input stream
 * @param <OUT> the type of the output stream
 */
public class OneInputTransformation<IN, OUT> extends PhysicalTransformation<OUT> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final Transformation<IN> input;
    private final StreamOperatorFactory<OUT> operatorFactory;
    private final KeySelector<IN, ?> keySelector;
    
    /**
     * Creates a new one-input transformation with the specified parameters.
     * 
     * @param input the input transformation
     * @param name the name of the transformation
     * @param operatorFactory the operator factory to create the operator
     * @param outputType the output type information
     * @param parallelism the parallelism for the transformation
     */
    public OneInputTransformation(Transformation<IN> input, String name, 
                                   StreamOperatorFactory<OUT> operatorFactory, 
                                   TypeInformation<OUT> outputType, int parallelism) {
        super(name, outputType, parallelism);
        this.input = input;
        this.operatorFactory = operatorFactory;
        this.keySelector = null;
    }
    
    /**
     * Creates a new one-input transformation with the specified parameters, including a key selector.
     * 
     * @param input the input transformation
     * @param name the name of the transformation
     * @param operatorFactory the operator factory to create the operator
     * @param outputType the output type information
     * @param parallelism the parallelism for the transformation
     * @param keySelector the key selector for keyed operations, or null if not keyed
     */
    public OneInputTransformation(Transformation<IN> input, String name, 
                                   StreamOperatorFactory<OUT> operatorFactory, 
                                   TypeInformation<OUT> outputType, int parallelism,
                                   KeySelector<IN, ?> keySelector) {
        super(name, outputType, parallelism);
        this.input = input;
        this.operatorFactory = operatorFactory;
        this.keySelector = keySelector;
    }
    
    /**
     * Returns the input transformation.
     * 
     * @return the input transformation
     */
    public Transformation<IN> getInput() {
        return input;
    }
    
    /**
     * Returns the operator factory.
     * 
     * @return the operator factory
     */
    public StreamOperatorFactory<OUT> getOperatorFactory() {
        return operatorFactory;
    }
    
    /**
     * Returns the key selector.
     * 
     * @return the key selector, or null if not specified
     */
    public KeySelector<IN, ?> getKeySelector() {
        return keySelector;
    }
    
    /**
     * Returns the input transformations that this transformation depends on.
     * For OneInputTransformation, this always returns a single-element list containing the input transformation.
     * 
     * @return the list of input transformations
     */
    @Override
    public List<Transformation<?>> getInputs() {
        return Collections.singletonList(input);
    }
}