/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.CheckpointListener;
import io.nop.stream.core.execution.Mail;
import io.nop.stream.core.execution.MailboxExecutor;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REASON;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_BARRIER_INJECTION_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ABORTED;

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

    private static final Logger LOG = LoggerFactory.getLogger(StreamSourceOperator.class);

    private static final long serialVersionUID = 1L;

    public static final String SOURCE_OFFSET_KEY = "source-offset";

    /**
     * Mailbox executor used to deliver control-plane mails (trigger-checkpoint,
     * cancel) to this source's owning task thread. Wired by
     * {@link io.nop.stream.core.execution.StreamTaskInvokable} via
     * {@link #setMailboxExecutor(MailboxExecutor)} before {@link #run()} is invoked.
     *
     * <p>When non-null and the source is still running, checkpoint triggers are delivered
     * as control mails and drained at the {@code SourceContext.collect()} emission point
     * on the task thread (mailbox-based handoff). When null (e.g. direct unit-test usage
     * without a task), {@link #offerBarrier(CheckpointBarrier)} falls back to direct
     * injection so the operator remains usable in isolation.
     */
    private MailboxExecutor mailboxExecutor;

    /**
     * G52: per-record progress marker wired by the owning {@code StreamTaskInvokable}.
     * Called from {@link SourceFunction.SourceContext#collect(Object)} on every record
     * emission so that a healthy-but-slow source is not misjudged as stalled. May be
     * null (e.g. isolated unit-test usage); collect() null-checks before invoking.
     */
    private Runnable progressMarker;

    private final SourceFunction<OUT> sourceFunction;

    private volatile boolean isRunning = true;

    /**
     * Set to true once sourceFunction.run() has returned.
     * After this point, offerBarrier() directly injects on the injector thread
     * (finished-source final-checkpoint exception; no task thread exists anymore).
     */
    private volatile boolean finished = false;

    public StreamSourceOperator(SourceFunction<OUT> sourceFunction) {
        this.sourceFunction = sourceFunction;
    }

    /**
     * Wires the mailbox executor that owns this source's control-plane mailbox. Called by
     * {@link io.nop.stream.core.execution.StreamTaskInvokable} before {@link #run()}.
     *
     * @param mailboxExecutor the per-task mailbox executor; must not be null
     */
    public void setMailboxExecutor(MailboxExecutor mailboxExecutor) {
        if (mailboxExecutor == null) {
            throw new IllegalArgumentException("MailboxExecutor must not be null");
        }
        this.mailboxExecutor = mailboxExecutor;
    }

    /**
     * G52: wires the per-record progress marker. Called by the owning
     * {@code StreamTaskInvokable} before {@link #run()} so that
     * {@link SourceFunction.SourceContext#collect(Object)} can refresh the
     * invokable's {@code lastProgressTime} on every emitted record.
     */
    public void setProgressMarker(Runnable progressMarker) {
        this.progressMarker = progressMarker;
    }

    /**
     * Delivers a checkpoint barrier to this source for injection.
     *
     * <p>Two paths:
     * <ul>
     *   <li><b>Source still running ({@code finished == false})</b>: if a mailbox
     *       executor is wired, a control-priority trigger-checkpoint mail is put into the
     *       task mailbox. The mail is drained and executed (snapshotState + emitBarrier)
     *       on the task thread at the next {@code SourceContext.collect()} emission point.
     *       If no mailbox is wired (isolated unit-test usage), the barrier is injected
     *       directly on the caller thread as a fallback.</li>
     *   <li><b>Source already finished ({@code finished == true})</b>: the barrier is
     *       injected directly on the injector thread. This is the explicit
     *       finished-source final-checkpoint exception: the task thread no longer exists,
     *       so there is no mailbox consumer. See {@code ai-dev/design/nop-stream/mailbox-design.md} §3.3.</li>
     * </ul>
     *
     * @return true if the barrier was accepted (always true; overlap is guarded at the
     *         {@link io.nop.stream.core.execution.CheckpointBarrierTracker} level)
     */
    public boolean offerBarrier(CheckpointBarrier barrier) {
        if (finished) {
            // Finished-source final-checkpoint exception: inject directly on the
            // injector thread (no task thread exists to consume a mail).
            try {
                injectBarrier(barrier);
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_BARRIER_INJECTION_FAILED, e).param(ARG_DETAIL, "after source finished");
            }
            return true;
        }
        MailboxExecutor exec = this.mailboxExecutor;
        if (exec == null) {
            // Isolated usage (e.g. unit tests without a task): direct inject on caller
            // thread. The mailbox-based handoff requires a wired MailboxExecutor.
            try {
                injectBarrier(barrier);
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_BARRIER_INJECTION_FAILED, e).param(ARG_DETAIL, "no mailbox wired");
            }
            return true;
        }
        // Mailbox-based handoff: the mail runs snapshotState + emitBarrier on the task
        // thread when drained at the SourceContext.collect() emission point.
        exec.getMailbox().put(Mail.control(
                () -> {
                    try {
                        injectBarrier(barrier);
                    } catch (Exception e) {
                        throw new StreamException(ERR_STREAM_BARRIER_INJECTION_FAILED, e)
                                .param(ARG_DETAIL, "trigger-checkpoint mail " + barrier.getId());
                    }
                },
                "trigger-checkpoint-" + barrier.getId()));
        return true;
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
     * which drains the control-plane mailbox (processing trigger-checkpoint mails) before
     * emitting each record, so that source {@code snapshotState}/{@code emitBarrier} run
     * on this task thread.
     *
     * @throws Exception if the source function fails
     */
    public void run() throws Exception {
        SourceFunction.SourceContext<OUT> ctx = new SourceFunction.SourceContext<OUT>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void collect(OUT element) {
                drainControlMails();
                // G52: per-record liveness marker for SOURCE / SELF_CONTAINED.
                markProgress();
                output.collect(new StreamRecord<>(element));
            }

            @Override
            public void collectWithTimestamp(OUT element, long timestamp) {
                drainControlMails();
                markProgress();
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
        // Mark finished BEFORE draining so that concurrent offerBarrier() calls will
        // either (a) see finished=true and inject directly, or (b) see finished=false,
        // put a trigger-checkpoint mail, and we drain it here on the task thread.
        finished = true;
        // Drain any trigger-checkpoint mail that arrived between the last collect() and
        // finished=true. Mirrors the legacy cap-1 drainAndInjectPendingBarriers().
        drainControlMails();
    }

    /**
     * Drains and runs all pending control-plane mails (trigger-checkpoint, cancel marker)
     * on this task thread. Replaces the legacy cap-1 {@code pendingBarriers} handoff with
     * a mailbox drain at the {@code SourceContext} emission point. If the mailbox
     * executor reports the task has been cooperatively cancelled (abort path), a
     * checkpoint-aborted exception is thrown to unwind the source function cooperatively
     * rather than relying solely on {@link InterruptedException} from interrupt.
     */
    private void drainControlMails() {
        MailboxExecutor exec = this.mailboxExecutor;
        if (exec != null) {
            exec.processAvailableMails();
            if (exec.isCancelled()) {
                throw new StreamException(ERR_STREAM_CHECKPOINT_ABORTED)
                        .param(ARG_REASON, "source cancelled via mailbox (cooperative abort)");
            }
        }
    }

    /**
     * G52: invokes the wired progress marker (if any). Called from
     * {@link SourceFunction.SourceContext#collect} so the owning invokable's
     * {@code lastProgressTime} tracks every emitted record.
     */
    private void markProgress() {
        Runnable m = this.progressMarker;
        if (m != null) {
            m.run();
        }
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
        try {
            sourceFunction.cancel();
        } catch (Exception e) {
            LOG.warn("Error cancelling source function during close", e);
        }
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
        if (sourceFunction instanceof ReplayableSourceFunction) {
            long offset = ((ReplayableSourceFunction<?>) sourceFunction).getCurrentOffset();
            result.putOperatorState(SOURCE_OFFSET_KEY, offset);
        }
        if (sourceFunction instanceof CheckpointedSourceFunction) {
            OperatorSnapshotResult sourceResult =
                    ((CheckpointedSourceFunction<?>) sourceFunction).snapshotState(context.getCheckpointId());
            if (sourceResult != null) {
                result.merge(sourceResult);
            }
        }
        return result;
    }

    @Override
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        super.restoreState(snapshotResult);
        if (sourceFunction instanceof ReplayableSourceFunction && snapshotResult != null) {
            Object offsetObj = snapshotResult.getOperatorState(SOURCE_OFFSET_KEY);
            if (offsetObj != null) {
                long offset;
                if (offsetObj instanceof Number) {
                    offset = ((Number) offsetObj).longValue();
                } else {
                    offset = Long.parseLong(String.valueOf(offsetObj));
                }
                ((ReplayableSourceFunction<?>) sourceFunction).seek(offset);
            }
        }
        if (sourceFunction instanceof CheckpointedSourceFunction) {
            TaskStateSnapshot taskState = new TaskStateSnapshot(new TaskLocation("", "", "", 0));
            if (snapshotResult != null) {
                for (Map.Entry<String, Object> entry : snapshotResult.getOperatorStates().entrySet()) {
                    taskState.putOperatorState(entry.getKey(), entry.getValue());
                }
                for (Map.Entry<String, Object> entry : snapshotResult.getKeyedStates().entrySet()) {
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
