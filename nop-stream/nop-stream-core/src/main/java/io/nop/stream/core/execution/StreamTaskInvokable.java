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
import static io.nop.stream.core.exceptions.NopStreamErrors.*;

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
    }

    public StreamTaskInvokable(OperatorChain operatorChain, List<RecordWriter<Object>> fanOutWriters) {
        if (operatorChain == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorChain");
        }
        this.operatorChain = operatorChain;
        this.outputWriter = !fanOutWriters.isEmpty() ? fanOutWriters.get(0) : null;
        this.inputGate = null;
        wireOperators(fanOutWriters);
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
    }

    public CheckpointBarrierTracker getBarrierTracker() {
        return barrierTracker;
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
        try {
            List<StreamOperator<?>> operators = operatorChain.getOperators();
            StreamOperator<?> head = operators.get(0);

            if (head instanceof StreamSourceOperator) {
                StreamSourceOperator<?> sourceOp = (StreamSourceOperator<?>) head;
                if (sourceOp.getOutput() != null) {
                    sourceOp.run();
                }
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
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeMiddle() throws Exception {
        try {
            if (headInput != null) {
                processInputGate(headInput);
                headInput.processWatermark(Watermark.MAX_WATERMARK);
            }
        } finally {
            if (outputWriter != null) {
                outputWriter.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeSink() throws Exception {
        if (headInput != null) {
            processInputGate(headInput);
            headInput.processWatermark(Watermark.MAX_WATERMARK);
        }
    }

    private void invokeSelfContained() throws Exception {
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

    @SuppressWarnings("unchecked")
    private void processInputGate(Input<Object> headInput) throws Exception {
        while (true) {
            Optional<StreamElement> elementOpt = inputGate.read();
            if (!elementOpt.isPresent()) {
                break;
            }

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
            writer.emit(record);
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
