/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.ResultPartition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TOPIC;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;
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
        // Pass capacity 1 to parent; the queue is never actually used
        super(1);
        this.messageService = messageService;
        this.topic = topic;
        this.typeRegistry = typeRegistry;
        this.edgeId = edgeId;
        this.epochId = epochId;
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
