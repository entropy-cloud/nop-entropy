/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.streamrecord.StreamRecord;

import java.util.Map;

/**
 * A stream operator that wraps a {@link SourceFunction} and emits elements through the
 * operator chain by calling {@code output.collect()} from within a SourceContext wrapper.
 *
 * <p>This operator is the head of any pipeline: it has no input and produces output by
 * running the user-supplied source function.
 *
 * @param <OUT> The type of elements produced by this source
 */
public class StreamSourceOperator<OUT> extends AbstractStreamOperator<OUT> {

    private static final long serialVersionUID = 1L;

    private final SourceFunction<OUT> sourceFunction;

    private volatile boolean isRunning = true;

    public StreamSourceOperator(SourceFunction<OUT> sourceFunction) {
        this.sourceFunction = sourceFunction;
    }

    /**
     * Returns the wrapped source function.
     *
     * @return the source function
     */
    public SourceFunction<OUT> getSourceFunction() {
        return sourceFunction;
    }

    /**
     * Runs the source function, emitting elements through the operator chain.
     * The source function calls {@link SourceFunction.SourceContext#collect(Object)},
     * which we forward to {@code output.collect(new StreamRecord<>(element))}.
     *
     * @throws Exception if the source function fails
     */
    public void run() throws Exception {
        SourceFunction.SourceContext<OUT> ctx = new SourceFunction.SourceContext<OUT>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(OUT element) {
                output.collect(new StreamRecord<>(element));
            }

            @Override
            public void collectWithTimestamp(OUT element, long timestamp) {
                output.collect(new StreamRecord<>(element, timestamp));
            }

            @Override
            public void emitWatermark(long mark) {
                output.emitWatermark(new io.nop.stream.core.streamrecord.watermark.Watermark(mark));
            }

            @Override
            public void markAsTemporarilyIdle() {
                output.emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus.IDLE);
            }

            @Override
            public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        };

        isRunning = true;
        sourceFunction.run(ctx);
    }

    @Override
    public void open() throws Exception {
    }

    @Override
    public void finish() throws Exception {
        isRunning = false;
    }

    @Override
    public void close() throws Exception {
        isRunning = false;
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        if (sourceFunction instanceof CheckpointListener) {
            ((CheckpointListener) sourceFunction).notifyCheckpointComplete(checkpointId);
        }
    }

    @Override
    public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
        OperatorSnapshotResult result = super.snapshotState(context);
        if (sourceFunction instanceof CheckpointedSourceFunction) {
            ((CheckpointedSourceFunction<?>) sourceFunction).snapshotState(context.getCheckpointId());
        }
        return result;
    }

    @Override
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        if (sourceFunction instanceof CheckpointedSourceFunction) {
            TaskStateSnapshot taskState = new TaskStateSnapshot(0L);
            if (snapshotResult != null) {
                for (Map.Entry<String, byte[]> entry : snapshotResult.getOperatorStates().entrySet()) {
                    taskState.putOperatorState(entry.getKey(), entry.getValue());
                }
                for (Map.Entry<String, byte[]> entry : snapshotResult.getKeyedStates().entrySet()) {
                    taskState.putKeyedState(entry.getKey(), entry.getValue());
                }
            }
            ((CheckpointedSourceFunction<?>) sourceFunction).initializeState(taskState);
        }
    }

    public void injectBarrier(CheckpointBarrier barrier) throws Exception {
        OperatorSnapshotResult snapshotResult = null;
        if (barrier.snapshot()) {
            StateSnapshotContext context = new StateSnapshotContext(barrier.getId(), barrier.getTimestamp());
            snapshotResult = snapshotState(context);
            this.lastSnapshotResult = snapshotResult;
        }
        if (snapshotCallback != null && snapshotResult != null) {
            snapshotCallback.accept(snapshotResult);
        }
        if (output != null) {
            output.emitBarrier(barrier);
        }
    }
}
