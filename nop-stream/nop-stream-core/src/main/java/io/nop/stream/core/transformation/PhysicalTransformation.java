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

/**
 * Abstract base class for physical transformations that can be executed.
 * Physical transformations represent actual operations that can be deployed and run,
 * as opposed to logical transformations that may be optimized or transformed.
 * 
 * @param <OUT> the output type of the transformation
 */
public abstract class PhysicalTransformation<OUT> extends Transformation<OUT> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Creates a new physical transformation with the specified name and output type.
     * 
     * @param name the name of the transformation
     * @param outputType the output type information
     * @param parallelism the parallelism for the transformation
     */
    protected PhysicalTransformation(String name, TypeInformation<OUT> outputType, int parallelism) {
        super(name, outputType, parallelism);
    }
}