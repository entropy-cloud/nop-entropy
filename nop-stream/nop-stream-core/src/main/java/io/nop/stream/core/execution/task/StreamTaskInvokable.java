/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.exceptions.StreamRuntimeException;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.MailboxExecutor;
import io.nop.stream.core.execution.ProcessingTimeServiceDriver;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.TaskProcessingTimeService;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.ChainingOutput;
import io.nop.stream.core.operators.Input;
import io.nop.stream.core.operators.KeyContext;
import io.nop.stream.core.operators.KeyExtractingOutput;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.operators.SourceReaderOperator;
import io.nop.stream.core.operators.TimerServiceManager;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.SideOutputElement;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.util.OutputTag;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OUTPUT_TAG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHAINING_OUTPUT_CLOSE_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER;

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

    /**
     * P1-03: the FULL list of fan-out writers (one per outgoing edge), kept for
     * close-time traversal. The fan-out constructors previously discarded this
     * list after wiring the tail operator, keeping only {@code outputWriter}
     * (= {@code fanOutWriters.get(0)}), so edge 2..N's EOS was never signalled
     * and bounded fan-out jobs hung their downstream sinks. Null for
     * non-fan-out roles.
     */
    private final List<RecordWriter<Object>> fanOutWriters;

    private final InputGate inputGate;

    /**
     * Per-task mailbox executor: the control-plane anchor for this task thread.
     * Holds the {@link io.nop.stream.core.execution.TaskMailbox} (multi-producer, single-consumer) and the cooperative
     * cancel flag. See {@code ai-dev/design/nop-stream/mailbox-design.md}.
     *
     * <p>SOURCE/SELF_CONTAINED: the head source operator's trigger-checkpoint mails are
     * delivered here and drained at the {@code SourceContext.collect()} emission point.
     * MIDDLE/SINK: the main loop ({@code processInputGate}) polls this at the top of each
     * iteration. The abort handler delivers a cancel mail + raises the cancel flag here.
     */
    private final transient MailboxExecutor mailboxExecutor = new MailboxExecutor();

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

    /**
     * G52 / AR-01: task-thread aliveness timestamp. Updated at every loop
     * iteration of {@link #processInputGate} — <b>including idle
     * iterations</b> (the AR-02 idle-return path cycles back to the loop
     * top) — so a healthy but data-idle MIDDLE/SINK task keeps a fresh
     * aliveness while a genuinely hung task (thread stuck in user code, loop
     * no longer progressing) ages out. Reported by
     * {@code TaskManager.heartbeat()} as the liveness signal for MIDDLE/SINK
     * roles. SOURCE/SELF_CONTAINED roles report the TM-side wall clock
     * instead (their run loop may legitimately block for the whole source
     * lifetime; data progress is decoupled from liveness there).
     *
     * <p>Volatile because the writer is the task thread and the reader is the
     * heartbeat thread; only ever assigned monotonically non-decreasing values.
     */
    private volatile long lastActivityTime = System.currentTimeMillis();

    private CheckpointBarrierTracker barrierTracker;

    private Input<Object> headInput;

    /**
     * Item 16 (P-REQ-1 operator/io layers): per-task data-plane metrics handle.
     * Serializable holder delegating to {@link io.nop.stream.core.metrics.StreamTaskMetrics#NOOP}
     * until the runtime injects a real implementation (both LOCAL and REMOTE
     * execution paths do so). Shared by reference with the wired
     * {@link RecordWriterOutput}s so a post-injection delegate swap is visible
     * everywhere.
     */
    private final io.nop.stream.core.metrics.TaskMetricsHandle taskMetrics =
            new io.nop.stream.core.metrics.TaskMetricsHandle();

    /**
     * RL-7 (R15-AR-4): side-output consumers shared by all ChainingOutputs this task wires.
     * Registration may happen before or after wiring (the map reference is shared). A side
     * output without a registered consumer fails fast instead of being silently dropped.
     */
    private final Map<OutputTag<?>, Consumer<StreamRecord<?>>> sideOutputConsumers = new HashMap<>();

    /**
     * Production {@link ProcessingTimeService} wired into every operator of this task's chain.
     * Created in the constructor (before any operator {@code open()} can run), driven by
     * {@link #processingTimeDriver} which is started at {@link #invoke()} and stopped in its
     * {@code finally}. Never null after construction.
     */
    private transient TaskProcessingTimeService processingTimeService;

    /**
     * Production {@link TimerServiceManager} wired into every operator of this task's chain.
     * Operators register their {@code HeapInternalTimerService} in {@code open()}; the driver
     * fires due processing-time timers through this manager. Never null after construction.
     */
    private transient TimerServiceManager timeServiceManager;

    /**
     * Scheduler thread that delivers processing-time fire mails to this task's mailbox.
     * Started unconditionally at {@link #invoke()} (never on the conditional
     * setBarrierTracker/setupSnapshotCallbacks path — the local {@code env.execute()} path
     * never creates a barrier tracker), stopped in {@code invoke()}'s {@code finally}.
     */
    private transient ProcessingTimeServiceDriver processingTimeDriver;

    public StreamTaskInvokable(OperatorChain operatorChain) {
        if (operatorChain == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorChain");
        }
        this.operatorChain = operatorChain;
        this.outputWriter = null;
        this.fanOutWriters = null;
        this.inputGate = null;
        wireOperators();
        wireMailboxToHeadSource();
        setupProcessingTimeServices();
    }

    public StreamTaskInvokable(OperatorChain operatorChain, List<RecordWriter<Object>> fanOutWriters) {
        if (operatorChain == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorChain");
        }
        this.operatorChain = operatorChain;
        this.outputWriter = !fanOutWriters.isEmpty() ? fanOutWriters.get(0) : null;
        this.fanOutWriters = fanOutWriters;
        this.inputGate = null;
        wireOperators(fanOutWriters);
        wireMailboxToHeadSource();
        setupProcessingTimeServices();
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
        this.fanOutWriters = null;
        this.inputGate = inputGate;
        wireOperators();
        wireMailboxToHeadSource();
        setupProcessingTimeServices();
    }

    public StreamTaskInvokable(OperatorChain operatorChain,
                               List<RecordWriter<Object>> fanOutWriters,
                               InputGate inputGate) {
        if (operatorChain == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorChain");
        }
        this.operatorChain = operatorChain;
        this.outputWriter = !fanOutWriters.isEmpty() ? fanOutWriters.get(0) : null;
        this.fanOutWriters = fanOutWriters;
        this.inputGate = inputGate;
        wireOperators(fanOutWriters);
        wireMailboxToHeadSource();
        setupProcessingTimeServices();
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

                currentOp.setOutput(new ChainingOutput<>(wiredInput, null, sideOutputConsumers));
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

                currentOp.setOutput(new ChainingOutput<>(wiredInput, null, sideOutputConsumers));
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
                    op.setOutput(new RecordWriterOutput(fanOutWriters.get(0), taskMetrics));
                } else {
                    List<Output<StreamRecord<Object>>> outputs = new ArrayList<>();
                    for (RecordWriter<Object> writer : fanOutWriters) {
                        outputs.add(new RecordWriterOutput(writer, taskMetrics));
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
                // Item 16 (P-REQ-1 io layer): source-side consumption counter.
                sourceOp.setRecordCounter(taskMetrics::recordsConsumed);
            } else if (head instanceof SourceReaderOperator) {
                // Stage 49 D5: new FLIP-27 style source path — wire the mailbox so
                // barrier / cancel mails are delivered to the SourceReaderOperator's
                // task thread.
                SourceReaderOperator<?> readerOp = (SourceReaderOperator<?>) head;
                readerOp.setMailboxExecutor(mailboxExecutor);
                readerOp.setRecordCounter(taskMetrics::recordsConsumed);
            }
        }
    }

    public CheckpointBarrierTracker getBarrierTracker() {
        return barrierTracker;
    }

    /**
     * Item 16: injects this task's data-plane metrics implementation. Must be
     * called before {@code invoke()} on the real execution paths; a task
     * without injection keeps NOOP behavior (serialization-safe).
     */
    public void setTaskMetrics(io.nop.stream.core.metrics.StreamTaskMetrics metrics) {
        this.taskMetrics.setDelegate(metrics);
    }

    public io.nop.stream.core.metrics.TaskMetricsHandle getTaskMetricsHandle() {
        return taskMetrics;
    }

    /**
     * @return the production {@link TimerServiceManager} wired into this task's operators;
     *         never null. Intended for runtime wiring assertions and diagnostics.
     */
    public TimerServiceManager getTimeServiceManager() {
        return timeServiceManager;
    }

    /**
     * @return the production {@link ProcessingTimeService} wired into this task's operators;
     *         never null. Intended for runtime wiring assertions and diagnostics.
     */
    public ProcessingTimeService getProcessingTimeService() {
        return processingTimeService;
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
     * RL-7 (R15-AR-4): registers a consumer for a side-output tag in the chained execution.
     * All ChainingOutputs wired by this task share the consumer map, so registration may
     * happen before or after {@code wireOperators}. Without a registered consumer, emitting
     * a side output fails fast ({@code ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER}) instead of
     * silently dropping the record.
     */
    @SuppressWarnings("unchecked")
    public <X> void registerSideOutputConsumer(OutputTag<X> outputTag,
                                               Consumer<StreamRecord<X>> consumer) {
        sideOutputConsumers.put(outputTag, (Consumer<StreamRecord<?>>) (Consumer<?>) consumer);
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

    /**
     * G52 / AR-01: task-thread aliveness timestamp. Fresh while the task's main
     * loop keeps cycling (data or idle); ages only when the task thread is
     * genuinely stuck and no longer reaches the loop top.
     *
     * @return monotonic timestamp of the last task-thread loop activity; never
     *         decreases
     */
    public long getLastActivityTime() {
        return lastActivityTime;
    }

    /**
     * G52 / AR-01: marks a task-thread aliveness event. Called at the top of
     * every {@link #processInputGate} loop iteration (idle and data) and at
     * {@code invoke()} role start points. Idempotent and thread-safe (volatile
     * assignment from the task thread only).
     */
    public void markActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public OperatorChain getOperatorChain() {
        return operatorChain;
    }

    public RecordWriter<Object> getOutputWriter() {
        return outputWriter;
    }

    /**
     * @return the full fan-out writer list (one per outgoing edge), or null
     *         when this task is not a fan-out producer. P1-03: the restart
     *         path reuses the whole list so a rebuilt fan-out producer keeps
     *         feeding every edge.
     */
    public List<RecordWriter<Object>> getFanOutWriters() {
        return fanOutWriters;
    }

    public InputGate getInputGate() {
        return inputGate;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void wireTailToRecordWriter(List<StreamOperator<?>> operators, int lastIndex) {
        StreamOperator<?> tail = operators.get(lastIndex);
        if (tail instanceof AbstractStreamOperator) {
            AbstractStreamOperator op = (AbstractStreamOperator) tail;
            op.setOutput(new RecordWriterOutput(outputWriter, taskMetrics));
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

    /**
     * Injects the production {@link ProcessingTimeService} and {@link TimerServiceManager}
     * into every operator of this task's chain.
     *
     * <p>This runs in the constructor — BEFORE any {@code operatorChain.open()} can run
     * (both {@link SubtaskTask} and {@link Task} open the chains before {@link #invoke()}).
     * It is unconditional: it does NOT live on the {@code setBarrierTracker} /
     * {@code setupSnapshotCallbacks} path, which the local {@code env.execute()} path never
     * reaches (no {@code CheckpointBarrierTracker} is created there).
     *
     * <p>The service OBJECTS are created here; the scheduler thread (driver) is started
     * separately at {@link #invoke()} so that a constructed-but-never-invoked invokable
     * does not leak a thread.
     */
    private void setupProcessingTimeServices() {
        TaskProcessingTimeService pts = new TaskProcessingTimeService();
        TimerServiceManager tsm = new TimerServiceManager();
        for (StreamOperator<?> op : operatorChain.getOperators()) {
            if (op instanceof AbstractStreamOperator) {
                AbstractStreamOperator<?> abstractOp = (AbstractStreamOperator<?>) op;
                abstractOp.setProcessingTimeService(pts);
                abstractOp.setTimeServiceManager(tsm);
            }
        }
        this.processingTimeService = pts;
        this.timeServiceManager = tsm;
    }

    /**
     * Starts the processing-time driver (daemon scheduler thread) unconditionally at the
     * start of {@link #invoke()}, before the invoke-level {@code operatorChain.open()}.
     * Idempotent.
     */
    private void startProcessingTimeDriver() {
        if (processingTimeDriver == null) {
            processingTimeDriver = new ProcessingTimeServiceDriver(
                    mailboxExecutor.getMailbox(), processingTimeService, timeServiceManager);
            processingTimeDriver.start();
        }
    }

    /**
     * Stops the processing-time driver in {@code invoke()}'s {@code finally}. Idempotent.
     */
    private void stopProcessingTimeDriver() {
        if (processingTimeDriver != null) {
            processingTimeDriver.shutdown();
            processingTimeDriver = null;
        }
    }

    /**
     * P1-03: closes ALL output writers of this task. A fan-out producer (2+
     * outgoing edges) must signal EOS on every edge — closing only
     * {@link #outputWriter} (edge 0) leaves edges 2..N open, so their
     * downstream sinks poll forever and a bounded fan-out job never
     * terminates.
     *
     * <p>The explicit traversal is deliberate:
     * {@code BroadcastingRecordWriterOutput} (the tail operator's fan-out
     * output) cannot do this job — its {@code close()} delegates to
     * {@code RecordWriterOutput.close()}, a no-op ("RecordWriter lifecycle
     * is managed by invoke()"), so closing via the operator output would
     * silently skip every writer (no-silent-skip, plan guide #24). Every
     * writer in the list is attempted; the first failure is rethrown with
     * the rest suppressed, mirroring {@link RecordWriter#close()} semantics.
     */
    private void closeOutputWriters() {
        if (fanOutWriters != null && !fanOutWriters.isEmpty()) {
            Exception firstError = null;
            for (RecordWriter<Object> writer : fanOutWriters) {
                try {
                    writer.close();
                } catch (Exception e) {
                    if (firstError == null) {
                        firstError = e;
                    } else {
                        firstError.addSuppressed(e);
                    }
                }
            }
            if (firstError != null) {
                if (firstError instanceof StreamException) {
                    throw (StreamException) firstError;
                }
                if (firstError instanceof RuntimeException) {
                    throw (RuntimeException) firstError;
                }
                throw new StreamException(
                        ERR_STREAM_CHAINING_OUTPUT_CLOSE_FAILED, firstError);
            }
        } else if (outputWriter != null) {
            outputWriter.close();
        }
    }

    @Override
    public void invoke() throws Exception {
        startProcessingTimeDriver();
        try {
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
        } finally {
            stopProcessingTimeDriver();
        }
    }

    private void invokeSource() throws Exception {
        operatorChain.open();
        // G52: liveness marker for the SOURCE role at the start of the run loop.
        // SourceContext.collect() (the per-record emission path) also marks progress;
        // this initial marker covers a slow-start source that has not emitted yet.
        markProgress();
        // G52 / AR-01: task-thread aliveness at role start. SOURCE liveness is
        // reported by the TM as wall clock (see TaskManager.heartbeat), so this
        // marker is diagnostic-only for the blocking-source path.
        markActivity();
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
            } else if (head instanceof SourceReaderOperator) {
                // Stage 49 D5: FLIP-27 style source path. Drive SourceReaderOperator.run()
                // which polls SourceReader.pollNext() until the reader signals isFinished().
                SourceReaderOperator<?> readerOp = (SourceReaderOperator<?>) head;
                try {
                    readerOp.run();
                } catch (Exception e) {
                    sourceError = e;
                }
                markProgress();
            }

            // P1-5: finish() must run after the source returns and BEFORE the
            // MAX_WATERMARK is emitted and operators are closed. Without this,
            // connectors that buffer (e.g. BatchConsumerSinkFunction) silently
            // dropped the tail batch on bounded source EOS.
            if (sourceError == null) {
                operatorChain.finish();
            }
        } finally {
            // Stage 44 successor 4 Phase 2 (producer-region restart): only close
            // the output on SUCCESSFUL completion (signal EOS). On failure, keep
            // the output partition open so a restarted producer can continue
            // writing to the same partition (with the same materialization point).
            // Closing on failure would force EOS and make producer-region restart
            // impossible — the restarted producer could not emit any data. When
            // the job goes to global recovery instead, fresh partitions are built
            // anyway, so an open partition here is harmless.
            if (sourceError == null) {
                try {
                    List<StreamOperator<?>> operators = operatorChain.getOperators();
                    StreamOperator<?> head = operators.get(0);
                    if (head instanceof StreamSourceOperator) {
                        ((StreamSourceOperator<?>) head).processWatermark(Watermark.MAX_WATERMARK);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to emit MAX_WATERMARK during source shutdown", e);
                }
                closeOutputWriters();
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
        // G52 / AR-01: task-thread aliveness at role start (the loop top tick
        // in processInputGate keeps it fresh afterwards, incl. idle iter.).
        markActivity();
        Exception inputError = null;
        InputLoopExitReason exitReason = null;
        try {
            if (headInput != null) {
                try {
                    exitReason = processInputGate(headInput);
                } catch (Exception e) {
                    inputError = e;
                }
                // Only a TRUE end-of-stream exit is a successful completion.
                // AR-7 (plan 1326-2 Phase 3): cancel/interrupt exits must NOT run the
                // success terminal state (finish + MAX_WATERMARK) — the truncated
                // stream must not be finalized as a bounded-complete one. AR-8: the
                // success path runs finish() BEFORE MAX_WATERMARK (P1-5 contract,
                // aligned with SOURCE/SELF_CONTAINED) so buffered operators' tail
                // batches reach downstream windows before the final watermark fires.
                if (inputError == null && exitReason == InputLoopExitReason.END_OF_STREAM) {
                    operatorChain.finish();
                    headInput.processWatermark(Watermark.MAX_WATERMARK);
                }
            }
        } finally {
            // AR-7: mirror invokeSource's failure-preserving output policy — only
            // signal EOS downstream on SUCCESSFUL completion. On error/cancel/
            // interrupt (and, by construction, on a thrown Error, which skips the
            // reason bookkeeping entirely) the output partition stays open so a
            // producer-region restart can continue writing to the same partition.
            if (inputError == null && exitReason == InputLoopExitReason.END_OF_STREAM) {
                closeOutputWriters();
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
        // G52 / AR-01: task-thread aliveness at role start (see invokeMiddle).
        markActivity();
        Exception inputError = null;
        InputLoopExitReason exitReason = null;
        try {
            if (headInput != null) {
                try {
                    exitReason = processInputGate(headInput);
                } catch (Exception e) {
                    inputError = e;
                }
                // AR-7: cancelled/interrupted SINK tasks must not finalize the
                // truncated stream (no finish, no MAX_WATERMARK) — e.g. a 2PC sink's
                // flush/commit window must not run on a cancelled task. AR-8: on the
                // success path finish() runs BEFORE MAX_WATERMARK (P1-5 contract).
                if (inputError == null && exitReason == InputLoopExitReason.END_OF_STREAM) {
                    operatorChain.finish();
                    headInput.processWatermark(Watermark.MAX_WATERMARK);
                }
            }
        } finally {
            // SINK has no downstream writer (no closeOutputWriters call — unchanged);
            // the cancellation distinction lives in the success-terminal guard above.
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
        // G52 / AR-01: task-thread aliveness at role start (see invokeSource).
        markActivity();
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
            } else if (head instanceof SourceReaderOperator) {
                // Stage 49 D5: FLIP-27 style source path in SELF_CONTAINED role
                // (single-subtask in-process execution).
                SourceReaderOperator<?> readerOp = (SourceReaderOperator<?>) head;
                try {
                    readerOp.run();
                } catch (Exception e) {
                    sourceError = e;
                }
                markProgress();
                if (sourceError == null) {
                    operatorChain.finish();
                }
            }
        } finally {
            operatorChain.close();
        }
        if (sourceError != null) {
            throw sourceError;
        }
    }

    /**
     * AR-7 (plan 1326-2 Phase 3): the input loop's exit reason. Previously EOS,
     * thread-interrupt and cooperative-cancel all fell into the same {@code break},
     * and invokeMiddle/invokeSink then ran the SUCCESS terminal state (finish +
     * MAX_WATERMARK + EOS downstream) on all of them — a cancelled task committed
     * its truncated stream as a bounded-complete one.
     */
    enum InputLoopExitReason {
        /** All upstream channels finished — bounded-complete input, success terminal state applies. */
        END_OF_STREAM,
        /** Cooperative cancel mail observed (abort handler / supervisor). */
        CANCELLED,
        /** Thread interrupted (cancel path); the interrupt flag is left set. */
        INTERRUPTED
    }

    @SuppressWarnings("unchecked")
    private InputLoopExitReason processInputGate(Input<Object> headInput) throws Exception {
        while (true) {
            // G52 / AR-01: task-thread aliveness tick at the top of every loop
            // iteration, INCLUDING idle iterations (the AR-02 idle-return path
            // cycles back here). A healthy idle task keeps this fresh; a hung
            // task (thread stuck in user code, no data, no loop progress) stops
            // ticking and ages out past taskTimeoutMs at the coordinator.
            markActivity();

            // Control-plane drain at the top of the main loop: process any pending
            // control mails (cancel marker, future processing-time timer) and observe
            // the cooperative cancel flag so abort exits gracefully instead of relying
            // solely on InterruptedException from interrupt. Barrier/element processing
            // below stays in-line and unchanged.
            if (mailboxExecutor.processAvailableMails()) {
                LOG.info("Task {} exiting main loop after cooperative cancel", getRole());
                return InputLoopExitReason.CANCELLED;
            }

            Optional<StreamElement> elementOpt = inputGate.read();
            if (!elementOpt.isPresent()) {
                // AR-02 (P1): InputGate now returns empty for BOTH end-of-stream
                // and momentary idle (idle-return threshold, see
                // InputGate.IDLE_RETURN_THRESHOLD_MS). Only a true EOS — all
                // channels finished — terminates the loop; an idle return cycles
                // back to the loop top, where processAvailableMails() drains any
                // pending control mails (e.g. processing-time timer fire mails)
                // before re-reading. Without this, an idle task (no data flow)
                // never reached the mailbox drain and processing-time timers
                // never fired (AR-02).
                //
                // AR-7: the exit reasons are now distinguished so the callers can
                // skip the success terminal state on cancel/interrupt.
                if (inputGate.isAllFinished()) {
                    return InputLoopExitReason.END_OF_STREAM;
                }
                if (Thread.currentThread().isInterrupted()) {
                    return InputLoopExitReason.INTERRUPTED;
                }
                if (mailboxExecutor.isCancelled()) {
                    return InputLoopExitReason.CANCELLED;
                }
                continue;
            }

            // G52: per-iteration liveness marker for MIDDLE/SINK roles.
            markProgress();

            StreamElement element = elementOpt.get();
            if (element.isRecord()) {
                // Item 16 (P-REQ-1 operator layer): record dispatch into the
                // operator chain + per-record chain processing time.
                taskMetrics.recordsIn(1);
                long metricsStart = System.nanoTime();
                try {
                    headInput.processElement((StreamRecord<Object>) (StreamRecord<?>) element.asRecord());
                } finally {
                    taskMetrics.processingTime(System.nanoTime() - metricsStart);
                }
            } else if (element.isSideOutput()) {
                // HG-01 (2026-08-14): cross-task side-output routing. Look up the registered
                // consumer by tag id (Phase 1 decision D4 — OutputTag's ctor forbids a
                // null-typeInfo lookup key, so iterate sideOutputConsumers keySet with
                // getId() equality). Unmatched tag = fail-fast at the consumption side.
                io.nop.stream.core.streamrecord.SideOutputElement side = element.asSideOutput();
                String tagId = side.getOutputTagId();
                io.nop.stream.core.util.OutputTag<?> matched = null;
                for (io.nop.stream.core.util.OutputTag<?> tag : sideOutputConsumers.keySet()) {
                    if (tag.getId().equals(tagId)) {
                        matched = tag;
                        break;
                    }
                }
                if (matched == null) {
                    throw new StreamRuntimeException(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER)
                            .param(ARG_OUTPUT_TAG, tagId)
                            .param(ARG_DETAIL, "No registered side-output consumer for tag '"
                                    + tagId + "' on task " + getRole());
                }
                sideOutputConsumers.get(matched).accept(side.getRecord());
            } else if (element.isWatermark()) {
                headInput.processWatermark(element.asWatermark());
            } else if (element.isCheckpointBarrier()) {
                // Stage 43 (unaligned checkpoint): if InputGate just switched to
                // unaligned mode, it stashed captured in-flight channel state.
                // Forward it to the tracker BEFORE processBarrier triggers operator
                // snapshots, so the channel state rides the barrier ACK path onto
                // the current TaskStateSnapshot alongside operator state.
                if (inputGate != null && barrierTracker != null) {
                    io.nop.stream.core.checkpoint.ChannelState channelState =
                            inputGate.consumePendingChannelState();
                    if (channelState != null) {
                        barrierTracker.setChannelState(channelState);
                    }
                }
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

        /**
         * Item 16 (P-REQ-1): shared metrics handle of the owning invokable —
         * counts operator recordsOut + io recordsEmitted at the cross-task
         * emission point.
         */
        private final io.nop.stream.core.metrics.TaskMetricsHandle taskMetrics;

        RecordWriterOutput(RecordWriter<Object> writer,
                           io.nop.stream.core.metrics.TaskMetricsHandle taskMetrics) {
            this.writer = writer;
            this.taskMetrics = taskMetrics;
        }

        /**
         * Pinned-contract constructor (output-behavior invariant harness): builds
         * an output without metrics wiring — identical emission behavior, no
         * counting.
         */
        RecordWriterOutput(RecordWriter<Object> writer) {
            this(writer, new io.nop.stream.core.metrics.TaskMetricsHandle());
        }

        @Override
        public void collect(StreamRecord<Object> record) {
            // Cross-task exchange queues the record for asynchronous consumption by a
            // different task. The producer operator may reuse a single StreamRecord
            // instance (TimestampedCollector), so snapshot it here; otherwise
            // subsequent collect() calls mutate the queued object and every queued
            // entry ends up holding the last emitted value.
            taskMetrics.recordsOut(1);
            taskMetrics.recordsEmitted(1);
            // Item 16 (P-REQ-1 io layer): time the emission itself — a blocked
            // emit (downstream exchange queue full) shows up here, making this
            // the producer-side backpressure proxy.
            long emitStart = System.nanoTime();
            writer.emit(record.copy(record.getValue()));
            taskMetrics.emitTime(System.nanoTime() - emitStart);
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
            // AR-9 (plan 1326-2 Phase 4): watermark status now crosses task boundaries —
            // an idle upstream task's status lets the downstream InputGate exclude the
            // idle channel from the min watermark merge instead of pinning event time.
            writer.emitWatermarkStatus(status);
        }

        @Override
        public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            // HG-01 (2026-08-14, I4 replacement): the cross-task wire protocol now carries
            // side outputs — wrap the tagged record in a SideOutputElement and broadcast it
            // through RecordWriter.emitElement to ALL downstream partitions (Phase 1
            // decision D3). The inner record is copied so the producer's reused StreamRecord
            // instance is never aliased by the exchange queue (D5). No-consumer fail-fast
            // moved to the consumption-side routing point in processInputGate.
            writer.emitElement(new SideOutputElement(outputTag.getId(),
                    record.copy(record.getValue())));
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
            // AR-9 (plan 1326-2 Phase 4): fan the status out to every downstream edge
            // (mirrors emitWatermark / emitBarrier broadcast semantics).
            for (Output<StreamRecord<Object>> output : outputs) {
                output.emitWatermarkStatus(status);
            }
        }

        @Override
        public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            // HG-01 (2026-08-14, I4 replacement): fan the tagged record out to every wrapped
            // output. Each RecordWriterOutput copies the inner record on construction, so no
            // single SideOutputElement instance is shared across partition queues (D5).
            for (Output<StreamRecord<Object>> output : outputs) {
                output.collect(outputTag, record);
            }
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
