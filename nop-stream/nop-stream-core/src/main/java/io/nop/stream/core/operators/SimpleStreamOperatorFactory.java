/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.operators.ChainingStrategy.ALWAYS;

public class SimpleStreamOperatorFactory<OUT> implements StreamOperatorFactory<OUT>, Serializable {
    
    private static final long serialVersionUID = 1L;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SimpleStreamOperatorFactory.class);
    
    private final StreamOperator<OUT> operator;
    private final String name;
    private final int parallelism;
    private ChainingStrategy chainingStrategy = ALWAYS;
    
    public SimpleStreamOperatorFactory(StreamOperator<OUT> operator, String name, int parallelism) {
        this.operator = operator;
        this.name = name;
        this.parallelism = parallelism;
    }
    
    public SimpleStreamOperatorFactory(StreamOperator<OUT> operator, String name) {
        this(operator, name, 1);
    }

    public SimpleStreamOperatorFactory(StreamOperator<OUT> operator, String name, int parallelism,
                                       ChainingStrategy chainingStrategy) {
        this(operator, name, parallelism);
        this.chainingStrategy = chainingStrategy;
    }
    
    @Override
    public StreamOperator<OUT> createStreamOperator(TypeInformation<OUT> outputType) {
        // Shareable operators opt out of the per-subtask copy contract entirely.
        if (operator.getClass().isAnnotationPresent(Shareable.class)) {
            return operator;
        }
        // If the operator is Serializable, create a deep copy so each invocation
        // returns an independent instance.
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
            } catch (java.io.NotSerializableException e) {
                // Operator contains non-serializable fields (e.g. lambdas).
                // Defensive SPI guard: previously this silently returned the shared
                // template instance, which corrupts parallel execution. Fail fast so
                // future SPI consumers (codegen/test harness) cannot silently regress.
                // Production code bypasses this path via getRawOperator(), so this is
                // a defense-in-depth guard for the public SPI contract.
                LOG.warn("Operator '{}' is not serializable; refusing to silently share "
                        + "template instance across subtasks (parallel SPI consumer would "
                        + "see cross-subtask state corruption). Throwing fail-fast.", name);
                throw new StreamException(
                        "Cannot create independent copy of non-serializable operator '" + name
                                + "'. Mark the operator @Shareable if cross-subtask sharing is safe, "
                                + "or override copyForSubtask() / make the operator Serializable.",
                        e);
            } catch (Exception e) {
                throw new StreamException(
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

    @Override
    public ChainingStrategy getChainingStrategy() {
        return chainingStrategy;
    }

    public void setChainingStrategy(ChainingStrategy chainingStrategy) {
        this.chainingStrategy = chainingStrategy;
    }
}
