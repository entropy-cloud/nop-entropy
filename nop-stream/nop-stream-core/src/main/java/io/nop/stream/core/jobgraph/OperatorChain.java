/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * <p>The chain maintains a list of operators that implement the {@link io.nop.stream.core.operator.StreamOperator}
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
 * @see io.nop.stream.core.operator.StreamOperator
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
    private final List<io.nop.stream.core.operator.StreamOperator<?>> operators;

    /**
     * Constructs an OperatorChain with the specified list of operators.
     *
     * <p>The operators list is defensively copied to ensure immutability.
     * The list must not be null or empty.
     *
     * @param operators the list of operators to chain together (must not be null or empty)
     * @throws IllegalArgumentException if operators is null or empty
     */
    public OperatorChain(List<io.nop.stream.core.operator.StreamOperator<?>> operators) {
        if (operators == null || operators.isEmpty()) {
            throw new IllegalArgumentException("Operators list cannot be null or empty");
        }
        this.operators = new ArrayList<>(operators); // Defensive copy
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
        for (io.nop.stream.core.operator.StreamOperator<?> operator : operators) {
            if (operator instanceof io.nop.stream.core.operators.Input) {
                try {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    io.nop.stream.core.operators.Input input =
                        (io.nop.stream.core.operators.Input) operator;
                    input.processElement(record);
                } catch (Exception e) {
                    throw new RuntimeException(
                        "Failed to process element in operator: " + operator.getName(), e);
                }
            } else {
                throw new IllegalStateException(
                    "Operator does not implement Input interface: " + operator.getClass().getName());
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

        // Open operators in sequence
        for (io.nop.stream.core.operator.StreamOperator<?> operator : operators) {
            try {
                operator.open();
                openedCount++;
            } catch (Exception e) {
                firstException = e;
                break;
            }
        }

        // If opening failed, close already opened operators
        if (firstException != null) {
            for (int i = 0; i < openedCount; i++) {
                try {
                    operators.get(i).close();
                } catch (Exception closeException) {
                    // Log but don't override the first exception
                    firstException.addSuppressed(closeException);
                }
            }
            throw new RuntimeException("Failed to open operator chain", firstException);
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

        // Close operators in reverse order
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
            throw new RuntimeException("Failed to close operator chain", firstException);
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
    public List<io.nop.stream.core.operator.StreamOperator<?>> getOperators() {
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
}
