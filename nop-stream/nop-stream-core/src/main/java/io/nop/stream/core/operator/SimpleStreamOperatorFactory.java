/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operator;

import io.nop.stream.core.common.typeinfo.TypeInformation;

import java.io.Serializable;

public class SimpleStreamOperatorFactory<OUT> implements StreamOperatorFactory<OUT>, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final Object operator;
    private final String name;
    private final int parallelism;
    
    public SimpleStreamOperatorFactory(Object operator, String name, int parallelism) {
        this.operator = operator;
        this.name = name;
        this.parallelism = parallelism;
    }
    
    public SimpleStreamOperatorFactory(Object operator, String name) {
        this(operator, name, 1);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public StreamOperator<OUT> createStreamOperator(TypeInformation<OUT> outputType) {
        return (StreamOperator<OUT>) operator;
    }
    
    public Object getRawOperator() {
        return operator;
    }
    
    @Override
    public int getParallelism() {
        return parallelism;
    }
    
    @Override
    public String getName() {
        return name;
    }
}
