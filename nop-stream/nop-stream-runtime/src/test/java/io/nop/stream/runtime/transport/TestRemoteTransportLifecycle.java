/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 358 Phase 1: data-plane transport correctness fixes.
 *
 * <ul>
 *   <li>Fix-1: task teardown must cancel every message-service subscription —
 *       {@code InputGate.close()} closes all channels; each
 *       {@code RemoteInputChannel.close()} cancels its subscription
 *       (wiring verified with a counting stub, plan guide rule #23).</li>
 *   <li>Fix-5: a failed end-of-stream send fails the partition typed (the
 *       downstream reader must never wait forever on a producer whose liveness
 *       signal is already gone).</li>
 *   <li>Fix-7: full-queue enqueues on recovery injection / EOS sentinel placement
 *       are never silently dropped — the channel fails typed.</li>
 *   <li>Fix-8: a wire-level undecodable delivery surfaces as a typed failure on
 *       the consuming channel instead of a silent discard.</li>
 * </ul>
 */
class TestRemoteTransportLifecycle {

    /** Stub backend that counts subscription cancellations and captures the consumer. */
    static class CountingSubscriptionMessageService implements IMessageService {
        final AtomicReference<IMessageConsumer> capturedConsumer = new AtomicReference<>();
        final AtomicInteger subscriptions = new AtomicInteger();
        final AtomicInteger cancellations = new AtomicInteger();

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
            subscriptions.incrementAndGet();
            capturedConsumer.set(listener);
            return new IMessageSubscription() {
                @Override
                public void cancel() {
                    cancellations.incrementAndGet();
                }

                @Override
                public boolean isSuspended() {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return cancellations.get() > 0;
                }

                @Override
                public void suspend() {
                }

                @Override
                public void resume() {
                }
            };
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Stub backend whose send always fails (simulates a broken backend). */
    static class FailingSendmessageService extends CountingSubscriptionMessageService {
        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return CompletableFuture.failedFuture(new IllegalStateException("backend down"));
        }
    }

    // ------------------------------------------------------------------
    // Fix-1: subscription lifecycle wiring
    // ------------------------------------------------------------------

    @Test
    void inputGateCloseCancelsRemoteSubscription() {
        CountingSubscriptionMessageService backend = new CountingSubscriptionMessageService();
        RemoteInputChannel channel = new RemoteInputChannel(backend, "t-lifecycle-1", 1L);
        assertTrue(channel.isSubscriptionActive());
        InputGate gate = new InputGate(Collections.singletonList((InputChannel) channel));

        assertEquals(1, backend.subscriptions.get(), "constructor must subscribe exactly once");
        assertEquals(0, backend.cancellations.get());

        gate.close();
        assertEquals(1, backend.cancellations.get(),
                "InputGate.close must cancel the remote channel's subscription (Fix-1 wiring)");

        gate.close();
        assertEquals(1, backend.cancellations.get(), "InputGate.close must be idempotent");
    }

    @Test
    void remoteChannelCloseIsIdempotentAndCancelsOnce() {
        CountingSubscriptionMessageService backend = new CountingSubscriptionMessageService();
        RemoteInputChannel channel = new RemoteInputChannel(backend, "t-lifecycle-2", 1L);

        channel.close();
        channel.close();
        assertEquals(1, backend.cancellations.get(), "RemoteInputChannel.close must cancel exactly once");
    }

    // ------------------------------------------------------------------
    // Fix-5: EOS send failure fails the partition typed
    // ------------------------------------------------------------------

    @Test
    void eosSendFailureThrowsTypedExceptionAndIsObservable() {
        FailingSendmessageService backend = new FailingSendmessageService();
        RemoteResultPartition partition = new RemoteResultPartition(
                backend, "t-eos-fail", null, "edge-1", 1L);

        StreamException ex = assertThrows(StreamException.class, partition::close,
                "a failed EOS send must fail the partition typed instead of a WARN-only discard");
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), ex.getErrorCode());
        assertNotNull(partition.getEosSendError(), "the captured EOS send error must be observable");
    }

    @Test
    void successfulEosSendDoesNotThrow() {
        CountingSubscriptionMessageService backend = new CountingSubscriptionMessageService();
        RemoteResultPartition partition = new RemoteResultPartition(
                backend, "t-eos-ok", null, "edge-1", 1L);
        partition.close();
        assertEquals(null, partition.getEosSendError(), "a successful EOS send must not record an error");
    }

    // ------------------------------------------------------------------
    // Fix-7: full-queue enqueue verification (no silent data loss / no hang)
    // ------------------------------------------------------------------

    @Test
    void injectElementsOnFullQueueFailsTypedExceptionWithoutSilentDrop() {
        CountingSubscriptionMessageService backend = new CountingSubscriptionMessageService();
        RemoteInputChannel channel = new RemoteInputChannel(backend, "t-inject-full", 1L, 2, 0L);

        // Fill the queue to capacity.
        channel.injectElements(java.util.Arrays.asList(
                newRecord(1), newRecord(2)));

        // Injecting beyond capacity must fail typed — recovery data must never be
        // silently dropped (guide #24).
        StreamException ex = assertThrows(StreamException.class,
                () -> channel.injectElements(java.util.Arrays.asList(newRecord(3))));
        assertEquals(NopStreamErrors.ERR_STREAM_CHANNEL_OVERFLOW.getErrorCode(), ex.getErrorCode());

        // The flagged failure must also be observable at the reader.
        StreamException readEx = assertThrows(StreamException.class, channel::read);
        assertEquals(NopStreamErrors.ERR_STREAM_CHANNEL_OVERFLOW.getErrorCode(), readEx.getErrorCode());
    }

    @Test
    void closeOnFullQueueUnblocksReaderWithTypedException() {
        CountingSubscriptionMessageService backend = new CountingSubscriptionMessageService();
        RemoteInputChannel channel = new RemoteInputChannel(backend, "t-close-full", 1L, 2, 0L);
        channel.injectElements(java.util.Arrays.asList(newRecord(1), newRecord(2)));

        // Close while the queue is full: the EOS sentinel cannot be enqueued, so
        // the reader must observe a typed failure at its next read entry instead
        // of blocking forever on a finished channel.
        channel.close();
        StreamException ex = assertThrows(StreamException.class, channel::read);
        assertEquals(NopStreamErrors.ERR_STREAM_CHANNEL_OVERFLOW.getErrorCode(), ex.getErrorCode());
    }

    // ------------------------------------------------------------------
    // Fix-8: wire-level undecodable delivery fails the channel typed
    // ------------------------------------------------------------------

    @Test
    void wireDecodeFailureSurfacesAsTypedExceptionOnRead() {
        CountingSubscriptionMessageService backend = new CountingSubscriptionMessageService();
        RemoteInputChannel channel = new RemoteInputChannel(backend, "t-wire-bad", 1L);
        DataPlaneMessageServiceAdapter adapter =
                new DataPlaneMessageServiceAdapter(backend, KafkaStringWireCodec.INSTANCE);
        // Subscribe the channel's consumer through the adapter: the backend now
        // delivers to the adapter's wrapper, which forwards to the channel's
        // consumer (EnvelopeConsumer implements WireDecodeFailureAware).
        adapter.subscribe("t-wire-bad", backend.capturedConsumer.get(), null);
        IMessageConsumer adapted = backend.capturedConsumer.get();

        // Deliver a malformed wire payload through the adapted consumer: the
        // string codec throws from fromWire. The adapter must forward the
        // failure to the channel (WireDecodeFailureAware) instead of discarding.
        adapted.onMessage("t-wire-bad", "{not json", (IMessageConsumeContext) null);

        StreamException ex = assertThrows(StreamException.class, channel::read,
                "the channel must fail typed instead of silently losing the record");
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), ex.getErrorCode());
    }

    private static StreamElement newRecord(long value) {
        return new StreamRecord<>(value, value);
    }
}
