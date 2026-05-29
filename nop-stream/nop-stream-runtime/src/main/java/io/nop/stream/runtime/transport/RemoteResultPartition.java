/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.ResultPartition;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;
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
 * <p>Fencing token / epoch id are carried in every envelope so that the receiver
 * can discard stale messages from a previous job execution.
 *
 * <p>Unlike the base {@link ResultPartition}, this implementation does not use
 * an internal queue. All writes are immediately sent via the message service.
 * Read operations are unsupported on the producer side — the consumer uses
 * {@link RemoteInputChannel} instead.
 */
public class RemoteResultPartition extends ResultPartition {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteResultPartition.class);

    private final IMessageService messageService;
    private final String topic;
    private final TypeRegistry typeRegistry;
    private final String edgeId;
    private final String fencingToken;
    private final long epochId;

    /**
     * Creates a RemoteResultPartition.
     *
     * @param messageService the message service for sending data
     * @param topic          the topic to send to
     * @param typeRegistry   registry for looking up output types per edge
     * @param edgeId         the edge identifier for type lookup
     * @param fencingToken   fencing token for the current job execution
     * @param epochId        epoch id for the current job execution
     */
    public RemoteResultPartition(IMessageService messageService,
                                 String topic,
                                 TypeRegistry typeRegistry,
                                 String edgeId,
                                 String fencingToken,
                                 long epochId) {
        // Pass capacity 1 to parent; the queue is never actually used
        super(1);
        this.messageService = messageService;
        this.topic = topic;
        this.typeRegistry = typeRegistry;
        this.edgeId = edgeId;
        this.fencingToken = fencingToken;
        this.epochId = epochId;
    }

    /**
     * Encodes the element and sends it via IMessageService.
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
                element, valueType, fencingToken, epochId);
        messageService.send(topic, envelope);
    }

    /**
     * Sends end-of-stream control signal to the downstream consumer.
     */
    @Override
    public void close() {
        if (isFinished()) {
            return;
        }
        markFinished();

        // Send end-of-stream control message
        StreamMessageEnvelope eos = new StreamMessageEnvelope(
                fencingToken, epochId,
                StreamMessageEnvelope.TYPE_CONTROL, null,
                "END_OF_STREAM");
        try {
            messageService.send(topic, eos);
        } catch (Exception e) {
            LOG.warn("Failed to send END_OF_STREAM on topic={}", topic, e);
        }
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

    public String getFencingToken() {
        return fencingToken;
    }

    public long getEpochId() {
        return epochId;
    }
}
