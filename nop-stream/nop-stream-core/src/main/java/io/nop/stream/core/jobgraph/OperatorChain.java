/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.*;

/**
 * Represents a chain of operators that are executed together in a single task for optimization.
 *
 * <p>Operator chaining is an optimization technique where multiple operators are fused together
 * to run in the same thread, avoiding serialization and network overhead. An OperatorChain
 * contains a sequence of operators that will be executed in order within a single task instance.
 *
 * <p>For example, a chain like "source -> map -> filter" can be executed in one task without
 * any data exchange between operators. This significantly improves performance by:
 * <ul>
 *   <li>Eliminating serialization/deserialization overhead between operators</li>
 *   <li>Avoiding network communication for data transfer</li>
 *   <li>Reducing thread context switching</li>
 *   <li>Enabling better CPU cache utilization</li>
 * </ul>
 *
 * <p>The chain maintains a list of operators that implement the {@link io.nop.stream.core.operators.StreamOperator}
 * interface. During execution, records flow through each operator in sequence via the
 * {@link io.nop.stream.core.operators.Input#processElement} method.
 *
 * <p><strong>Lifecycle Management:</strong>
 * <ul>
 *   <li>{@link #open()}: Initializes all operators in the chain before processing begins</li>
 *   <li>{@link #processElement(io.nop.stream.core.streamrecord.StreamRecord)}: Processes records through the chain</li>
 *   <li>{@link #close()}: Cleans up all operators after processing completes</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> OperatorChain instances are immutable after construction.
 * The operators list cannot be modified once the chain is created. Each parallel task instance
 * should have its own OperatorChain instance.
 *
 * @see io.nop.stream.core.operators.StreamOperator
 * @see io.nop.stream.core.operators.Input
 * @see io.nop.stream.core.streamrecord.StreamRecord
 * @see JobVertex
 */
public class OperatorChain implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The chain of operators to execute in sequence.
     * This list is immutable after construction.
     */
    private final List<io.nop.stream.core.operators.StreamOperator<?>> operators;

    private final List<KeySelector<?, ?>> keySelectors;

    public OperatorChain(List<io.nop.stream.core.operators.StreamOperator<?>> operators) {
        this(operators, Collections.emptyList());
    }

    public OperatorChain(List<io.nop.stream.core.operators.StreamOperator<?>> operators,
                         List<KeySelector<?, ?>> keySelectors) {
        if (operators == null || operators.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operators");
        }
        this.operators = new ArrayList<>(operators);
        this.keySelectors = keySelectors != null ? new ArrayList<>(keySelectors) : Collections.emptyList();
    }

    /**
     * Processes a stream record through all operators in the chain.
     *
     * <p>The record is passed to each operator in sequence. Each operator processes
     * the record and potentially transforms it before passing it to the next operator
     * in the chain.
     *
     * <p><strong>Note:</strong> This method assumes all operators in the chain implement
     * the {@link io.nop.stream.core.operators.Input} interface. If an operator does not
     * implement Input, an {@link IllegalStateException} will be thrown.
     *
     * @param record the stream record to process
     * @throws IllegalStateException if an operator in the chain does not implement Input
     * @throws RuntimeException if any operator throws an exception during processing
     */
    public void processElement(io.nop.stream.core.streamrecord.StreamRecord<?> record) {
        // Process record through each operator in the chain
        for (io.nop.stream.core.operators.StreamOperator<?> operator : operators) {
            if (operator instanceof io.nop.stream.core.operators.Input) {
                try {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    io.nop.stream.core.operators.Input input =
                        (io.nop.stream.core.operators.Input) operator;
                    input.processElement(record);
                } catch (Exception e) {
                    throw new StreamException(ERR_STREAM_OPERATOR_ERROR, e)
                            .param(ARG_OPERATOR_NAME, operator.getClass().getName())
                            .param(ARG_DETAIL, "Failed to process element in operator");
                }
            } else {
                throw new StreamException(ERR_STREAM_OPERATOR_ERROR)
                        .param(ARG_OPERATOR_NAME, operator.getClass().getName())
                        .param(ARG_DETAIL, "Operator does not implement Input interface");
            }
        }
    }

    /**
     * Opens all operators in the chain to start processing.
     *
     * <p>This method should be called before any calls to {@link #processElement}.
     * It Initializes all operators in sequence, preparing them for data processing.
     * If any operator fails to open, previously opened operators are closed before
     * propagating the exception.
     *
     * <p><strong>Implementation Note:</strong> The operators are opened in forward order.
     * If an exception occurs during opening, cleanup is performed for already opened operators.
     *
     * @throws RuntimeException if any operator fails to open
     */
    public void open() {
        Exception firstException = null;
        int openedCount = 0;

        for (int i = operators.size() - 1; i >= 0; i--) {
            try {
                operators.get(i).open();
                openedCount++;
            } catch (Exception e) {
                firstException = e;
                break;
            }
        }

        if (firstException != null) {
            for (int i = operators.size() - 1; i >= operators.size() - openedCount; i--) {
                try {
                    operators.get(i).close();
                } catch (Exception closeException) {
                    firstException.addSuppressed(closeException);
                }
            }
            throw new StreamException(ERR_STREAM_OPERATOR_ERROR, firstException)
                    .param(ARG_DETAIL, "Failed to open operator chain");
        }
    }

    /**
     * Closes all operators in the chain and releases any resources.
     *
     * <p>This method should be called after all processing is complete. It closes
     * all operators in reverse order to ensure proper cleanup. Exceptions during
     * closing are collected and not thrown immediately to ensure all operators
     * get a chance to close.
     *
     * <p><strong>Implementation Note:</strong> The operators are closed in reverse order.
     * All operators are attempted to be closed even if some fail. Exceptions are
     * suppressed and attached to the first exception if multiple failures occur.
     *
     * @throws RuntimeException if any operator fails to close (first exception, others suppressed)
     */
    public void close() {
        Exception firstException = null;

        for (int i = operators.size() - 1; i >= 0; i--) {
            try {
                operators.get(i).close();
            } catch (Exception e) {
                if (firstException == null) {
                    firstException = e;
                } else {
                    firstException.addSuppressed(e);
                }
            }
        }

        if (firstException != null) {
            throw new StreamException(ERR_STREAM_OPERATOR_ERROR, firstException)
                    .param(ARG_DETAIL, "Failed to close operator chain");
        }
    }

    /**
     * Returns the list of operators in this chain.
     *
     * <p>The returned list is an unmodifiable view to prevent external modification.
     * The operator chain is immutable after construction.
     *
     * @return unmodifiable list of operators in the chain
     */
    public List<io.nop.stream.core.operators.StreamOperator<?>> getOperators() {
        return Collections.unmodifiableList(operators);
    }

    /**
     * Returns the number of operators in this chain.
     *
     * @return the number of operators
     */
    public int getNumberOfOperators() {
        return operators.size();
    }

    public List<KeySelector<?, ?>> getKeySelectors() {
        return Collections.unmodifiableList(keySelectors);
    }

    /**
     * Creates a deep copy of this OperatorChain using Java serialization.
     * Each parallel task instance must have its own OperatorChain to avoid shared mutable state.
     *
     * @return a new OperatorChain with independent copies of all operators
     * @throws RuntimeException if serialization fails (e.g., operators contain non-serializable state)
     */
    public OperatorChain deepCopy() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(this);
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            try (ObjectInputStream ois = new ObjectInputStream(bais)) {
                return (OperatorChain) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new StreamException(ERR_STREAM_SERIALIZATION, e)
                    .param(ARG_DETAIL, "Failed to deep-copy OperatorChain. Ensure all operators and their state are Serializable.");
        }
    }
}
