/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.channel.ChannelBacklogProbe;
import io.nop.api.core.time.CoreMetrics;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.transport.StreamElementCodec;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TOPIC;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * A {@link ResultPartition} that sends data across TaskManager boundaries via
 * {@link IMessageService}.
 *
 * <p>Each {@code RemoteResultPartition} corresponds to exactly one topic on the
 * message service. Stream elements are encoded into {@link StreamMessageEnvelope}
 * via {@link StreamElementCodec} before sending.
 *
 * <p>The monotonic fencing epoch ({@code epochId}) is carried in every envelope so
 * that the receiver can discard stale messages from a previous job execution /
 * leader / recovery. Stage 39 unified the data plane to a single long epoch
 * comparison (the legacy composite String fencingToken + long epochId dual-key
 * filter is collapsed into one long key).
 *
 * <p>Unlike the base {@link ResultPartition}, this implementation does not use
 * an internal queue. All writes are immediately sent via the message service.
 * Read operations are unsupported on the producer side — the consumer uses
 * {@link RemoteInputChannel} instead.
 *
 * <p><strong>Buffer pool exclusion (intentional, G53)</strong>: this class calls
 * {@code super(1)} and overrides {@link #write(StreamElement)} to send directly via
 * {@code IMessageService}, bypassing both the per-partition queue and the per-job
 * {@code IBufferPool}. Cross-JVM producer-side bound is therefore NOT provided here;
 * it is the responsibility of the {@code IMessageService} backend (Stage 40). This
 * exclusion is by design and documented in {@code 01-architecture-baseline.md} §六,
 * not an accidental omission.
 */
public class RemoteResultPartition extends ResultPartition {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteResultPartition.class);

    /**
     * Serializes all message-service sends (data, EOS). plan 2279 Q2: encoding
     * happens OUTSIDE this lock and only the backend send is inside, so a slow
     * backend (synchronous JDBC write, ms-scale) no longer pins the monitor.
     * Per-partition FIFO is preserved: each producer thread performs its sends
     * under this lock in arrival order. The close() ordering contract (no
     * message past EOS) is enforced by re-checking {@code isFinished()} inside
     * the lock — see {@link #close()}.
     */
    private final Object sendLock = new Object();

    private final IMessageService messageService;
    private final String topic;
    private final TypeRegistry typeRegistry;
    private final String edgeId;
    private final long epochId;

    // ---- WI6 (plan 2331): DB channel producer backpressure gate ----
    /** null = gating not available/not configured (explicit off). */
    private final ChannelBacklogProbe backlogProbe;
    /** > 0 only when gating is enabled. */
    private final long backlogLimit;
    /** Bounded wait before the gate fails typed (mirrors the D2 consume-side default). */
    private final long gateWaitMillis;
    /**
     * Backlog cache TTL for the PASS-THROUGH path. Producer-side growth is accounted
     * locally (each admitted send increments the cached counter — exact under bursts);
     * the DB refresh only observes consumer-side progress. Without the local increment
     * a fast producer would outrun the TTL window and defeat the limit entirely (found
     * by the gated E2E, plan 2331).
     */
    static final long BACKLOG_CACHE_TTL_MILLIS = 200L;
    private long backlogCacheAt;
    private long backlogCacheValue;

    /**
     * Captured when the end-of-stream control message could not be delivered in
     * {@link #close()}. {@code null} until a failed EOS send; observable via
     * {@link #getEosSendError()}.
     */
    private volatile Throwable eosSendError;

    /**
     * Creates a RemoteResultPartition.
     *
     * @param messageService the message service for sending data
     * @param topic          the topic to send to
     * @param typeRegistry   registry for looking up output types per edge
     * @param edgeId         the edge identifier for type lookup
     * @param epochId        monotonic fencing epoch for the current job execution
     *                       (Stage 39: the single long fencing key)
     */
    public RemoteResultPartition(IMessageService messageService,
                                 String topic,
                                 TypeRegistry typeRegistry,
                                 String edgeId,
                                 long epochId) {
        this(messageService, topic, typeRegistry, edgeId, epochId, null, 0L);
    }

    /**
     * WI6 (plan 2331): gating constructor. {@code backlogProbe == null} or
     * {@code backlogLimit <= 0} disables the gate (explicit off — other channels such
     * as Kafka/Pulsar carry their own backpressure and are out of WI6 scope).
     *
     * @param backlogProbe   persistence-backed backlog reader, or null to disable
     * @param backlogLimit   max unconsumed messages per topic before the producer waits
     */
    public RemoteResultPartition(IMessageService messageService,
                                 String topic,
                                 TypeRegistry typeRegistry,
                                 String edgeId,
                                 long epochId,
                                 ChannelBacklogProbe backlogProbe,
                                 long backlogLimit) {
        // Pass capacity 1 to parent; the queue is never actually used
        super(1);
        this.messageService = messageService;
        this.topic = topic;
        this.typeRegistry = typeRegistry;
        this.edgeId = edgeId;
        this.epochId = epochId;
        this.backlogProbe = backlogProbe;
        this.backlogLimit = backlogLimit;
        this.gateWaitMillis = 10_000L;
    }

    public ChannelBacklogProbe getBacklogProbe() {
        return backlogProbe;
    }

    /** WI6: TTL-cached backlog read for the ops gauge (gate off = zero queries, reads 0). */
    public long readBacklogCached() {
        return (backlogProbe == null || backlogLimit <= 0) ? 0L : currentBacklog();
    }

    public long getBacklogLimit() {
        return backlogLimit;
    }

    /**
     * WI6: gated send — waits OUTSIDE the send lock (a waiting producer must never pin
     * the monitor that close() needs for EOS, nor serialize other producer threads
     * behind a DB roundtrip). Barriers go through the same gate in-band: sustained
     * overload fails the write typed so the task restarts, instead of growing the
     * channel table unboundedly (explicit plan 2331 ruling).
     *
     * <p>Accounting: well under the limit the TTL-cached counter is bumped locally per
     * admitted send (exact burst bound — a fast producer can never outrun the TTL
     * window); at the limit the gate force-refreshes from the DB each wait slice, so
     * reopening tracks actual consumption within one slice.
     */
    private void gateOnBacklog() throws InterruptedException {
        if (backlogProbe == null || backlogLimit <= 0) {
            return;
        }
        long cached = peekBacklogCached();
        if (cached < backlogLimit) {
            bumpBacklogCached();
            return;
        }
        long deadline = CoreMetrics.currentTimeMillis() + gateWaitMillis;
        while (true) {
            long fresh = forceBacklog();
            if (fresh < backlogLimit) {
                bumpBacklogCached();
                return;
            }
            if (CoreMetrics.currentTimeMillis() >= deadline) {
                throw new StreamException(NopStreamErrors.ERR_STREAM_SEND_GATE_TIMEOUT)
                        .param(NopStreamErrors.ARG_TOPIC, topic)
                        .param(NopStreamErrors.ARG_BACKLOG_LIMIT, backlogLimit)
                        .param(NopStreamErrors.ARG_CURRENT_BACKLOG, fresh);
            }
            Thread.sleep(100L);
        }
    }

    private synchronized long peekBacklogCached() {
        long now = CoreMetrics.currentTimeMillis();
        if (now - backlogCacheAt >= BACKLOG_CACHE_TTL_MILLIS) {
            backlogCacheValue = backlogProbe.backlog(topic);
            backlogCacheAt = now;
        }
        return backlogCacheValue;
    }

    private synchronized void bumpBacklogCached() {
        backlogCacheValue++;
    }

    private synchronized long forceBacklog() {
        backlogCacheValue = backlogProbe.backlog(topic);
        backlogCacheAt = CoreMetrics.currentTimeMillis();
        return backlogCacheValue;
    }

    /** TTL-cached backlog read (pass-through path must not COUNT per message). */
    private synchronized long currentBacklog() {
        long now = CoreMetrics.currentTimeMillis();
        if (now - backlogCacheAt >= BACKLOG_CACHE_TTL_MILLIS) {
            backlogCacheValue = backlogProbe.backlog(topic);
            backlogCacheAt = now;
        }
        return backlogCacheValue;
    }


    /**
     * Encodes the element and sends it via IMessageService. The encode runs outside
     * the send lock; only the backend send is serialized (plan 2279 Q2), so encode
     * work overlaps with an in-flight send and a slow backend never blocks the
     * writer between records longer than the send itself.
     *
     * @param element the element to write (must not be null)
     * @throws InterruptedException if the thread is interrupted
     * @throws IllegalStateException if the partition is already finished
     */
    @Override
    public void write(StreamElement element) throws InterruptedException {
        if (element == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "element");
        }
        if (isFinished()) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "Cannot write to a finished RemoteResultPartition");
        }

        String valueType = typeRegistry != null ? typeRegistry.getOutputTypeClassName(edgeId) : null;
        StreamMessageEnvelope envelope = StreamElementCodec.encode(
                element, valueType, epochId);
        gateOnBacklog();
        sendUnderLock(envelope);
    }

    /**
     * Sends under {@link #sendLock} with an in-lock finished re-check. The re-check
     * is the data-side counterpart of the EOS ordering contract enforced in
     * {@link #close()}: a write that started before close() but reaches the lock
     * after EOS was sent is rejected instead of landing after the terminal message.
     */
    private void sendUnderLock(StreamMessageEnvelope envelope) throws InterruptedException {
        synchronized (sendLock) {
            if (isFinished()) {
                throw new StreamException(ERR_STREAM_INVALID_STATE)
                        .param(ARG_DETAIL, "Cannot write to a finished RemoteResultPartition");
            }
            messageService.send(topic, envelope);
        }
    }

    @Override
    public void close() {
        if (isFinished()) {
            return;
        }
        markFinished();
        try {
            // Send the end-of-stream control message as the partition's terminal
            // message. EOS is the only notification the consumer ever gets that the
            // producer is done: a lost EOS leaves the downstream reader waiting
            // forever — fail the partition typed so the owning task fails and
            // job-level cancellation unblocks the consumer — never a silent,
            // unbounded downstream wait.
            StreamMessageEnvelope eos = new StreamMessageEnvelope(
                    epochId,
                    StreamMessageEnvelope.TYPE_CONTROL, null,
                    StreamMessageEnvelope.CONTROL_END_OF_STREAM);
            try {
                synchronized (sendLock) {
                    messageService.send(topic, eos);
                }
            } catch (Exception e) {
                eosSendError = e;
                LOG.error("Failed to send END_OF_STREAM on topic={} - failing partition typed so the "
                        + "task/job failure path unblocks the downstream reader", topic, e);
                throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                        .param(ARG_TOPIC, topic)
                        .param(ARG_DETAIL, "Failed to send END_OF_STREAM control message");
            }
        } finally {
            // WI6 residency: release the DB-backlog gauge binding so the gauge reads 0.0
            // after close instead of this closed partition's stale backlog (the meter
            // itself stays registered for a re-deploy re-bind).
            DbBacklogGauges.unbind(topic);
        }
    }

    /**
     * The error captured when the end-of-stream control message could not be
     * delivered (see {@link #close()}), or {@code null} when EOS was sent
     * successfully. Diagnostic hook for tests and ops tooling.
     */
    public Throwable getEosSendError() {
        return eosSendError;
    }

    /**
     * Returns the topic this partition sends to.
     */
    public String getTopic() {
        return topic;
    }

    public IMessageService getMessageService() {
        return messageService;
    }

    public long getEpochId() {
        return epochId;
    }
}
