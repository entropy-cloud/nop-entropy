/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.transport.StreamElementCodec;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.StreamElement;

/**
 * Consumer-side channel that receives data from a {@link RemoteResultPartition}
 * via {@link IMessageService}.
 *
 * <p>Subscribes to a message service topic and decodes incoming
 * {@link StreamMessageEnvelope} instances back into {@link StreamElement}.
 * Decoded elements are placed into a local {@link LinkedBlockingQueue} for
 * consumption by the task thread.
 *
 * <p><strong>Fencing:</strong> Only envelopes whose fencing token and epoch id
 * match the expected values are accepted. Stale messages are silently discarded.
 *
 * <p><strong>Lifecycle:</strong>
 * <ol>
 *   <li>Constructor subscribes to the topic</li>
 *   <li>{@link #read()} / {@link #read(long, TimeUnit)} consume from the local queue</li>
 *   <li>When an END_OF_STREAM control message is received, the channel is marked as finished</li>
 *   <li>{@link #close()} cancels the subscription</li>
 * </ol>
 */
public class RemoteInputChannel extends InputChannel {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteInputChannel.class);

    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    /** Sentinel placed into the queue to signal end-of-stream. */
    private static final StreamElement END_OF_STREAM = new StreamElement() {};

    private final LinkedBlockingQueue<StreamElement> queue;
    private final String expectedFencingToken;
    private final long expectedEpochId;
    private final IMessageSubscription subscription;
    private volatile boolean finished;

    /**
     * Creates a RemoteInputChannel that subscribes to the given topic.
     *
     * @param messageService      the message service to subscribe to
     * @param topic               the topic to subscribe to
     * @param expectedFencingToken expected fencing token for message filtering
     * @param expectedEpochId      expected epoch id for message filtering
     */
    public RemoteInputChannel(IMessageService messageService,
                              String topic,
                              String expectedFencingToken,
                              long expectedEpochId) {
        this(messageService, topic, expectedFencingToken, expectedEpochId,
                DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Creates a RemoteInputChannel with a custom queue capacity.
     *
     * @param messageService       the message service to subscribe to
     * @param topic                the topic to subscribe to
     * @param expectedFencingToken expected fencing token for message filtering
     * @param expectedEpochId      expected epoch id for message filtering
     * @param queueCapacity        capacity of the local element queue
     */
    public RemoteInputChannel(IMessageService messageService,
                              String topic,
                              String expectedFencingToken,
                              long expectedEpochId,
                              int queueCapacity) {
        // Pass a dummy partition to the parent; we override all read methods
        super(new ResultPartition(1));
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.expectedFencingToken = expectedFencingToken;
        this.expectedEpochId = expectedEpochId;
        this.finished = false;

        // Subscribe to the topic
        this.subscription = messageService.subscribe(topic, new EnvelopeConsumer());
        LOG.info("RemoteInputChannel subscribed to topic={}, fencingToken={}, epochId={}",
                topic, expectedFencingToken, expectedEpochId);
    }

    /**
     * Reads the next element from the local queue (blocking).
     *
     * @return the next element, or null if end-of-stream
     * @throws InterruptedException if interrupted while waiting
     */
    @Override
    public StreamElement read() throws InterruptedException {
        StreamElement element = queue.take();
        if (element == END_OF_STREAM) {
            return null;
        }
        return element;
    }

    /**
     * Reads the next element with a timeout.
     *
     * @param timeout maximum wait time
     * @param unit    time unit
     * @return the next element, or null on timeout / end-of-stream
     * @throws InterruptedException if interrupted while waiting
     */
    @Override
    public StreamElement read(long timeout, TimeUnit unit) throws InterruptedException {
        StreamElement element = queue.poll(timeout, unit);
        if (element == null) {
            return null;
        }
        if (element == END_OF_STREAM) {
            return null;
        }
        return element;
    }

    /**
     * Returns whether the upstream producer has finished sending.
     */
    @Override
    public boolean isFinished() {
        return finished;
    }

    /**
     * Cancels the message subscription and releases resources.
     */
    public void close() {
        if (subscription != null && !subscription.isCancelled()) {
            subscription.cancel();
        }
        // Ensure readers can unblock
        if (!finished) {
            finished = true;
            queue.offer(END_OF_STREAM);
        }
    }

    /**
     * Returns the current number of elements waiting in the local queue.
     */
    public int queueSize() {
        return queue.size();
    }

    /**
     * IMessageConsumer that decodes envelopes and puts elements into the local queue.
     */
    private class EnvelopeConsumer implements IMessageConsumer {

        @Override
        public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
            if (finished) {
                return null;
            }

            if (!(message instanceof StreamMessageEnvelope)) {
                LOG.warn("Ignoring non-envelope message on topic={}: {}", topic, message);
                return null;
            }

            StreamMessageEnvelope envelope = (StreamMessageEnvelope) message;

            // Fencing token verification
            if (expectedFencingToken != null
                    && !expectedFencingToken.equals(envelope.getFencingToken())) {
                LOG.debug("Discarding stale message: expected fencingToken={}, got={}",
                        expectedFencingToken, envelope.getFencingToken());
                return null;
            }

            // Epoch id verification
            if (envelope.getEpochId() != expectedEpochId) {
                LOG.debug("Discarding stale message: expected epochId={}, got={}",
                        expectedEpochId, envelope.getEpochId());
                return null;
            }

            // Handle END_OF_STREAM control message
            if (StreamMessageEnvelope.TYPE_CONTROL.equals(envelope.getType())) {
                Object payload = envelope.getPayload();
                if ("END_OF_STREAM".equals(payload)) {
                    finished = true;
                    queue.offer(END_OF_STREAM);
                    return null;
                }
            }

            // Decode the element
            try {
                StreamElement element = StreamElementCodec.decode(envelope);
                queue.put(element);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while enqueueing decoded element", e);
            } catch (Exception e) {
                LOG.error("Failed to decode envelope on topic={}", topic, e);
            }

            return null;
        }
    }
}
