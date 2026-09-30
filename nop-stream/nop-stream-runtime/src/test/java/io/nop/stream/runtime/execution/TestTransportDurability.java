package io.nop.stream.runtime.execution;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.runtime.transport.DataPlaneMessageServiceAdapter;
import io.nop.stream.runtime.transport.IdentityWireCodec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plan 369 Phase 1 C4 (runtime fill): the deployment wiring that actually
 * knows the transport fills the {@link CheckpointConfig} persistence
 * declaration from the injected {@code IMessageService}. The in-memory
 * {@link LocalMessageService} is classified NON_DURABLE; unknown transports
 * keep the conservative DURABLE default.
 */
class TestTransportDurability {

    @Test
    void testLocalInMemoryTransportIsNonDurable() {
        LocalMessageService service = new LocalMessageService();
        assertEquals(CheckpointConfig.TransportPersistence.NON_DURABLE,
                TransportDurability.resolve(service),
                "the in-memory broker loses in-flight records on process failure");
    }

    @Test
    void testAdapterDelegatesToUnderlyingTransport() {
        DataPlaneMessageServiceAdapter adapter = new DataPlaneMessageServiceAdapter(
                new LocalMessageService(), IdentityWireCodec.INSTANCE);
        assertEquals(CheckpointConfig.TransportPersistence.NON_DURABLE,
                TransportDurability.resolve(adapter),
                "the adapter is a view — the underlying transport decides");
    }

    @Test
    void testUnknownTransportDefaultsToDurable() {
        IMessageService unknown = new IMessageService() {
            @Override
            public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message,
                    io.nop.api.core.message.MessageSendOptions options) {
                throw new UnsupportedOperationException("test stub");
            }

            @Override
            public io.nop.api.core.message.IMessageSubscription subscribe(String topic,
                    io.nop.api.core.message.IMessageConsumer listener,
                    io.nop.api.core.message.MessageSubscribeOptions options) {
                throw new UnsupportedOperationException("test stub");
            }
        };
        assertEquals(CheckpointConfig.TransportPersistence.DURABLE,
                TransportDurability.resolve(unknown),
                "an unknown transport is declared durable (conservative default)");
    }

    @Test
    void testNullTransportDefaultsToDurable() {
        assertEquals(CheckpointConfig.TransportPersistence.DURABLE,
                TransportDurability.resolve(null));
    }
}
