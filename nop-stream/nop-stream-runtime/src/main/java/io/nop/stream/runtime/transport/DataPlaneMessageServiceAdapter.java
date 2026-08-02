/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.concurrent.CompletionStage;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;

/**
 * Wraps an {@link IMessageService} so that data-plane {@link StreamMessageEnvelope}
 * traffic is adapted to / from the backend's faithful wire format via a
 * {@link IDataPlaneWireCodec} (Stage 40).
 *
 * <p><strong>Scope.</strong> This decorator is applied <em>only</em> to the data-plane
 * view of the shared transport (it is what {@code RemoteGraphExecutionPlanBuilder} /
 * {@code RemoteResultPartition} / {@code RemoteInputChannel} see). The control-plane
 * RPC layer keeps using the raw {@link IMessageService}, so the two planes share one
 * backend instance but never mangle each other's messages (topic-addressed, disjoint
 * topics: {@code nop-stream.rpc.*} for control, {@code nop-stream.{jobId}.*} for data).
 *
 * <p><strong>Adaptation.</strong>
 * <ul>
 *   <li>{@code send}: when the payload is a {@link StreamMessageEnvelope}, it is run
 *       through {@link IDataPlaneWireCodec#toWire} before delegating; any other payload
 *       is passed through unchanged (defensive — data-plane traffic is always
 *       envelopes).</li>
 *   <li>{@code subscribe}: the consumer is wrapped so each delivered wire object is run
 *       through {@link IDataPlaneWireCodec#fromWire} before reaching the inner consumer;
 *       undecodable deliveries are discarded (explicit, observable via debug log — not
 *       silently swallowed, plan guide #24).</li>
 * </ul>
 *
 * <p>This keeps {@code RemoteResultPartition} / {@code RemoteInputChannel} fully
 * backend-agnostic: they keep sending / receiving {@link StreamMessageEnvelope}; the
 * codec is selected per deployment (identity for {@code LocalMessageService},
 * {@code SysDaoWireCodec} for the DB backend, {@code PulsarStringWireCodec} for Pulsar).
 */
public class DataPlaneMessageServiceAdapter implements IMessageService {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(DataPlaneMessageServiceAdapter.class);

    private final IMessageService delegate;
    private final IDataPlaneWireCodec codec;

    public DataPlaneMessageServiceAdapter(IMessageService delegate, IDataPlaneWireCodec codec) {
        this.delegate = delegate;
        this.codec = codec;
    }

    public IMessageService getDelegate() {
        return delegate;
    }

    public IDataPlaneWireCodec getCodec() {
        return codec;
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        Object wire = adaptSend(message);
        return delegate.sendAsync(topic, wire, options);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        // Some backends require a non-null subscribeName (Pulsar) — synthesize one when
        // the caller (RemoteInputChannel's 2-arg subscribe passes null) did not provide
        // it. LocalMessageService ignores it; SysDaoMessageService uses it as subscriberId.
        MessageSubscribeOptions effective = options;
        if (effective == null) {
            effective = new MessageSubscribeOptions();
            effective.setSubscribeName("nop-stream-dataplane-" + topic + "-"
                    + Long.toHexString(System.nanoTime()));
        }
        IMessageConsumer adapted = new AdaptingConsumer(listener);
        return delegate.subscribe(topic, adapted, effective);
    }

    @Override
    public String getAckTopic(String topic) {
        return delegate.getAckTopic(topic);
    }

    private Object adaptSend(Object message) {
        if (message instanceof StreamMessageEnvelope) {
            return codec.toWire((StreamMessageEnvelope) message);
        }
        return message;
    }

    /**
     * Consumer wrapper that reconstructs the envelope from the backend's wire shape
     * before forwarding to the inner (data-plane) consumer.
     */
    private final class AdaptingConsumer implements IMessageConsumer {
        private final IMessageConsumer inner;

        AdaptingConsumer(IMessageConsumer inner) {
            this.inner = inner;
        }

        @Override
        public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
            StreamMessageEnvelope envelope = codec.fromWire(message);
            if (envelope == null) {
                LOG.debug("Discarding undecodable data-plane message on topic={}: {}", topic, message);
                return null;
            }
            return inner.onMessage(topic, envelope, context);
        }
    }
}
