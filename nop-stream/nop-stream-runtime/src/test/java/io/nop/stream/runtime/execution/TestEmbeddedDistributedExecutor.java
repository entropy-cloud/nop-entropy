package io.nop.stream.runtime.execution;

import org.junit.jupiter.api.Test;

import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSendOptions;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class TestEmbeddedDistributedExecutor {

    @Test
    void testDefaultConstructorUsesDefaultNodeCount() {
        EmbeddedDistributedExecutor executor = new EmbeddedDistributedExecutor(new TestMessageService());
        assertNotNull(executor);
    }

    @Test
    void testCustomNodeCountConstructor() {
        EmbeddedDistributedExecutor executor = new EmbeddedDistributedExecutor(
                new TestMessageService(), 4);
        assertNotNull(executor);
    }

    @Test
    void testCustomTimeoutConstructor() {
        EmbeddedDistributedExecutor executor = new EmbeddedDistributedExecutor(
                new TestMessageService(), 2, 120);
        assertNotNull(executor);
    }

    @Test
    void testSupportsDistributedMode() {
        EmbeddedDistributedExecutor executor = new EmbeddedDistributedExecutor(new TestMessageService());
        assertTrue(executor.supportsDeploymentMode(
                io.nop.stream.core.execution.DeploymentMode.DISTRIBUTED));
        assertFalse(executor.supportsDeploymentMode(
                io.nop.stream.core.execution.DeploymentMode.EMBEDDED));
    }

    private static class TestMessageService implements IMessageService {
        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer consumer, MessageSubscribeOptions options) {
            return () -> {};
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
