/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;

import java.util.Collections;
import java.util.List;

/**
 * A transformation that represents a data source in the streaming DAG.
 * Sources are leaf nodes in the transformation graph and have no input transformations.
 * They produce elements by implementing a SourceFunction that emits data through
 * a SourceContext.
 *
 * <p>
 * This is a simplified version based on Apache Flink's SourceTransformation class.
 * Sources represent the starting point of a streaming pipeline and can be
 * implemented to read from various data sources like Kafka, files, databases, etc.
 * </p>
 *
 * @param <OUT> The type of the elements produced by this source
 *
 * @since 1.0.0
 */
public class SourceTransformation<OUT> extends Transformation<OUT> {

    private static final long serialVersionUID = 1L;

    private final SourceFunction<OUT> sourceFunction;

    /**
     * Creates a new source transformation with the specified parameters.
     *
     * @param name the name of the source transformation
     * @param sourceFunction the source function that produces data
     * @param outputType the output type information for the produced elements
     * @param parallelism the parallelism for the source transformation
     */
    public SourceTransformation(String name, SourceFunction<OUT> sourceFunction, 
                                TypeInformation<OUT> outputType, int parallelism) {
        super(name, outputType, parallelism);
        this.sourceFunction = sourceFunction;
    }

    /**
     * Returns the source function that produces data for this transformation.
     *
     * @return the source function
     */
    public SourceFunction<OUT> getSourceFunction() {
        return sourceFunction;
    }

    /**
     * Returns the input transformations that this transformation depends on.
     * Since sources are leaf nodes in the DAG, they have no input dependencies.
     *
     * @return an empty list, as sources have no input transformations
     */
    @Override
    public List<Transformation<?>> getInputs() {
        return Collections.emptyList();
    }
}