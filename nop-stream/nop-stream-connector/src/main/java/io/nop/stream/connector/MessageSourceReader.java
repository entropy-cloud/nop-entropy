/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_JOB_EXECUTE_FAILED;

/**
 * WI2 (plan 2343 Phase 2): FLIP-27 reader of the message source — embeds the legacy
 * {@link MessageSourceFunction} (push subscription) on a daemon thread and converts the
 * push into the pull {@link #pollNext()} through a bounded queue.
 *
 * <p>Contract pins (plan M-4):
 * <ul>
 *   <li>{@code pollNext} blocks at most {@value #POLL_TIMEOUT_MILLIS} ms on the queue —
 *       checkpoint/cancel mailbox latency is bounded by the same value (the operator
 *       drains mails between polls);</li>
 *   <li>queue-full = the subscription thread blocks on {@code put} — the SAME
 *       backpressure semantics as the legacy synchronous {@code ctx.collect};</li>
 *   <li>errors (payload type mismatch, collect failure, terminal subscription error)
 *       are captured by the legacy function's P1-9 logic and rethrown from {@code run()}
 *       — this reader stashes the thread's failure and rethrows it from {@code pollNext}
 *       (typed task failure, never a silent EOS);</li>
 *   <li>{@code close()} unsubscribes via the legacy cancel (R5-CON-04 parity) and joins
 *       the daemon thread with a typed-failure timeout.</li>
 * </ul>
 */
public class MessageSourceReader<T> implements SourceReader<T, MessageTopicSplit> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(MessageSourceReader.class);

    static final long POLL_TIMEOUT_MILLIS = 100L;
    static final int QUEUE_CAPACITY = 256;
    static final long CLOSE_JOIN_TIMEOUT_MILLIS = 5000L;

    private final SourceFunction<T> embeddedFunction;

    private transient SourceReaderContext context;
    private transient BlockingQueue<Object> queue;
    private transient volatile Thread runnerThread;
    private transient volatile Throwable runnerFailure;
    private transient MessageTopicSplit token;
    private volatile boolean running;

    public MessageSourceReader(SourceFunction<T> embeddedFunction) {
        if (embeddedFunction == null) {
            throw new IllegalArgumentException("embeddedFunction is required");
        }
        this.embeddedFunction = embeddedFunction;
    }

    @Override
    public void start() {
        running = true;
        if (token != null) {
            launchRunner();
        }
        // no token yet: pollNext requests the assignment, then launches
    }

    private void launchRunner() {
        if (runnerThread != null) {
            return;
        }
        queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        runnerFailure = null;
        Thread thread = new Thread(this::runEmbedded, "message-source-reader-"
                + (context == null ? "?" : context.getSubtaskIndex()));
        thread.setDaemon(true);
        thread.start();
        runnerThread = thread;
    }

    private void runEmbedded() {
        try {
            embeddedFunction.run(new QueueSourceContext());
        } catch (Throwable e) {
            // The legacy function's P1-9 capture-and-rethrow lands here: payload type
            // mismatch, collect failure, or a terminal subscription error. Stash and
            // surface from pollNext — a dead subscription must fail the task, never
            // masquerade as EOS.
            runnerFailure = e;
            LOG.warn("Message source runner terminated with failure", e);
        }
    }

    @Override
    public void addSplits(List<MessageTopicSplit> splits) {
        if (splits == null || splits.isEmpty()) {
            return;
        }
        MessageTopicSplit newToken = splits.get(splits.size() - 1);
        if (this.token == null) {
            this.token = newToken;
            if (running) {
                launchRunner();
            }
            return;
        }
        if (!this.token.splitId().equals(newToken.splitId())) {
            throw new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED)
                    .param(ARG_DETAIL, "MessageSourceReader already holds token " + this.token.splitId()
                            + "; a different token (" + newToken.splitId()
                            + ") cannot be adopted — one subscription per reader");
        }
        // same token re-delivered (post-restore re-registration): idempotent
    }

    @Override
    public Optional<T> pollNext() throws Exception {
        if (!running) {
            return Optional.empty();
        }
        if (token == null) {
            // request the subscription token once per idle stretch
            if (context != null && context.getAssignmentProxy() != null) {
                context.getAssignmentProxy().requestSplits(context.getSubtaskIndex(),
                        Optional.empty());
            }
            return Optional.empty();
        }
        if (queue == null) {
            launchRunner();
        }
        Object value = queue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        if (value == null) {
            // idle poll: surface a terminal runner failure even without new records
            if (runnerFailure != null) {
                throw asException(runnerFailure);
            }
            return Optional.empty();
        }
        if (runnerFailure != null) {
            // fail fast on the first poll after the runner died
            throw asException(runnerFailure);
        }
        @SuppressWarnings("unchecked")
        T typed = value == NullValueMarker.INSTANCE ? null : (T) value;
        return Optional.of(typed);
    }

    private StreamException asException(Throwable failure) {
        if (failure instanceof StreamException) {
            return (StreamException) failure;
        }
        if (failure instanceof Exception) {
            return (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED, failure)
                    .param(ARG_DETAIL, "message source failed: " + failure.getMessage());
        }
        return (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED)
                .param(ARG_DETAIL, "message source failed: " + failure);
    }

    @Override
    public boolean isFinished() {
        // UNBOUNDED: the reader only stops via close/cancel
        return !running;
    }

    @Override
    public List<MessageTopicSplit> snapshotState(long checkpointId) {
        return token == null ? List.of() : List.of(token);
    }

    @Override
    public void restoreState(List<MessageTopicSplit> splits) {
        if (splits == null || splits.isEmpty()) {
            return;
        }
        // the token carries no traversal truth; adopt it idempotently (same splitId)
        addSplits(splits);
    }

    @Override
    public void close() throws Exception {
        running = false;
        embeddedFunction.cancel();
        Thread thread = runnerThread;
        if (thread != null) {
            thread.join(CLOSE_JOIN_TIMEOUT_MILLIS);
            if (thread.isAlive()) {
                throw (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED)
                        .param(ARG_DETAIL, "message source runner did not terminate within "
                                + CLOSE_JOIN_TIMEOUT_MILLIS + "ms of close()");
            }
        }
        if (queue != null) {
            queue.clear();
        }
        LOG.info("MessageSourceReader closed (subtask={})",
                context == null ? "?" : context.getSubtaskIndex());
    }

    void setContextForTest(SourceReaderContext ctx) {
        this.context = ctx;
    }

    /** Feeds the embedded legacy function's pushes into the bounded queue. */
    private class QueueSourceContext implements SourceFunction.SourceContext<T> {

        @Override
        public void collect(T element) {
            try {
                // blocking put = legacy synchronous-collect backpressure parity
                queue.put(element == null ? NullValueMarker.INSTANCE : element);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED, e)
                        .param(ARG_DETAIL, "message source queue put interrupted (shutdown)");
            }
        }

        @Override
        public void collectWithTimestamp(T element, long timestamp) {
            // the FLIP-27 bridge carries no timestamps (parity with the legacy collect)
            collect(element);
        }

        @Override
        public void emitWatermark(long mark) {
            // message payloads carry no watermark semantics
        }

        @Override
        public void markAsTemporarilyIdle() {
            // not observable through the queue bridge
        }

        @Override
        public long getProcessingTime() {
            return System.currentTimeMillis();
        }

        @Override
        public boolean isCancelled() {
            return !running;
        }
    }

    /** Null-marker wrapper: ArrayBlockingQueue rejects nulls. */
    enum NullValueMarker {
        INSTANCE
    }
}
