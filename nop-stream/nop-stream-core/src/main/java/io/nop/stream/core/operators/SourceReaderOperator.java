/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.execution.Mail;
import io.nop.stream.core.execution.MailboxExecutor;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SourceSplit;
import io.nop.stream.core.source.SplitAssignmentProxy;
import io.nop.stream.core.source.coordinator.LocalSourceCoordinator;
import io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_BARRIER_INJECTION_FAILED;

/**
 * Stage 49 D5: operator that drives a FLIP-27 style {@link SourceReader} on the task side.
 *
 * <p>This is the task-side counterpart of the legacy {@link StreamSourceOperator} for the
 * SourceFunction path. It runs a pull loop that calls {@link SourceReader#pollNext()}
 * repeatedly, emitting whatever records the reader produces and draining the control-plane
 * mailbox between polls so that barrier trigger / cancel mails are processed on the task
 * thread (consistent with the existing source operator's mailbox handoff).
 *
 * <p>Splits are delivered to this operator by the coordinator-side enumerator through
 * {@link SourceReader#addSplits(java.util.List)} — the {@link SplitAssignmentProxy}
 * wired through {@link SourceReaderContext} routes pull requests and finished-split
 * reports back to the coordinator.
 *
 * <p>Checkpoint semantics:
 * <ul>
 *   <li>per-split cursor state lives in the reader and is captured via
 *       {@link SourceReader#snapshotState(long)} into operator state on barrier injection;</li>
 *   <li>enumerator coordinator-state (which splits exist / are assigned / are finished) is
 *       captured separately on the coordinator side into
 *       {@code EpochManifest.sourceEnumeratorSnapshots} (Stage 49 D2).</li>
 * </ul>
 *
 * @param <OUT> the element type this operator emits
 */
@Internal
public class SourceReaderOperator<OUT> extends AbstractStreamOperator<OUT> {

    private static final Logger LOG = LoggerFactory.getLogger(SourceReaderOperator.class);

    private static final long serialVersionUID = 1L;

    public static final String READER_SPLITS_KEY = "source-reader-splits";

    /** The FLIP-27 source descriptor (serializable; used to instantiate the live reader). */
    private final Source<OUT, ? extends SourceSplit, ?> source;

    /**
     * Source vertex id from the transformation DAG. Used to look up the
     * {@link LocalSourceCoordinator} via {@link SourceCoordinatorRegistry} at open() time.
     */
    private final int vertexId;

    /** This subtask's identity / parallelism / coordinator channel. Set in open(). */
    private int subtaskIndex;
    private int totalParallelism;

    /** Coordinator channel for split pull requests / finished-split reports. Set in open(). */
    private SplitAssignmentProxy assignmentProxy;

    /** Mailbox for barrier / cancel delivery. Set by StreamTaskInvokable before run(). */
    private MailboxExecutor mailboxExecutor;

    /** Live reader instance, created in open(). */
    private SourceReader<OUT, ? extends SourceSplit> reader;

    /**
     * Pre-open split buffer: splits delivered by the enumerator between
     * {@link #wireCoordinatorChannel()} and {@link #open()}'s reader creation are stashed
     * here, then flushed via {@link #flushPreOpenSplits()} once the reader exists.
     */
    private final java.util.List<SourceSplit> preOpenSplits =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private volatile boolean isRunning = true;
    private volatile boolean finished = false;
    private volatile boolean opened = false;

    public SourceReaderOperator(Source<OUT, ? extends SourceSplit, ?> source,
                                int vertexId) {
        this.source = source;
        this.vertexId = vertexId;
    }

    /**
     * Per-subtask identity wiring. Called by the LOCAL execution path before {@link #open()}
     * (or before {@link #run()} at the latest) to set this operator's subtaskIndex and
     * totalParallelism. If not called, defaults to subtaskIndex=0, totalParallelism=1.
     */
    public void setSubtaskIdentity(int subtaskIndex, int totalParallelism) {
        this.subtaskIndex = subtaskIndex;
        this.totalParallelism = totalParallelism;
    }

    public int getVertexId() {
        return vertexId;
    }

    public int getSubtaskIndex() {
        return subtaskIndex;
    }

    public int getTotalParallelism() {
        return totalParallelism;
    }

    public void setMailboxExecutor(MailboxExecutor mailboxExecutor) {
        if (mailboxExecutor == null) {
            throw new IllegalArgumentException("MailboxExecutor must not be null");
        }
        this.mailboxExecutor = mailboxExecutor;
    }

    @SuppressWarnings("unchecked")
    @Override
    public SourceReaderOperator<OUT> copyForSubtask() {
        // Per-subtask copy: source descriptor + vertexId shared; per-subtask identity and
        // coordinator channel are (re)wired by the LOCAL execution path via
        // {@link #setSubtaskIdentity} + {@link #wireCoordinatorChannel} before run().
        return new SourceReaderOperator<>(source, vertexId);
    }

    /**
     * Stage 49 D3: looks up this vertex's {@link LocalSourceCoordinator} in the
     * {@link SourceCoordinatorRegistry} (creating and registering it if absent — first
     * subtask to open creates), registers this subtask with it, and captures the returned
     * {@link SplitAssignmentProxy} (per-subtask channel) on this operator.
     *
     * <p>If no coordinator can be created (e.g. isolated unit test), the operator falls back
     * to running the reader with a {@code null} assignment proxy — the reader must then be
     * fed splits directly via {@link SourceReader#addSplits(java.util.List)} by the test.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void wireCoordinatorChannel() {
        LocalSourceCoordinator<? extends SourceSplit, ?> coord = SourceCoordinatorRegistry.get(vertexId);
        if (coord == null) {
            // First subtask to open: create and register the coordinator.
            int parallelism = totalParallelism > 0 ? totalParallelism : 1;
            coord = SourceCoordinatorRegistry.registerIfAbsent(vertexId,
                    vid -> new LocalSourceCoordinator(
                            String.valueOf(vid),
                            (Source) source,
                            parallelism));
        }
        if (totalParallelism <= 0) {
            totalParallelism = coord.getTotalParallelism();
        }
        LocalSourceCoordinator<SourceSplit, ?> typed = (LocalSourceCoordinator<SourceSplit, ?>) coord;
        LocalSourceCoordinator.ReaderChannel<SourceSplit> channel = typed.registerReader(subtaskIndex);
        // Install the bridge callback (no-op until reader is set in open(); once reader is
        // non-null, the callback routes delivered splits to reader.addSplits()). Buffering
        // in the channel handles the registerReader-before-create-reader race.
        bridgeChannelToReaderFromWire(channel);
        this.assignmentProxy = channel;
        this.totalParallelism = typed.getTotalParallelism();
    }

    /**
     * Bridge helper for {@link #wireCoordinatorChannel()} — at wire time the reader is
     * still null, so we install a callback that lazily fetches the reader (set by
     * {@link #open()}). Buffering in the channel handles the race.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void bridgeChannelToReaderFromWire(LocalSourceCoordinator.ReaderChannel<SourceSplit> channel) {
        // At wire time the reader is null: buffer splits to preOpenSplits. open() will
        // flush them to the reader once it exists.
        channel.setOnSplitsDelivered(new java.util.function.Consumer<List<SourceSplit>>() {
            @Override
            public void accept(List<SourceSplit> splits) {
                SourceReader r = getReaderRaw();
                if (r != null) {
                    r.addSplits(splits);
                } else {
                    preOpenSplits.addAll(splits);
                }
            }
        });
    }

    /** Returns the coordinator-owned reader channel (for tests / coordinator verification). */
    public SplitAssignmentProxy getAssignmentProxy() {
        return assignmentProxy;
    }

    @Override
    public void open() throws Exception {
        if (opened) {
            // SubtaskTask.openOperatorChains() and StreamTaskInvokable.invoke*() both
            // call operatorChain.open(); make open() idempotent so we don't double-create
            // the reader / double-register with the coordinator.
            return;
        }
        opened = true;
        // Stage 49 D3: look up / create the coordinator and register this subtask.
        // If no coordinator can be created (isolated unit test), proceed with a null proxy.
        wireCoordinatorChannel();

        SourceReaderContext ctx = new SourceReaderContext(subtaskIndex, totalParallelism, assignmentProxy);
        this.reader = source.createReader(ctx);

        // Switch the channel callback to deliver directly to the (now non-null) reader,
        // and flush any pre-open-buffered splits into the reader.
        if (assignmentProxy instanceof LocalSourceCoordinator.ReaderChannel) {
            bridgeChannelToReader((LocalSourceCoordinator.ReaderChannel<?>) assignmentProxy);
        }
        flushPreOpenSplits();

        this.reader.start();
        LOG.debug("SourceReaderOperator opened: source={}, vertex={}, subtask={}/{}",
                source.getClass().getSimpleName(), vertexId, subtaskIndex, totalParallelism);
    }

    /**
     * Flushes any splits that were buffered during {@link #wireCoordinatorChannel()}
     * (before the reader existed in {@link #open()}) into the now-non-null reader.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void flushPreOpenSplits() {
        List<SourceSplit> toFlush;
        synchronized (preOpenSplits) {
            if (preOpenSplits.isEmpty()) {
                return;
            }
            toFlush = new ArrayList<>(preOpenSplits);
            preOpenSplits.clear();
        }
        if (reader != null && !toFlush.isEmpty()) {
            ((SourceReader) reader).addSplits((List) toFlush);
        }
    }

    /**
     * Type-safe helper to wire a {@code ReaderChannel<T>} push into the reader's
     * {@code addSplits(List<T>)} entry point. The wildcard capture on the channel type
     * makes this impossible to inline without an unchecked cast; isolating it here keeps
     * the call sites clean.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <X extends SourceSplit> void bridgeChannelToReader(
            LocalSourceCoordinator.ReaderChannel<X> channel) {
        // open()-time install: reader is non-null now, deliver directly.
        channel.setOnSplitsDelivered(new java.util.function.Consumer<List<X>>() {
            @Override
            public void accept(List<X> splits) {
                SourceReader r = getReaderRaw();
                if (r != null) {
                    r.addSplits(splits);
                } else {
                    // Should not happen post-open, but defensive.
                    preOpenSplits.addAll(splits);
                }
            }
        });
    }

    /**
     * Main pull loop: drives {@link SourceReader#pollNext()} and emits records through the
     * operator chain. Control-plane mails (trigger-checkpoint, cancel) are drained at every
     * poll boundary on this task thread.
     */
    public void run() throws Exception {
        isRunning = true;
        // Bounded sources eventually return empty on every poll AND report isFinished();
        // unbounded sources run until cancelled.
        while (isRunning) {
            drainControlMails();

            Optional<OUT> next;
            try {
                next = reader.pollNext();
            } catch (Exception e) {
                throw new StreamException(
                        io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_JOB_EXECUTE_FAILED, e)
                        .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                                "SourceReader.pollNext failed: " + e.getMessage());
            }

            if (next != null && next.isPresent()) {
                output.collect(new StreamRecord<>(next.get()));
            } else {
                // No record available right now. Drain mailbox (may carry trigger-checkpoint
                // or cancel). If the reader signals termination, exit.
                if (!isRunning) {
                    break;
                }
                if (reader.isFinished()) {
                    LOG.debug("SourceReaderOperator exiting run loop: reader reports isFinished (vertex={}, subtask={})",
                            vertexId, subtaskIndex);
                    break;
                }
                // Cooperative yield: avoid busy-loop when reader is idle but not finished.
                Thread.yield();
            }
        }
        finished = true;
        // Drain any final trigger-checkpoint mail (final-checkpoint exception, mirrors
        // StreamSourceOperator.run() finished-source handling).
        drainControlMails();
    }

    private void drainControlMails() {
        MailboxExecutor exec = this.mailboxExecutor;
        if (exec != null) {
            exec.processAvailableMails();
            if (exec.isCancelled()) {
                isRunning = false;
            }
        }
    }

    @Override
    public void finish() throws Exception {
        isRunning = false;
    }

    @Override
    public void close() throws Exception {
        isRunning = false;
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (Exception e) {
            LOG.warn("Error closing source reader during close", e);
        }
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        if (reader != null) {
            reader.notifyCheckpointComplete(checkpointId);
        }
    }

    /**
     * Snapshots per-split cursor state from the reader into operator state on barrier.
     * The split list is serialized into the operator state keyed by {@link #READER_SPLITS_KEY}.
     */
    @Override
    public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
        OperatorSnapshotResult result = super.snapshotState(context);
        if (reader != null) {
            java.util.List<? extends SourceSplit> splits = reader.snapshotState(context.getCheckpointId());
            if (splits != null && !splits.isEmpty()) {
                // Store the per-split cursor list; the actual split serializer is invoked
                // by the runtime storage layer. For v1 LOCAL mode we store the in-memory
                // list (JSON-serialized downstream by CheckpointSerDe via JsonTool).
                result.putOperatorState(READER_SPLITS_KEY, splits);
            }
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        if (reader != null && snapshotResult != null) {
            Object stored = snapshotResult.getOperatorState(READER_SPLITS_KEY);
            if (stored instanceof java.util.List) {
                java.util.List<? extends SourceSplit> splits =
                        (java.util.List<? extends SourceSplit>) stored;
                // Raw cast: SourceReader's generics capture can't be satisfied across the
                // serializable operator-state boundary. The reader's own split serializer
                // is responsible for the runtime type check.
                ((SourceReader) reader).restoreState((java.util.List) splits);
            }
        }
    }

    /**
     * Barrier injection entry point. Used by the LOCAL-mode barrier injector (mirrors
     * {@link StreamSourceOperator#offerBarrier(CheckpointBarrier)}): if mailbox is wired
     * and the source is still running, the barrier is delivered as a control mail that
     * runs snapshotState + emitBarrier on the task thread at the next poll boundary.
     */
    public boolean offerBarrier(CheckpointBarrier barrier) {
        if (finished) {
            try {
                injectBarrier(barrier);
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_BARRIER_INJECTION_FAILED, e)
                        .param(ARG_DETAIL, "after source reader finished");
            }
            return true;
        }
        MailboxExecutor exec = this.mailboxExecutor;
        if (exec == null) {
            try {
                injectBarrier(barrier);
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_BARRIER_INJECTION_FAILED, e)
                        .param(ARG_DETAIL, "no mailbox wired");
            }
            return true;
        }
        exec.getMailbox().put(Mail.control(
                () -> {
                    try {
                        injectBarrier(barrier);
                    } catch (Exception e) {
                        throw new StreamException(ERR_STREAM_BARRIER_INJECTION_FAILED, e)
                                .param(ARG_DETAIL, "trigger-checkpoint mail " + barrier.getId());
                    }
                },
                "source-reader-trigger-checkpoint-" + barrier.getId()));
        return true;
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

    /** Accessor for tests / coordinator wiring: the live reader instance. */
    public SourceReader<OUT, ? extends SourceSplit> getReader() {
        return reader;
    }

    /**
     * Raw accessor used by the {@code ReaderChannel} bridge callbacks (anonymous inner
     * classes can't reliably reference the outer generic {@code reader} field; this
     * indirection sidesteps the capture issue).
     */
    @SuppressWarnings("rawtypes")
    private SourceReader getReaderRaw() {
        return reader;
    }
}
