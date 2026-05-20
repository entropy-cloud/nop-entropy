/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.typeinfo.TypeInformation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SimpleStreamOperatorFactory<OUT> implements StreamOperatorFactory<OUT>, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final StreamOperator<OUT> operator;
    private final String name;
    private final int parallelism;
    
    public SimpleStreamOperatorFactory(StreamOperator<OUT> operator, String name, int parallelism) {
        this.operator = operator;
        this.name = name;
        this.parallelism = parallelism;
    }
    
    public SimpleStreamOperatorFactory(StreamOperator<OUT> operator, String name) {
        this(operator, name, 1);
    }
    
    @Override
    public StreamOperator<OUT> createStreamOperator(TypeInformation<OUT> outputType) {
        // If the operator is Serializable, create a deep copy so each invocation
        // returns an independent instance. Otherwise fall back to returning the
        // shared template instance (documented limitation for non-serializable operators).
        if (operator instanceof Serializable) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                    oos.writeObject(operator);
                }
                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                try (ObjectInputStream ois = new ObjectInputStream(bais)) {
                    @SuppressWarnings("unchecked")
                    StreamOperator<OUT> copy = (StreamOperator<OUT>) ois.readObject();
                    return copy;
                }
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to create copy of operator via serialization: " + name, e);
            }
        }
        return operator;
    }
    
    public StreamOperator<OUT> getRawOperator() {
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
