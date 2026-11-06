/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.task;

import io.nop.api.core.time.CoreMetrics;
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
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.exceptions.StreamRuntimeException;
import io.nop.stream.core.execution.CheckpointBarrierTracker;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.MailboxExecutor;
import io.nop.stream.core.execution.ProcessingTimeServiceDriver;
import io.nop.stream.core.execution.CloseSupport;
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
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHAINING_OUTPUT_EXCEPTION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
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
     * The FULL list of fan-out writers (one per outgoing edge), kept for
     * close-time traversal. Closing only {@code outputWriter}
     * (= {@code fanOutWriters.get(0)}) would leave edges 2..N open — their
     * EOS never signalled, hanging the downstream sinks of a bounded fan-out
     * job. Null for non-fan-out roles.
     */
    private final List<RecordWriter<Object>> fanOutWriters;

    private final InputGate inputGate;

    /**
     * WI8 Phase 2 (plan 2332): per-edge side-output tag ids aligned with
     * {@link #fanOutWriters} (index-for-index; {@code null} entries = main edges,
     * non-null = tag edges carrying only their own tag id). {@code null} list = no
     * tag information (legacy constructors) — the tail output keeps the historical
     * HG-01 broadcast semantics exactly.
     */
    private final List<String> fanOutWriterTagIds;

    /**
     * WI8 Phase 2 (plan 2332): the tag id of the single-writer edge when that edge is
     * a tag edge; {@code null} for legacy single-writer tasks (main edge semantics).
     */
    private final String singleWriterTagId;

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
     * Per-invokable liveness timestamp. Updated at every data-plane progress
     * point (record emission for SOURCE/SELF_CONTAINED via {@link #markProgress()},
     * input-gate iteration for MIDDLE/SINK inside {@link #processInputGate}).
     * Read by {@code TaskManager.heartbeat()} via {@link #getLastProgressTime()}
     * and reported to the coordinator piggybacked on the existing node heartbeat.
     *
     * <p>Volatile because the writer is the task thread and the reader is the
     * heartbeat thread; only ever assigned monotonically non-decreasing values.
     */
    private volatile long lastProgressTime = CoreMetrics.currentTimeMillis();

    /**
     * Task-thread aliveness timestamp. Updated at every loop
     * iteration of {@link #processInputGate} — <b>including idle
     * iterations</b> (the idle-return path cycles back to the loop
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
    private volatile long lastActivityTime = CoreMetrics.currentTimeMillis();

    private CheckpointBarrierTracker barrierTracker;

    private Input<Object> headInput;

    /**
     * Per-task data-plane metrics handle.
     * Serializable holder delegating to {@link io.nop.stream.core.metrics.StreamTaskMetrics#NOOP}
     * until the runtime injects a real implementation (both LOCAL and REMOTE
     * execution paths do so). Shared by reference with the wired
     * {@link RecordWriterOutput}s so a post-injection delegate swap is visible
     * everywhere.
     */
    private final io.nop.stream.core.metrics.TaskMetricsHandle taskMetrics =
            new io.nop.stream.core.metrics.TaskMetricsHandle();

    /**
     * Side-output consumers shared by all ChainingOutputs this task wires.
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

    /**
     * W-07 (CRA 8.42 R4-02, ruling C.2): the execution-context-local
     * SideOutputRegistry this task belongs to (the owning env's instance, injected
     * by the LOCAL plan-build chain; {@code null} on the remote/分进程 build path —
     * an explicit tightening: side-output read misses then answer the existing
     * NO_CONSUMER fail-fast, matching split-process semantics). The
     * cross-task {@link #sideOutputConsumerFor} fallback consults this instance
     * instead of a process-static map, so two executions on the same host can
     * never topple each other's same-tag consumers and a terminal execution's
     * registrations die with its env object graph.
     */
    private transient volatile io.nop.stream.core.datastream.SideOutputRegistry sideOutputRegistry;

    /**
     * The ChainingOutputs wired by {@link #chainInnerOperators()} — the
     * {@link #setSideOutputRegistry} injection point propagates the registry into
     * them (the wiring runs inside the constructor, before any injection).
     */
    private final List<ChainingOutput<?>> wiredChainingOutputs = new ArrayList<>();

    /** This task's execution-scoped side-output registry (null on the remote path). */
    public io.nop.stream.core.datastream.SideOutputRegistry getSideOutputRegistry() {
        return sideOutputRegistry;
    }

    /**
     * W-07 (ruling C.2): injects the owning env's side-output registry. Called by
     * the LOCAL plan-build chain right after construction (the constructor has no
     * registry parameter — the 20 main construction sites stay on their existing
     * signatures; SupervisionLoop transfers the OLD invokable's registry here, so
     * a region-restarted task keeps its execution scope).
     */
    public void setSideOutputRegistry(io.nop.stream.core.datastream.SideOutputRegistry registry) {
        this.sideOutputRegistry = registry;
        for (ChainingOutput<?> chained : wiredChainingOutputs) {
            chained.setSideOutputRegistry(registry);
        }
    }

    public StreamTaskInvokable(OperatorChain operatorChain) {
        if (operatorChain == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorChain");
        }
        this.operatorChain = operatorChain;
        this.outputWriter = null;
        this.fanOutWriters = null;
        this.inputGate = null;
        this.fanOutWriterTagIds = null;
        this.singleWriterTagId = null;
        wireOperators();
        wireMailboxToHeadSource();
        setupProcessingTimeServices();
    }

    public StreamTaskInvokable(OperatorChain operatorChain, List<RecordWriter<Object>> fanOutWriters) {
        this(operatorChain, fanOutWriters, null, null);
    }

    @SuppressWarnings("unchecked")
    public StreamTaskInvokable(OperatorChain operatorChain,
                               RecordWriter<?> outputWriter,
                               InputGate inputGate) {
        this(operatorChain, outputWriter, null, inputGate);
    }

    public StreamTaskInvokable(OperatorChain operatorChain,
                               List<RecordWriter<Object>> fanOutWriters,
                               InputGate inputGate) {
        this(operatorChain, fanOutWriters, null, inputGate);
    }

    /**
     * WI8 Phase 2 (plan 2332): tag-aware fan-out constructor. {@code writerTagIds} is
     * index-aligned with {@code fanOutWriters}: {@code null} entries are main edges,
     * non-null entries are side-output tag edges carrying only their own tag id. When
     * the vertex has at least one tag edge, tagged emissions route ONLY to the matching
     * tag edge (never to main edges) and main records never cross a tag edge — without
     * this discrimination a tag-fed consumer would receive each record twice.
     */
    public StreamTaskInvokable(OperatorChain operatorChain,
                               List<RecordWriter<Object>> fanOutWriters,
                               List<String> writerTagIds,
                               InputGate inputGate) {
        if (operatorChain == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorChain");
        }
        if (writerTagIds != null && writerTagIds.size() != fanOutWriters.size()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "writerTagIds")
                    .param(ARG_DETAIL, "writerTagIds must be index-aligned with fanOutWriters: writers="
                            + fanOutWriters.size() + " tagIds=" + writerTagIds.size());
        }
        this.operatorChain = operatorChain;
        this.outputWriter = !fanOutWriters.isEmpty() ? fanOutWriters.get(0) : null;
        this.fanOutWriters = fanOutWriters;
        this.inputGate = inputGate;
        this.fanOutWriterTagIds = writerTagIds;
        this.singleWriterTagId = null;
        wireOperators(fanOutWriters);
        wireMailboxToHeadSource();
        setupProcessingTimeServices();
    }

    /**
     * WI8 Phase 2 (plan 2332): tag-aware single-writer constructor. A non-null
     * {@code writerTagId} marks the edge as a side-output tag edge (carries only
     * {@code SideOutputElement} traffic of that tag id); {@code null} keeps the legacy
     * main-edge semantics.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public StreamTaskInvokable(OperatorChain operatorChain,
                               RecordWriter<?> outputWriter,
                               String writerTagId,
                               InputGate inputGate) {
        if (operatorChain == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "operatorChain");
        }
        this.operatorChain = operatorChain;
        this.outputWriter = (RecordWriter<Object>) outputWriter;
        this.fanOutWriters = null;
        this.inputGate = inputGate;
        this.fanOutWriterTagIds = null;
        this.singleWriterTagId = writerTagId;
        wireOperators();
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
        chainInnerOperators();
        wireHeadInput();
        if (outputWriter != null) {
            if (singleWriterTagId != null) {
                // WI8 Phase 2 (plan 2332): the only outgoing edge is a side-output tag
                // edge — main records never cross it, only SideOutputElement traffic
                // of the edge's tag id does.
                wireTailOperatorOutput(
                        new EdgeWriterOutput(outputWriter, singleWriterTagId, true, taskMetrics));
            } else {
                wireTailToRecordWriter(operatorChain.getOperators(), operatorChain.getOperators().size() - 1);
            }
        }
    }

    /** WI8 Phase 2: single tail-output wiring step shared by the tag-aware paths. */
    private void wireTailOperatorOutput(Output<StreamRecord<Object>> output) {
        List<StreamOperator<?>> operators = operatorChain.getOperators();
        if (!operators.isEmpty()) {
            StreamOperator<?> tail = operators.get(operators.size() - 1);
            if (tail instanceof AbstractStreamOperator) {
                ((AbstractStreamOperator) tail).setOutput(output);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void wireOperators(List<RecordWriter<Object>> fanOutWriters) {
        List<StreamOperator<?>> operators = operatorChain.getOperators();
        chainInnerOperators();
        wireHeadInput();

        if (operators.isEmpty() || !(operators.get(operators.size() - 1) instanceof AbstractStreamOperator)) {
            return;
        }
        AbstractStreamOperator<Object> op =
                (AbstractStreamOperator<Object>) operators.get(operators.size() - 1);
        if (fanOutWriterTagIds != null) {
            // WI8 Phase 2 (plan 2332): per-edge tag-aware outputs. When the vertex has
            // at least one tag edge, tagged emissions route ONLY to the matching tag
            // edge and main records never cross a tag edge — otherwise a tag-fed
            // consumer would double-deliver every record (main copy + tagged copy).
            boolean hasTagEdges = fanOutWriterTagIds.stream().anyMatch(java.util.Objects::nonNull);
            List<Output<StreamRecord<Object>>> edgeOutputs = new ArrayList<>(fanOutWriters.size());
            for (int i = 0; i < fanOutWriters.size(); i++) {
                String tagId = i < fanOutWriterTagIds.size() ? fanOutWriterTagIds.get(i) : null;
                edgeOutputs.add(new EdgeWriterOutput(fanOutWriters.get(i), tagId, hasTagEdges, taskMetrics));
            }
            if (edgeOutputs.size() == 1) {
                op.setOutput(edgeOutputs.get(0));
            } else {
                op.setOutput(new BroadcastingRecordWriterOutput(edgeOutputs));
            }
            return;
        }
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

    /**
     * G4: the inner-operator chaining loop shared verbatim by both
     * {@code wireOperators} overloads — wires each intermediate operator's
     * output to the next operator's input, wrapping it with the key selector
     * when the next hop declares one.
     */
    private void chainInnerOperators() {
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

                ChainingOutput<?> chained =
                        new ChainingOutput<>(wiredInput, null, sideOutputConsumers, this.sideOutputRegistry);
                wiredChainingOutputs.add(chained);
                currentOp.setOutput(chained);
            }
        }
    }

    /**
     * G4: head-input wiring shared verbatim by both {@code wireOperators}
     * overloads — the head operator's input (key-selector-wrapped when the head
     * declares a key selector) is what the task's input loop feeds.
     */
    private void wireHeadInput() {
        List<StreamOperator<?>> operators = operatorChain.getOperators();
        List<KeySelector<?, ?>> keySelectors = operatorChain.getKeySelectors();
        if (!operators.isEmpty() && operators.get(0) instanceof Input) {
            Input rawHeadInput = (Input) operators.get(0);
            if (!keySelectors.isEmpty() && keySelectors.get(0) != null && operators.get(0) instanceof KeyContext) {
                headInput = new KeyExtractingOutput<>(rawHeadInput, keySelectors.get(0), (KeyContext) operators.get(0));
            } else {
                headInput = rawHeadInput;
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
     * <p>Also wires {@code setProgressMarker(this::markProgress)} so that
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
                // Source-side consumption counter.
                sourceOp.setRecordCounter(taskMetrics::recordsConsumed);
            } else if (head instanceof SourceReaderOperator) {
                // FLIP-27 style source path — wire the mailbox so
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
     * Injects this task's data-plane metrics implementation. Must be
     * called before {@code invoke()} on the real execution paths; a task
     * without injection keeps NOOP behavior (serialization-safe).
     */
    public void setTaskMetrics(io.nop.stream.core.metrics.StreamTaskMetrics metrics) {
        this.taskMetrics.setDelegate(metrics);
        // WI16 (plan 2337): bind the watermark cascading gauges to this task's input
        // gate (holder rebind — recovery rebuilds rebind the same meter ids). SOURCE /
        // SELF_CONTAINED roles have no gate and skip.
        if (metrics instanceof io.nop.stream.core.metrics.MicrometerStreamTaskMetrics
                && inputGate != null) {
            ((io.nop.stream.core.metrics.MicrometerStreamTaskMetrics) metrics)
                    .bindWatermarkGauges(inputGate);
        }
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
     * Registers a consumer for a side-output tag in the chained execution.
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
     * WI8 Phase 2 (plan 2332): registers this task as the consumer of an incoming
     * side-output tag edge. Records arriving over a tag edge surface on the input gate
     * as {@code SideOutputElement}s and {@code classifyAndDispatch} routes them to the
     * consumer registered here by tag id — feeding them into the operator chain's head
     * input exactly like a main-path record, so the target operator is unaware it is
     * being fed from a tag channel.
     *
     * <p>Called by the deployment plan builders for every incoming
     * {@code JobEdge#getOutputTagId() != null} edge. Idempotent per tag id (replacing
     * registration is the map's contract).
     */
    public void registerTagEdgeConsumer(String tagId) {
        if (tagId == null || tagId.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "tagId");
        }
        Input<Object> input = this.headInput;
        if (input == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "cannot register tag-edge consumer for '" + tagId
                            + "': this task's operator chain head is not an Input");
        }
        registerSideOutputConsumer(new OutputTag<>(tagId, UnknownTypeInformation.INSTANCE), record -> {
            try {
                input.processElement((StreamRecord<Object>) record);
            } catch (Exception e) {
                throw new StreamRuntimeException(ERR_STREAM_CHAINING_OUTPUT_EXCEPTION, e)
                        .param(ARG_DETAIL, "tag-edge consumer for '" + tagId
                                + "' failed feeding the operator chain");
            }
        });
    }

    /**
     * WI8 Phase 2 (plan 2332): wiring assertion helper — resolves this task's
     * side-output consumer for the given tag id exactly like the runtime routing
     * points do (task-local map first, then the plan-time
     * {@code SideOutputRegistry} fallback).
     */
    public boolean hasSideOutputConsumer(String tagId) {
        return sideOutputConsumerFor(tagId) != null;
    }

    /**
     * Liveness timestamp. Updated at every data-plane progress point.
     *
     * @return monotonic timestamp of the last data-plane progress; never decreases
     */
    public long getLastProgressTime() {
        return lastProgressTime;
    }

    /**
     * Marks a data-plane progress event. Called from {@link #processInputGate}
     * (MIDDLE/SINK) and from the source emission paths
     * ({@link #invokeSource}/{@link #invokeSelfContained} via SourceContext.collect
     * or the source operator's pull loop). Idempotent and thread-safe (volatile
     * assignment from the task thread only).
     */
    public void markProgress() {
        this.lastProgressTime = CoreMetrics.currentTimeMillis();
    }

    /**
     * Task-thread aliveness timestamp. Fresh while the task's main
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
     * Marks a task-thread aliveness event. Called at the top of
     * every {@link #processInputGate} loop iteration (idle and data) and at
     * {@code invoke()} role start points. Idempotent and thread-safe (volatile
     * assignment from the task thread only).
     */
    public void markActivity() {
        this.lastActivityTime = CoreMetrics.currentTimeMillis();
    }

    public OperatorChain getOperatorChain() {
        return operatorChain;
    }

    /**
     * WI14 (plan 2340): read-only keyed-state snapshot for the queryable-state-lite
     * REST surface. Traverses the operator chain for the first operator holding a
     * keyed state backend and returns its {@code snapshotState()} — the same
     * JSON-native {@code StateSnapshot} the checkpoint path persists.
     *
     * <p>Returns an empty snapshot when no operator in this chain holds a keyed
     * backend (stateless/parallel-only roles) — an explicit empty, not an error.
     */
    public io.nop.stream.core.common.state.backend.StateSnapshot keyedStateSnapshot() {
        for (io.nop.stream.core.operators.StreamOperator<?> op : operatorChain.getOperators()) {
            if (op instanceof io.nop.stream.core.operators.AbstractStreamOperator) {
                io.nop.stream.core.common.state.backend.IKeyedStateBackend<?> backend =
                        ((io.nop.stream.core.operators.AbstractStreamOperator<?>) op).getKeyedStateBackend();
                if (backend != null) {
                    try {
                        // MemoryStateSerDe returns null for a backend with zero
                        // registered states — normalize to an explicit empty snapshot
                        io.nop.stream.core.common.state.backend.StateSnapshot snap =
                                backend.snapshotState();
                        return snap != null ? snap
                                : new io.nop.stream.core.common.state.backend.StateSnapshot(
                                        java.util.Collections.emptyMap());
                    } catch (Exception e) {
                        throw new io.nop.stream.core.exceptions.StreamException(
                                io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE)
                                .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                                        "keyed state snapshot failed: " + e.getMessage());
                    }
                }
            }
        }
        return new io.nop.stream.core.common.state.backend.StateSnapshot(
                java.util.Collections.emptyMap());
    }

    public RecordWriter<Object> getOutputWriter() {
        return outputWriter;
    }

    /**
     * @return the full fan-out writer list (one per outgoing edge), or null
     *         when this task is not a fan-out producer. The restart
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
     * Releases the input gate's external resources exactly once at task
     * teardown. For remote edges this cancels the message-service subscription
     * of every input channel — without this hook a finished, failed, or
     * redeployed task would leak one live subscription per input edge and keep
     * occupying the shared message-backend dispatch surface. Safe for source /
     * self-contained roles (no input gate): the call is a no-op then.
     */
    private void closeInputGate() {
        if (inputGate == null) {
            return;
        }
        inputGate.close();
    }

    /**
     * Closes ALL output writers of this task. A fan-out producer (2+
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
     * silently skip every writer. Every
     * writer in the list is attempted; the first failure is rethrown with
     * the rest suppressed, mirroring {@link RecordWriter#close()} semantics.
     */
    private void closeOutputWriters() {
        if (fanOutWriters != null && !fanOutWriters.isEmpty()) {
            Exception firstError = CloseSupport.closeAll(fanOutWriters, RecordWriter::close);
            CloseSupport.throwAsCloseError(firstError, ERR_STREAM_CHAINING_OUTPUT_CLOSE_FAILED);
        } else if (outputWriter != null) {
            outputWriter.close();
        }
    }

    /**
     * Closes the operator chain and the input gate no matter what failed
     * before. The first close error wins; later ones are attached as
     * suppressed exceptions. Both releases are always attempted: a failing
     * EOS send (or any output-close failure) must never skip the input-gate
     * close — the gate close unsubscribes the remote input channels this task
     * holds, so skipping it leaks one live subscription per input edge.
     */
    private Exception closeChainAndGate(Exception firstError) {
        Exception closeError = CloseSupport.closeAll(java.util.Arrays.asList(
                (AutoCloseable) operatorChain::close,
                this::closeInputGate));
        return CloseSupport.accumulate(firstError, closeError);
    }

    /**
     * Rethrows a close error after cleanup completed, preserving the typing
     * contract of {@link #closeOutputWriters()}: stream and runtime exceptions
     * propagate as-is, anything else is wrapped.
     */
    private void throwCloseError(Exception e) {
        if (e instanceof StreamException) {
            throw (StreamException) e;
        }
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        throw new StreamException(ERR_STREAM_CHAINING_OUTPUT_CLOSE_FAILED, e);
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
            // WI16: detach the time-share/watermark gauges' static holder so an ended
            // task stops reporting its last ratios (the meters keep reading NaN).
            io.nop.stream.core.metrics.StreamTaskMetrics delegate = taskMetrics.getDelegate();
            if (delegate instanceof io.nop.stream.core.metrics.MicrometerStreamTaskMetrics) {
                ((io.nop.stream.core.metrics.MicrometerStreamTaskMetrics) delegate).releaseHolder();
            }
        }
    }

    private void invokeSource() throws Exception {
        operatorChain.open();
        // Liveness marker for the SOURCE role at the start of the run loop.
        // SourceContext.collect() (the per-record emission path) also marks progress;
        // this initial marker covers a slow-start source that has not emitted yet.
        markProgress();
        // Task-thread aliveness at role start. SOURCE liveness is
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
                    // Mark progress after source run returns (covers a source
                    // that emits in batches and might otherwise look idle mid-run).
                    markProgress();
                }
            } else if (head instanceof SourceReaderOperator) {
                // FLIP-27 style source path. Drive SourceReaderOperator.run()
                // which polls SourceReader.pollNext() until the reader signals isFinished().
                SourceReaderOperator<?> readerOp = (SourceReaderOperator<?>) head;
                try {
                    readerOp.run();
                } catch (Exception e) {
                    sourceError = e;
                }
                markProgress();
            }

            // finish() must run after the source returns and BEFORE the
            // MAX_WATERMARK is emitted and operators are closed. Without this,
            // connectors that buffer (e.g. BatchConsumerSinkFunction) silently
            // dropped the tail batch on bounded source EOS.
            if (sourceError == null) {
                operatorChain.finish();
            }
        } finally {
            // Producer-region restart contract: only close
            // the output on SUCCESSFUL completion (signal EOS). On failure, keep
            // the output partition open so a restarted producer can continue
            // writing to the same partition (with the same materialization point).
            // Closing on failure would force EOS and make producer-region restart
            // impossible — the restarted producer could not emit any data. When
            // the job goes to global recovery instead, fresh partitions are built
            // anyway, so an open partition here is harmless.
            Exception closeError = null;
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
                try {
                    closeOutputWriters();
                } catch (Exception e) {
                    // An EOS send failure must not skip the chain/gate close
                    // below — that would leak the input-channel subscriptions
                    // this task holds. Surface the failure after cleanup.
                    closeError = e;
                }
            }
            closeError = closeChainAndGate(closeError);
            if (closeError != null) {
                if (sourceError != null) {
                    sourceError.addSuppressed(closeError);
                } else {
                    throwCloseError(closeError);
                }
            }
        }
        if (sourceError != null) {
            throw sourceError;
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeMiddle() throws Exception {
        operatorChain.open();
        // Task-thread aliveness at role start (the loop top tick
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
                // Cancel/interrupt exits must NOT run the
                // success terminal state (finish + MAX_WATERMARK) — the truncated
                // stream must not be finalized as a bounded-complete one. The
                // success path runs finish() BEFORE MAX_WATERMARK
                // (aligned with SOURCE/SELF_CONTAINED) so buffered operators' tail
                // batches reach downstream windows before the final watermark fires.
                if (inputError == null && exitReason == InputLoopExitReason.END_OF_STREAM) {
                    operatorChain.finish();
                    headInput.processWatermark(Watermark.MAX_WATERMARK);
                }
            }
        } finally {
            // Mirror invokeSource's failure-preserving output policy — only
            // signal EOS downstream on SUCCESSFUL completion. On error/cancel/
            // interrupt (and, by construction, on a thrown Error, which skips the
            // reason bookkeeping entirely) the output partition stays open so a
            // producer-region restart can continue writing to the same partition.
            Exception closeError = null;
            if (inputError == null && exitReason == InputLoopExitReason.END_OF_STREAM) {
                try {
                    closeOutputWriters();
                } catch (Exception e) {
                    // An EOS send failure must not skip the chain/gate close
                    // below — that would leak the input-channel subscriptions
                    // this task holds. Surface the failure after cleanup.
                    closeError = e;
                }
            }
            closeError = closeChainAndGate(closeError);
            if (closeError != null) {
                if (inputError != null) {
                    inputError.addSuppressed(closeError);
                } else {
                    throwCloseError(closeError);
                }
            }
        }
        if (inputError != null) {
            throw inputError;
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeSink() throws Exception {
        operatorChain.open();
        // Task-thread aliveness at role start (see invokeMiddle).
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
                // Cancelled/interrupted SINK tasks must not finalize the
                // truncated stream (no finish, no MAX_WATERMARK) — e.g. a 2PC sink's
                // flush/commit window must not run on a cancelled task. On the
                // success path finish() runs BEFORE MAX_WATERMARK.
                if (inputError == null && exitReason == InputLoopExitReason.END_OF_STREAM) {
                    operatorChain.finish();
                    headInput.processWatermark(Watermark.MAX_WATERMARK);
                }
            }
        } finally {
            // SINK has no downstream writer (no closeOutputWriters call — unchanged);
            // the cancellation distinction lives in the success-terminal guard above.
            // A8: route through closeChainAndGate like invokeMiddle — a failing
            // operatorChain.close() must never skip closeInputGate() (that leaks
            // one live remote input-channel subscription per input edge), and the
            // close error must not mask the input error.
            Exception closeError = closeChainAndGate(null);
            if (closeError != null) {
                if (inputError != null) {
                    inputError.addSuppressed(closeError);
                } else {
                    throwCloseError(closeError);
                }
            }
        }
        if (inputError != null) {
            throw inputError;
        }
    }

    private void invokeSelfContained() throws Exception {
        operatorChain.open();
        // Liveness marker for SELF_CONTAINED at the start of run.
        markProgress();
        // Task-thread aliveness at role start (see invokeSource).
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
                    // Mark progress after run (covers batched emission).
                    markProgress();
                    if (sourceError == null) {
                        // finish() before MAX_WATERMARK and close.
                        operatorChain.finish();
                        sourceOp.processWatermark(Watermark.MAX_WATERMARK);
                    }
                }
            } else if (head instanceof SourceReaderOperator) {
                // FLIP-27 style source path in SELF_CONTAINED role
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
            // A8: closeChainAndGate instead of a sequential close — a failing
            // operatorChain.close() must never skip closeInputGate(), and the
            // close error must not mask the source error.
            Exception closeError = closeChainAndGate(null);
            if (closeError != null) {
                if (sourceError != null) {
                    sourceError.addSuppressed(closeError);
                } else {
                    throwCloseError(closeError);
                }
            }
        }
        if (sourceError != null) {
            throw sourceError;
        }
    }

    /**
     * The input loop's exit reason. Distinguishing EOS from
     * thread-interrupt and cooperative-cancel lets invokeMiddle/invokeSink run
     * the success terminal state (finish +
     * MAX_WATERMARK + EOS downstream) only on a true end-of-stream exit.
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
        // WI16 (plan 2337): time-share accumulation. Idle slices = read calls that
        // return empty (momentary idle); elapsed slices = main-loop iterations. The
        // busy/backpressured shares are derived from the processingTime/emitTime
        // timers (emit nested in processing — see the authoritative name table).
        long iterationStart = System.nanoTime();
        while (true) {
            // Task-thread aliveness tick at the top of every loop
            // iteration, INCLUDING idle iterations (the idle-return path
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

            long readStart = System.nanoTime();
            Optional<StreamElement> elementOpt = inputGate.read();
            if (!elementOpt.isPresent()) {
                taskMetrics.idleTime(System.nanoTime() - readStart);
                // InputGate returns empty for BOTH end-of-stream
                // and momentary idle (idle-return threshold, see
                // InputGate.IDLE_RETURN_THRESHOLD_MS). Only a true EOS — all
                // channels finished — terminates the loop; an idle return cycles
                // back to the loop top, where processAvailableMails() drains any
                // pending control mails (e.g. processing-time timer fire mails)
                // before re-reading — an idle task (no data flow) must still
                // reach the mailbox drain or processing-time timers never fire.
                //
                // The exit reasons are distinguished so the callers can
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

            // Per-iteration liveness marker for MIDDLE/SINK roles.
            markProgress();

            classifyAndDispatch(headInput, elementOpt.get());
            long now = System.nanoTime();
            taskMetrics.elapsedTime(now - iterationStart);
            iterationStart = now;
        }
    }

    /**
     * Classifies one element polled from the input gate and dispatches it to the
     * operator chain, preserving the pre-extraction dispatch order: record, side
     * output, watermark, checkpoint barrier, watermark status.
     */
    @SuppressWarnings("unchecked")
    private void classifyAndDispatch(Input<Object> headInput, StreamElement element) throws Exception {
        if (element.isRecord()) {
            // Record dispatch into the
            // operator chain + per-record chain processing time.
            taskMetrics.recordsIn(1);
            long metricsStart = CoreMetrics.nanoTime();
            try {
                headInput.processElement((StreamRecord<Object>) (StreamRecord<?>) element.asRecord());
            } finally {
                taskMetrics.processingTime(CoreMetrics.nanoTime() - metricsStart);
            }
        } else if (element.isSideOutput()) {
            // Cross-task side-output routing. Look up the registered
            // consumer by tag id (OutputTag's ctor forbids a
            // null-typeInfo lookup key). Unmatched tag = fail-fast at the consumption side.
            SideOutputElement side = element.asSideOutput();
            String tagId = side.getOutputTagId();
            Consumer<StreamRecord<?>> consumer = sideOutputConsumerFor(tagId);
            if (consumer == null) {
                throw new StreamRuntimeException(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER)
                        .param(ARG_OUTPUT_TAG, tagId)
                        .param(ARG_DETAIL, "No registered side-output consumer for tag '"
                                + tagId + "' on task " + getRole());
            }
            consumer.accept(side.getRecord());
        } else if (element.isWatermark()) {
            headInput.processWatermark(element.asWatermark());
        } else if (element.isCheckpointBarrier()) {
            // Unaligned checkpoint: if InputGate just switched to
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

    /**
     * Resolves the registered consumer for a side-output tag id by
     * direct map lookup instead of a keySet scan. {@link OutputTag} equality and
     * hashCode are defined purely on the id, so a probe OutputTag carrying the
     * incoming tag id hashes and compares exactly like the registered key —
     * matching every consumer registered on the shared map, including those
     * registered through {@code ChainingOutput#registerSideOutputConsumer}. At
     * most one entry per id can exist: an equal (same-id) key replaces its
     * predecessor on re-registration.
     */
    private Consumer<StreamRecord<?>> sideOutputConsumerFor(String tagId) {
        // Degenerate ids (null/empty) can never be registered — OutputTag's ctor
        // forbids them — so they resolve to "no consumer" without probing.
        if (tagId == null || tagId.isEmpty()) {
            return null;
        }
        Consumer<StreamRecord<?>> consumer = sideOutputConsumers.get(
                new OutputTag<>(tagId, UnknownTypeInformation.INSTANCE));
        if (consumer == null) {
            // W-07 (CRA 8.42 R4-02, ruling C.2): fall back to the execution-context-local
            // registry carried from the owning env (registered at assembly via
            // getSideOutput(tag).sink(...)). Null registry (remote build path) misses
            // here → the caller's NO_CONSUMER fail-fast (split-process semantics,
            // explicitly tightened for the embedded same-JVM remote form — F-3).
            io.nop.stream.core.datastream.SideOutputRegistry scoped = sideOutputRegistry;
            consumer = scoped != null ? scoped.get(tagId) : null;
        }
        return consumer;
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
         * Shared metrics handle of the owning invokable —
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
            // Time the emission itself — a blocked
            // emit (downstream exchange queue full) shows up here, making this
            // the producer-side backpressure proxy.
            long emitStart = CoreMetrics.nanoTime();
            writer.emit(record.copy(record.getValue()));
            taskMetrics.emitTime(CoreMetrics.nanoTime() - emitStart);
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
            // Watermark status crosses task boundaries —
            // an idle upstream task's status lets the downstream InputGate exclude the
            // idle channel from the min watermark merge instead of pinning event time.
            writer.emitWatermarkStatus(status);
        }

        @Override
        public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            // The cross-task wire protocol carries
            // side outputs — wrap the tagged record in a SideOutputElement and broadcast it
            // through RecordWriter.emitElement to ALL downstream partitions. The inner
            // record is copied so the producer's reused StreamRecord
            // instance is never aliased by the exchange queue. No-consumer fail-fast
            // is enforced at the consumption-side routing point in processInputGate.
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

    /**
     * WI8 Phase 2 (plan 2332): one cross-vertex edge's output with tag-edge routing.
     *
     * <ul>
     *   <li>{@code edgeTagId == null} (main edge): carries main records, and also
     *       forwards tagged records — the legacy HG-01 broadcast semantics, so a
     *       WindowOperator late-data emission still reaches its registry-registered
     *       consumer across a plain main edge. When the vertex has tag edges, tagged
     *       records are NOT forwarded here (they route via their tag edge only —
     *       forwarding them would double-deliver or fail-fast on main consumers).</li>
     *   <li>{@code edgeTagId != null} (tag edge): carries ONLY
     *       {@code SideOutputElement} traffic matching its tag id; main records never
     *       cross it and foreign tagged records never cross it.</li>
     * </ul>
     *
     * Watermarks / watermark statuses / barriers forward on both edge kinds — the
     * tag-fed consumer needs event-time progress, checkpoint alignment and EOS exactly
     * like a main-path consumer.
     */
    private static class EdgeWriterOutput implements Output<StreamRecord<Object>> {

        private final RecordWriter<Object> writer;
        private final String edgeTagId;
        private final boolean vertexHasTagEdges;
        private final io.nop.stream.core.metrics.TaskMetricsHandle taskMetrics;

        EdgeWriterOutput(RecordWriter<Object> writer, String edgeTagId,
                         boolean vertexHasTagEdges,
                         io.nop.stream.core.metrics.TaskMetricsHandle taskMetrics) {
            this.writer = writer;
            this.edgeTagId = edgeTagId;
            this.vertexHasTagEdges = vertexHasTagEdges;
            this.taskMetrics = taskMetrics;
        }

        @Override
        public void collect(StreamRecord<Object> record) {
            if (edgeTagId != null) {
                // A tag edge carries no main-path records.
                return;
            }
            taskMetrics.recordsOut(1);
            taskMetrics.recordsEmitted(1);
            long emitStart = CoreMetrics.nanoTime();
            writer.emit(record.copy(record.getValue()));
            taskMetrics.emitTime(CoreMetrics.nanoTime() - emitStart);
        }

        @Override
        public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            if (vertexHasTagEdges) {
                if (edgeTagId == null || !edgeTagId.equals(outputTag.getId())) {
                    // On a tap vertex, tagged records route via their own tag edge only.
                    return;
                }
            } else if (edgeTagId != null) {
                // A standalone tag edge carries only its own tag id.
                if (!edgeTagId.equals(outputTag.getId())) {
                    return;
                }
            }
            // The inner record is copied so the producer's reused StreamRecord
            // instance is never aliased by the exchange queue (same as RecordWriterOutput).
            writer.emitElement(new SideOutputElement(outputTag.getId(),
                    record.copy(record.getValue())));
        }

        @Override
        public void emitWatermark(Watermark mark) {
            writer.emitWatermark(mark);
        }

        @Override
        public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
            writer.emitWatermarkStatus(status);
        }

        @Override
        public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
            // Latency markers not forwarded across task boundaries (same as RecordWriterOutput)
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
            writer.emitBarrier(barrier);
        }

        @Override
        public void close() {
            // RecordWriter lifecycle is managed by invoke() (same as RecordWriterOutput)
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
            // G11: shared accumulate-suppress teardown (CloseSupport).
            Exception firstError = CloseSupport.closeAll(outputs, Output::close);
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
            // Fan the status out to every downstream edge
            // (mirrors emitWatermark / emitBarrier broadcast semantics).
            for (Output<StreamRecord<Object>> output : outputs) {
                output.emitWatermarkStatus(status);
            }
        }

        @Override
        public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            // Fan the tagged record out to every wrapped
            // output. Each RecordWriterOutput copies the inner record on construction, so no
            // single SideOutputElement instance is shared across partition queues.
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
