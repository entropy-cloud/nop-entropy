/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.ChainingOutput;
import io.nop.stream.core.operators.Input;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.watermark.Watermark;

import java.util.List;
/**
 * Invokable that executes a single-chain streaming pipeline through the graph model path.
 *
 * <p>This class replaces the placeholder {@link Invokable} with real data flow execution.
 * It wires operators together with {@link ChainingOutput}, runs the source operator,
 * and emits a final {@link Watermark#MAX_WATERMARK} after the source completes.
 *
 * <p><strong>Lifecycle:</strong> The {@link io.nop.stream.core.execution.Task} that owns this invokable
 * handles opening and closing operator chains via {@link OperatorChain#open()} and
 * {@link OperatorChain#close()}. This invokable is responsible only for wiring and
 * running the data flow within {@link #invoke()}.
 *
 * <p><strong>Wiring:</strong> During construction, adjacent operators in the chain are wired
 * with {@link ChainingOutput} so that each operator's output forwards to the next operator's
 * input, matching the fast path behavior in
 * {@link io.nop.stream.core.environment.StreamExecutionEnvironment#wireOperatorChain}.
 *
 * @see Invokable
 * @see OperatorChain
 * @see StreamSourceOperator
 * @see ChainingOutput
 */
@Internal
public class StreamTaskInvokable implements Invokable<Void> {

    private static final long serialVersionUID = 1L;

    private final OperatorChain operatorChain;

    private CheckpointBarrierTracker barrierTracker;

    /**
     * Creates a new StreamTaskInvokable and wires the operators in the chain.
     *
     * @param operatorChain the operator chain to execute (must not be null)
     */
    public StreamTaskInvokable(OperatorChain operatorChain) {
        if (operatorChain == null) {
            throw new IllegalArgumentException("OperatorChain cannot be null");
        }
        this.operatorChain = operatorChain;
        wireOperators();
    }

    /**
     * Wires adjacent operators with {@link ChainingOutput} so that each operator's
     * output forwards to the next operator's input.
     *
     * <p>Only operators that extend {@link AbstractStreamOperator} are wired, since
     * only they have a settable output field. The last operator (sink) is not wired
     * since it has no downstream.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void wireOperators() {
        List<StreamOperator<?>> operators = operatorChain.getOperators();

        for (int i = 0; i < operators.size() - 1; i++) {
            StreamOperator<?> current = operators.get(i);
            StreamOperator<?> next = operators.get(i + 1);

            if (current instanceof AbstractStreamOperator && next instanceof Input) {
                AbstractStreamOperator<?> currentOp = (AbstractStreamOperator<?>) current;
                Input<?> nextInput = (Input<?>) next;
                currentOp.setOutput(new ChainingOutput(nextInput));
            }
        }
    }

    public void setBarrierTracker(CheckpointBarrierTracker tracker) {
        this.barrierTracker = tracker;
        if (tracker != null) {
            setupSnapshotCallbacks();
        }
    }

    public CheckpointBarrierTracker getBarrierTracker() {
        return barrierTracker;
    }

    public OperatorChain getOperatorChain() {
        return operatorChain;
    }

    private void setupSnapshotCallbacks() {
        List<StreamOperator<?>> operators = operatorChain.getOperators();
        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i) instanceof AbstractStreamOperator) {
                final int opIndex = i;
                ((AbstractStreamOperator<?>) operators.get(i)).setSnapshotCallback(
                    snapshot -> barrierTracker.acknowledgeOperator(opIndex, snapshot)
                );
            }
        }
    }

    /**
     * Executes the data flow: runs the source operator and emits a final MAX_WATERMARK.
     *
     * <p>Operator open/close lifecycle is handled by the owning {@link Task},
     * so this method only needs to:
     * <ol>
     *   <li>Run the source operator to push data through the chain</li>
     *   <li>Emit {@link Watermark#MAX_WATERMARK} to signal end of stream</li>
     * </ol>
     *
     * @throws Exception if the source operator fails during execution
     */
    @Override
    public void invoke() throws Exception {
        List<StreamOperator<?>> operators = operatorChain.getOperators();
        StreamOperator<?> head = operators.get(0);

        if (head instanceof StreamSourceOperator) {
            StreamSourceOperator<?> sourceOp = (StreamSourceOperator<?>) head;
            if (sourceOp.getOutput() != null) {
                sourceOp.run();
                sourceOp.processWatermark(Watermark.MAX_WATERMARK);
            }
        }
    }
}
