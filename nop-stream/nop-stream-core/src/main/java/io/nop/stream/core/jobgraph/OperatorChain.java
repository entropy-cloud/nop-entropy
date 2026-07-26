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

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OPERATOR_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_OPERATOR_ERROR;

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
 *   <li>{@link #finish()}: Flushes any buffered data in each operator after the source returns</li>
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
     * Opens all operators in the chain to start processing.
     *
     * <p>This method should be called before data processing begins.
     * It Initializes all operators in sequence, preparing them for data processing.
     * If any operator fails to open, previously opened operators are closed before
     * propagating the exception.
     *
     * <p><strong>Implementation Note:</strong> The operators are opened in reverse order
     * (tail to head, i.e. last operator first). If an exception occurs during opening,
     * cleanup is performed for already opened operators.
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
     * Finishes all operators in the chain after data processing is complete.
     *
     * <p>This method should be called after the head source returns and BEFORE the
     * MAX_WATERMARK is emitted and {@link #close()} is invoked. It drives the
     * 5-segment operator lifecycle: {@code open() → process*() → finish() → close()}.
     * The prior implementation skipped {@code finish()}, collapsing the lifecycle
     * to 3 segments and silently dropping any buffered data that connectors
     * (e.g. {@code BatchConsumerSinkFunction}) were relying on {@code finish()} to flush.
     *
     * <p><strong>Implementation Note:</strong> Operators are finished in reverse
     * order (tail to head, matching {@link #close()} ordering) so a downstream
     * sink fully drains its buffers before its upstream peers finish. Exceptions
     * during finishing are collected (suppressed on the first failure) and
     * propagated after all operators have been attempted — mirroring {@link #close()}.
     *
     * @throws RuntimeException if any operator fails to finish
     */
    public void finish() {
        Exception firstException = null;

        for (int i = operators.size() - 1; i >= 0; i--) {
            try {
                operators.get(i).finish();
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
                    .param(ARG_DETAIL, "Failed to finish operator chain");
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
     * Creates a shallow copy of this OperatorChain suitable for parallel subtask execution.
     *
     * <p>Each parallel subtask needs its own OperatorChain to maintain independent operator state
     * (output wiring, watermark tracking, etc.), but user functions (closures, sinks, map functions)
     * are shared across subtasks to preserve captured external references (e.g., result lists,
     * counters). This is the correct behavior: in a streaming pipeline, user functions should
     * be able to observe all elements from their subtask, and collected results should be visible
     * to the test caller regardless of which subtask produced them.
     *
     * <p>Per-operator copy semantics are delegated to {@link
     * io.nop.stream.core.operators.StreamOperator#copyForSubtask()}. Operators that do not
     * declare copy semantics (no override, not {@link io.nop.stream.core.operators.Shareable})
     * throw {@link UnsupportedOperationException} here so that parallelism &gt; 1 cannot silently
     * fall back to sharing mutable state across subtasks (No-Silent-No-Op).
     *
     * @return a new OperatorChain with fresh operator state but shared user functions
     */
    public OperatorChain deepCopy() {
        List<io.nop.stream.core.operators.StreamOperator<?>> copiedOperators = new ArrayList<>(operators.size());
        for (io.nop.stream.core.operators.StreamOperator<?> op : operators) {
            copiedOperators.add(op.copyForSubtask());
        }
        return new OperatorChain(copiedOperators, new ArrayList<>(keySelectors));
    }
}
