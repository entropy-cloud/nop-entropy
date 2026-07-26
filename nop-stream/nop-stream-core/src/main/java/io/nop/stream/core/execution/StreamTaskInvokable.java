/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.ChainingOutput;
import io.nop.stream.core.operators.Input;
import io.nop.stream.core.operators.KeyContext;
import io.nop.stream.core.operators.KeyExtractingOutput;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHAINING_OUTPUT_CLOSE_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * Invokable that executes a streaming pipeline through the graph model path,
 * supporting Source, Middle, Sink, and Self-Contained roles.
 *
 * <p>The role is determined by which data exchange components are provided:
 * <ul>
 *   <li><b>Source</b>: outputWriter != null, inputGate == null</li>
 *   <li><b>Middle</b>: outputWriter != null, inputGate != null</li>
 *   <li><b>Sink</b>: outputWriter == null, inputGate != null</li>
 *   <li><b>Self-Contained</b>: both null (original single-chain behavior)</li>
 * </ul>
 */
@Internal
public class StreamTaskInvokable implements Invokable<Void> {

    private static final Logger LOG = LoggerFactory.getLogger(StreamTaskInvokable.class);
    private static final long serialVersionUID = 1L;

    private final OperatorChain operatorChain;
    private final RecordWriter<Object> outputWriter;
    private final InputGate inputGate;

    /**
     * Per-task mailbox executor: the control-plane anchor for this task thread.
     * Holds the {@link TaskMailbox} (multi-producer, single-consumer) and the cooperative
     * cancel flag. See {@code ai-dev/design/nop-stream/mailbox-design.md}.
     *
     * <p>SOURCE/SELF_CONTAINED: the head source operator's trigger-checkpoint mails are
     * delivered here and drained at the {@code SourceContext.collect()} emission point.
     * MIDDLE/SINK: the main loop ({@code processInputGate}) polls this at the top of each
     * iteration. The abort handler delivers a cancel mail + raises the cancel flag here.
     */
    private final MailboxExecutor mailboxExecutor = new MailboxExecutor();

    /**
     * G52: per-invokable liveness timestamp. Updated at every data-plane progress
     * point (record emission for SOURCE/SELF_CONTAINED via {@link #markProgress()},
     * input-gate iteration for MIDDLE/SINK inside {@link #processInputGate}).
     * Read by {@code TaskManager.heartbeat()} via {@link #getLastProgressTime()}
     * and reported to the coordinator piggybacked on the existing node heartbeat.
     *
     * <p>Volatile because the writer is the task thread and the reader is the
     * heartbeat thread; only ever assigned monotonically non-decreasing values.
     */
    private volatile long lastProgressTime = System.currentTimeMillis();

    private CheckpointBarrierTracker barrierTracker;

    private Input<Object> headInput;

    public StreamTaskInvokable(OperatorChain operatorChain) {
        if (operatorChain == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorChain");
        }
        this.operatorChain = operatorChain;
        this.outputWriter = null;
        this.inputGate = null;
        wireOperators();
        wireMailboxToHeadSource();
    }

    public StreamTaskInvokable(OperatorChain operatorChain, List<RecordWriter<Object>> fanOutWriters) {
        if (operatorChain == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorChain");
        }
        this.operatorChain = operatorChain;
        this.outputWriter = !fanOutWriters.isEmpty() ? fanOutWriters.get(0) : null;
        this.inputGate = null;
        wireOperators(fanOutWriters);
        wireMailboxToHeadSource();
    }

    @SuppressWarnings("unchecked")
    public StreamTaskInvokable(OperatorChain operatorChain,
                               RecordWriter<?> outputWriter,
                               InputGate inputGate) {
        if (operatorChain == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorChain");
        }
        this.operatorChain = operatorChain;
        this.outputWriter = (RecordWriter<Object>) outputWriter;
        this.inputGate = inputGate;
        wireOperators();
        wireMailboxToHeadSource();
    }

    public StreamTaskInvokable(OperatorChain operatorChain,
                               List<RecordWriter<Object>> fanOutWriters,
                               InputGate inputGate) {
        if (operatorChain == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorChain");
        }
        this.operatorChain = operatorChain;
        this.outputWriter = !fanOutWriters.isEmpty() ? fanOutWriters.get(0) : null;
        this.inputGate = inputGate;
        wireOperators(fanOutWriters);
        wireMailboxToHeadSource();
    }

    public TaskRole getRole() {
        if (outputWriter != null && inputGate == null) {
            return TaskRole.SOURCE;
        } else if (outputWriter != null) {
            return TaskRole.MIDDLE;
        } else if (inputGate != null) {
            return TaskRole.SINK;
        }
        return TaskRole.SELF_CONTAINED;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void wireOperators() {
        List<StreamOperator<?>> operators = operatorChain.getOperators();
        List<KeySelector<?, ?>> keySelectors = operatorChain.getKeySelectors();
        int lastIndex = operators.size() - 1;

        for (int i = 0; i < lastIndex; i++) {
            StreamOperator<?> current = operators.get(i);
            StreamOperator<?> next = operators.get(i + 1);

            if (current instanceof AbstractStreamOperator && next instanceof Input) {
                AbstractStreamOperator currentOp = (AbstractStreamOperator) current;
                Input nextInput = (Input) next;

                Input wiredInput;
                if (i + 1 < keySelectors.size() && keySelectors.get(i + 1) != null && next instanceof KeyContext) {
                    wiredInput = new KeyExtractingOutput<>(nextInput, keySelectors.get(i + 1), (KeyContext) next);
                } else {
                    wiredInput = nextInput;
                }

                currentOp.setOutput(new ChainingOutput<>(wiredInput));
            }
        }

        if (!operators.isEmpty() && operators.get(0) instanceof Input) {
            Input rawHeadInput = (Input) operators.get(0);
            if (!keySelectors.isEmpty() && keySelectors.get(0) != null && operators.get(0) instanceof KeyContext) {
                headInput = new KeyExtractingOutput<>(rawHeadInput, keySelectors.get(0), (KeyContext) operators.get(0));
            } else {
                headInput = rawHeadInput;
            }
        }

        if (outputWriter != null) {
            wireTailToRecordWriter(operators, lastIndex);
        }
    }

    private void wireOperators(List<RecordWriter<Object>> fanOutWriters) {
        List<StreamOperator<?>> operators = operatorChain.getOperators();
        List<KeySelector<?, ?>> keySelectors = operatorChain.getKeySelectors();
        int lastIndex = operators.size() - 1;

        for (int i = 0; i < lastIndex; i++) {
            StreamOperator<?> current = operators.get(i);
            StreamOperator<?> next = operators.get(i + 1);

            if (current instanceof AbstractStreamOperator && next instanceof Input) {
                AbstractStreamOperator currentOp = (AbstractStreamOperator) current;
                Input nextInput = (Input) next;

                Input wiredInput;
                if (i + 1 < keySelectors.size() && keySelectors.get(i + 1) != null && next instanceof KeyContext) {
                    wiredInput = new KeyExtractingOutput<>(nextInput, keySelectors.get(i + 1), (KeyContext) next);
                } else {
                    wiredInput = nextInput;
                }

                currentOp.setOutput(new ChainingOutput<>(wiredInput));
            }
        }

        if (!operators.isEmpty() && operators.get(0) instanceof Input) {
            Input rawHeadInput = (Input) operators.get(0);
            if (!keySelectors.isEmpty() && keySelectors.get(0) != null && operators.get(0) instanceof KeyContext) {
                headInput = new KeyExtractingOutput<>(rawHeadInput, keySelectors.get(0), (KeyContext) operators.get(0));
            } else {
                headInput = rawHeadInput;
            }
        }

        if (!operators.isEmpty() && lastIndex >= 0) {
            StreamOperator<?> tail = operators.get(lastIndex);
            if (tail instanceof AbstractStreamOperator) {
                @SuppressWarnings("unchecked")
                AbstractStreamOperator<Object> op = (AbstractStreamOperator<Object>) tail;
                if (fanOutWriters.size() == 1) {
                    op.setOutput(new RecordWriterOutput(fanOutWriters.get(0)));
                } else {
                    List<Output<StreamRecord<Object>>> outputs = new ArrayList<>();
                    for (RecordWriter<Object> writer : fanOutWriters) {
                        outputs.add(new RecordWriterOutput(writer));
                    }
                    op.setOutput(new BroadcastingRecordWriterOutput(outputs));
                }
            }
        }
    }

    public void setBarrierTracker(CheckpointBarrierTracker tracker) {
        this.barrierTracker = tracker;
        if (tracker != null) {
            setupSnapshotCallbacks();
        }
        // Ensure the head source operator (if any) can deliver trigger-checkpoint mails
        // to this task's mailbox. Idempotent and safe for non-source roles.
        wireMailboxToHeadSource();
    }

    /**
     * Wires this task's {@link MailboxExecutor} to the head operator when it is a
     * {@link StreamSourceOperator}, so that {@code offerBarrier()} delivers
     * trigger-checkpoint mails to the task mailbox. Applies to SOURCE and SELF_CONTAINED
     * roles. No-op for MIDDLE/SINK and when no head source operator is present.
     *
     * <p>G52: also wires {@code setProgressMarker(this::markProgress)} so that
     * {@link StreamSourceOperator}'s SourceContext refreshes
     * {@link #getLastProgressTime()} on every emitted record.
     */
    private void wireMailboxToHeadSource() {
        List<StreamOperator<?>> operators = operatorChain.getOperators();
        if (!operators.isEmpty()) {
            StreamOperator<?> head = operators.get(0);
            if (head instanceof StreamSourceOperator) {
                StreamSourceOperator<?> sourceOp = (StreamSourceOperator<?>) head;
                sourceOp.setMailboxExecutor(mailboxExecutor);
                sourceOp.setProgressMarker(this::markProgress);
            }
        }
    }

    public CheckpointBarrierTracker getBarrierTracker() {
        return barrierTracker;
    }

    /**
     * @return this task's mailbox executor (control-plane anchor). Never null. The
     *         barrier-injector thread and abort handler deliver control mails here; the
     *         task thread drains them at safe points.
     */
    public MailboxExecutor getMailboxExecutor() {
        return mailboxExecutor;
    }

    /**
     * G52: liveness timestamp. Updated at every data-plane progress point.
     *
     * @return monotonic timestamp of the last data-plane progress; never decreases
     */
    public long getLastProgressTime() {
        return lastProgressTime;
    }

    /**
     * G52: marks a data-plane progress event. Called from {@link #processInputGate}
     * (MIDDLE/SINK) and from the source emission paths
     * ({@link #invokeSource}/{@link #invokeSelfContained} via SourceContext.collect
     * or the source operator's pull loop). Idempotent and thread-safe (volatile
     * assignment from the task thread only).
     */
    public void markProgress() {
        this.lastProgressTime = System.currentTimeMillis();
    }

    public OperatorChain getOperatorChain() {
        return operatorChain;
    }

    public RecordWriter<Object> getOutputWriter() {
        return outputWriter;
    }

    public InputGate getInputGate() {
        return inputGate;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void wireTailToRecordWriter(List<StreamOperator<?>> operators, int lastIndex) {
        StreamOperator<?> tail = operators.get(lastIndex);
        if (tail instanceof AbstractStreamOperator) {
            AbstractStreamOperator op = (AbstractStreamOperator) tail;
            op.setOutput(new RecordWriterOutput(outputWriter));
        }
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

    @Override
    public void invoke() throws Exception {
        switch (getRole()) {
            case SOURCE:
                invokeSource();
                break;
            case MIDDLE:
                invokeMiddle();
                break;
            case SINK:
                invokeSink();
                break;
            case SELF_CONTAINED:
                invokeSelfContained();
                break;
        }
    }

    private void invokeSource() throws Exception {
        operatorChain.open();
        // G52: liveness marker for the SOURCE role at the start of the run loop.
        // SourceContext.collect() (the per-record emission path) also marks progress;
        // this initial marker covers a slow-start source that has not emitted yet.
        markProgress();
        Exception sourceError = null;
        try {
            List<StreamOperator<?>> operators = operatorChain.getOperators();
            StreamOperator<?> head = operators.get(0);

            if (head instanceof StreamSourceOperator) {
                StreamSourceOperator<?> sourceOp = (StreamSourceOperator<?>) head;
                if (sourceOp.getOutput() != null) {
                    try {
                        sourceOp.run();
                    } catch (Exception e) {
                        sourceError = e;
                    }
                    // G52: mark progress after source run returns (covers a source
                    // that emits in batches and might otherwise look idle mid-run).
                    markProgress();
                }
            }

            // P1-5: finish() must run after the source returns and BEFORE the
            // MAX_WATERMARK is emitted and operators are closed. Without this,
            // connectors that buffer (e.g. BatchConsumerSinkFunction) silently
            // dropped the tail batch on bounded source EOS.
            if (sourceError == null) {
                operatorChain.finish();
            }
        } finally {
            try {
                List<StreamOperator<?>> operators = operatorChain.getOperators();
                StreamOperator<?> head = operators.get(0);
                if (head instanceof StreamSourceOperator) {
                    ((StreamSourceOperator<?>) head).processWatermark(Watermark.MAX_WATERMARK);
                }
            } catch (Exception e) {
                LOG.warn("Failed to emit MAX_WATERMARK during source shutdown", e);
            }
            if (outputWriter != null) {
                outputWriter.close();
            }
            operatorChain.close();
        }
        if (sourceError != null) {
            throw sourceError;
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeMiddle() throws Exception {
        operatorChain.open();
        Exception inputError = null;
        try {
            if (headInput != null) {
                try {
                    processInputGate(headInput);
                } catch (Exception e) {
                    inputError = e;
                }
                // P1-5: finish() before MAX_WATERMARK and close so connectors flush.
                if (inputError == null) {
                    headInput.processWatermark(Watermark.MAX_WATERMARK);
                    operatorChain.finish();
                }
            }
        } finally {
            if (outputWriter != null) {
                outputWriter.close();
            }
            operatorChain.close();
        }
        if (inputError != null) {
            throw inputError;
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeSink() throws Exception {
        operatorChain.open();
        Exception inputError = null;
        try {
            if (headInput != null) {
                try {
                    processInputGate(headInput);
                } catch (Exception e) {
                    inputError = e;
                }
                // P1-5: finish() before MAX_WATERMARK and close so connectors flush.
                if (inputError == null) {
                    headInput.processWatermark(Watermark.MAX_WATERMARK);
                    operatorChain.finish();
                }
            }
        } finally {
            operatorChain.close();
        }
        if (inputError != null) {
            throw inputError;
        }
    }

    private void invokeSelfContained() throws Exception {
        operatorChain.open();
        // G52: liveness marker for SELF_CONTAINED at the start of run.
        markProgress();
        Exception sourceError = null;
        try {
            List<StreamOperator<?>> operators = operatorChain.getOperators();
            StreamOperator<?> head = operators.get(0);

            if (head instanceof StreamSourceOperator) {
                StreamSourceOperator<?> sourceOp = (StreamSourceOperator<?>) head;
                if (sourceOp.getOutput() != null) {
                    try {
                        sourceOp.run();
                    } catch (Exception e) {
                        sourceError = e;
                    }
                    // G52: mark progress after run (covers batched emission).
                    markProgress();
                    if (sourceError == null) {
                        // P1-5: finish() before MAX_WATERMARK and close.
                        operatorChain.finish();
                        sourceOp.processWatermark(Watermark.MAX_WATERMARK);
                    }
                }
            }
        } finally {
            operatorChain.close();
        }
        if (sourceError != null) {
            throw sourceError;
        }
    }

    @SuppressWarnings("unchecked")
    private void processInputGate(Input<Object> headInput) throws Exception {
        while (true) {
            // Control-plane drain at the top of the main loop: process any pending
            // control mails (cancel marker, future processing-time timer) and observe
            // the cooperative cancel flag so abort exits gracefully instead of relying
            // solely on InterruptedException from interrupt. Barrier/element processing
            // below stays in-line and unchanged.
            if (mailboxExecutor.processAvailableMails()) {
                LOG.info("Task {} exiting main loop after cooperative cancel", getRole());
                break;
            }

            Optional<StreamElement> elementOpt = inputGate.read();
            if (!elementOpt.isPresent()) {
                break;
            }

            // G52: per-iteration liveness marker for MIDDLE/SINK roles.
            markProgress();

            StreamElement element = elementOpt.get();
            if (element.isRecord()) {
                headInput.processElement((StreamRecord<Object>) (StreamRecord<?>) element.asRecord());
            } else if (element.isWatermark()) {
                headInput.processWatermark(element.asWatermark());
            } else if (element.isCheckpointBarrier()) {
                headInput.processBarrier(element.asCheckpointBarrier());
            } else if (element.isWatermarkStatus()) {
                headInput.processWatermarkStatus(element.asWatermarkStatus());
            }
        }
    }

    public enum TaskRole {
        SOURCE,
        MIDDLE,
        SINK,
        SELF_CONTAINED
    }

    private static class RecordWriterOutput implements Output<StreamRecord<Object>> {

        private final RecordWriter<Object> writer;

        RecordWriterOutput(RecordWriter<Object> writer) {
            this.writer = writer;
        }

        @Override
        public void collect(StreamRecord<Object> record) {
            // Cross-task exchange queues the record for asynchronous consumption by a
            // different task. The producer operator may reuse a single StreamRecord
            // instance (TimestampedCollector), so snapshot it here; otherwise
            // subsequent collect() calls mutate the queued object and every queued
            // entry ends up holding the last emitted value.
            writer.emit(record.copy(record.getValue()));
        }

        @Override
        public void close() {
            // RecordWriter lifecycle is managed by invoke()
        }

        @Override
        public void emitWatermark(Watermark mark) {
            writer.emitWatermark(mark);
        }

        @Override
        public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
            // Not forwarded across task boundaries
        }

        @Override
        public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            // Side outputs not supported in cross-task exchange
        }

        @Override
        public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
            // Latency markers not forwarded across task boundaries
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
            writer.emitBarrier(barrier);
        }
    }

    private static class BroadcastingRecordWriterOutput implements Output<StreamRecord<Object>> {
        private final List<Output<StreamRecord<Object>>> outputs;

        BroadcastingRecordWriterOutput(List<Output<StreamRecord<Object>>> outputs) {
            this.outputs = outputs;
        }

        @Override
        public void collect(StreamRecord<Object> record) {
            for (Output<StreamRecord<Object>> output : outputs) {
                output.collect(record);
            }
        }

        @Override
        public void close() {
            Exception firstError = null;
            for (Output<StreamRecord<Object>> output : outputs) {
                try {
                    output.close();
                } catch (Exception e) {
                    if (firstError == null) {
                        firstError = e;
                    } else {
                        firstError.addSuppressed(e);
                    }
                }
            }
            if (firstError != null) {
                throw new StreamException(ERR_STREAM_CHAINING_OUTPUT_CLOSE_FAILED, firstError);
            }
        }

        @Override
        public void emitWatermark(Watermark mark) {
            for (Output<StreamRecord<Object>> output : outputs) {
                output.emitWatermark(mark);
            }
        }

        @Override
        public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
        }

        @Override
        public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
        }

        @Override
        public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
            for (Output<StreamRecord<Object>> output : outputs) {
                output.emitBarrier(barrier);
            }
        }
    }
}
