/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.ResultPartition;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import io.nop.stream.core.execution.transport.StreamElementCodec;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamElement;

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

    private final IMessageService messageService;
    private final String topic;
    private final TypeRegistry typeRegistry;
    private final String edgeId;
    private final long epochId;

    /**
     * Stage 43: idle-heartbeat interval in milliseconds. When {@code > 0} the
     * producer emits a {@link StreamMessageEnvelope#CONTROL_HEARTBEAT} envelope
     * whenever no data record has been sent for this interval (see
     * {@link #sendHeartbeatIfIdle()}). {@code <= 0} disables heartbeat emission
     * (back-compat default for callers that do not opt in).
     */
    private final long heartbeatIntervalMs;

    /**
     * Stage 43: monotonic timestamp of the last <em>data</em> record send. Read
     * by {@link #sendHeartbeatIfIdle()} to decide whether the channel is idle.
     * Heartbeats themselves do NOT refresh this timestamp (a heartbeat is not
     * data progress). {@code AtomicLong} so the heartbeat scheduler thread and
     * the producer thread agree on visibility without locking {@code write()}.
     */
    private final AtomicLong lastDataSendTime = new AtomicLong(System.currentTimeMillis());

    /**
     * Stage 43: handle of the scheduled heartbeat task, if {@link
     * #startHeartbeat(ScheduledExecutorService)} was called. Stored so callers /
     * tests can cancel it on close.
     */
    private volatile ScheduledFuture<?> heartbeatTask;

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
        this(messageService, topic, typeRegistry, edgeId, epochId, 0L);
    }

    /**
     * Stage 43: full constructor with idle-heartbeat emission enabled.
     *
     * <p>When {@code heartbeatIntervalMs > 0}, the producer should be wired to a
     * shared scheduler via {@link #startHeartbeat(ScheduledExecutorService)} so
     * that {@link #sendHeartbeatIfIdle()} is invoked periodically. The scheduler
     * is intentionally not owned per-partition (the plan forbids a dedicated
     * timer thread per channel); the caller (e.g. task runtime) supplies one
     * shared executor.
     *
     * @param messageService      the message service for sending data
     * @param topic               the topic to send to
     * @param typeRegistry        registry for looking up output types per edge
     * @param edgeId              the edge identifier for type lookup
     * @param epochId             monotonic fencing epoch for the current job execution
     *                            (Stage 39: the single long fencing key)
     * @param heartbeatIntervalMs idle-heartbeat interval in ms; {@code <= 0} disables
     */
    public RemoteResultPartition(IMessageService messageService,
                                 String topic,
                                 TypeRegistry typeRegistry,
                                 String edgeId,
                                 long epochId,
                                 long heartbeatIntervalMs) {
        // Pass capacity 1 to parent; the queue is never actually used
        super(1);
        this.messageService = messageService;
        this.topic = topic;
        this.typeRegistry = typeRegistry;
        this.edgeId = edgeId;
        this.epochId = epochId;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    /**
     * Encodes the element and sends it via IMessageService.
     *
     * @param element the element to write (must not be null)
     * @throws InterruptedException if the thread is interrupted
     * @throws IllegalStateException if the partition is already finished
     */
    @Override
    public synchronized void write(StreamElement element) throws InterruptedException {
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
        messageService.send(topic, envelope);
        // Stage 43: a data record was sent — refresh the idle-heartbeat clock.
        // Barriers/watermarks are written via write() too and also count as
        // producer liveness (they prove the producer is driving the stream).
        lastDataSendTime.set(System.currentTimeMillis());
    }

    @Override
    public synchronized void close() {
        if (isFinished()) {
            return;
        }
        markFinished();

        // Stage 43: stop the heartbeat task before sending EOS so no heartbeat
        // races past the terminal control message.
        stopHeartbeat();

        // Send end-of-stream control message
        StreamMessageEnvelope eos = new StreamMessageEnvelope(
                epochId,
                StreamMessageEnvelope.TYPE_CONTROL, null,
                StreamMessageEnvelope.CONTROL_END_OF_STREAM);
        try {
            messageService.send(topic, eos);
        } catch (Exception e) {
            LOG.warn("Failed to send END_OF_STREAM on topic={}", topic, e);
        }
    }

    /**
     * Stage 43: schedules periodic {@link #sendHeartbeatIfIdle()} on the given
     * shared scheduler. No-op (returns {@code null}) when heartbeat emission is
     * disabled ({@code heartbeatIntervalMs <= 0}). The caller owns the scheduler
     * lifetime; this method only stores the {@link ScheduledFuture} so {@link
     * #close()} can cancel it.
     *
     * <p>This never spawns a per-channel thread — the supplied executor is shared
     * across all partitions of the task/job (plan Phase 1 threading model).
     *
     * @param scheduler a shared scheduled executor (typically owned by the task runtime)
     * @return the scheduled future for cancellation, or {@code null} if disabled
     */
    public ScheduledFuture<?> startHeartbeat(ScheduledExecutorService scheduler) {
        if (heartbeatIntervalMs <= 0 || scheduler == null) {
            return null;
        }
        this.heartbeatTask = scheduler.scheduleAtFixedRate(
                this::sendHeartbeatIfIdle,
                heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
        return this.heartbeatTask;
    }

    /**
     * Stage 43: cancels the scheduled heartbeat task if one was started. Safe
     * no-op when not scheduled. Called from {@link #close()}.
     */
    public void stopHeartbeat() {
        ScheduledFuture<?> task = this.heartbeatTask;
        if (task != null) {
            task.cancel(false);
            this.heartbeatTask = null;
        }
    }

    /**
     * Stage 43: emits a lightweight idle-heartbeat envelope if (a) heartbeat is
     * enabled, (b) the partition is not finished, and (c) no data record has
     * been sent for at least {@link #heartbeatIntervalMs}. Public so tests can
     * drive the heartbeat deterministically without a real timer.
     *
     * <p>The heartbeat is a {@link StreamMessageEnvelope#TYPE_CONTROL} envelope
     * with payload {@link StreamMessageEnvelope#CONTROL_HEARTBEAT}, carrying the
     * current fencing {@code epochId}. It is distinguishable from data and from
     * {@link StreamMessageEnvelope#CONTROL_END_OF_STREAM}.
     *
     * @return {@code true} if a heartbeat was sent on this invocation
     */
    public synchronized boolean sendHeartbeatIfIdle() {
        if (heartbeatIntervalMs <= 0 || isFinished()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long idleFor = now - lastDataSendTime.get();
        if (idleFor < heartbeatIntervalMs) {
            return false;
        }
        StreamMessageEnvelope heartbeat = new StreamMessageEnvelope(
                epochId,
                StreamMessageEnvelope.TYPE_CONTROL, null,
                StreamMessageEnvelope.CONTROL_HEARTBEAT);
        try {
            messageService.send(topic, heartbeat);
            LOG.debug("Sent idle heartbeat on topic={}, idleForMs={}", topic, idleFor);
            return true;
        } catch (Exception e) {
            // A heartbeat send failure is not fatal — the consumer-side timeout
            // will detect sustained failure. Log at debug so transient backend
            // hiccups do not spam.
            LOG.debug("Failed to send heartbeat on topic={}", topic, e);
            return false;
        }
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
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
