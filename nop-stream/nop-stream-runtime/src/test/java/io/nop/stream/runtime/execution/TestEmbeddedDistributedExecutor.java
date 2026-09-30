package io.nop.stream.runtime.execution;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEmbeddedDistributedExecutor {

    /**
     * Constructor smoke test (all three constructor shapes). Deliberately does
     * NOT claim to verify nodeCount/timeout semantics: the fields are private
     * without getters and are only consumed via the private
     * {@code determineNodeCount}/{@code waitForCompletion} during deploy/stop —
     * parameter behavior is exercised by the deploy E2E tests.
     */
    @Test
    void constructorVariantsDoNotThrow() {
        assertNotNull(new EmbeddedDistributedExecutor(new TestMessageService()));
        assertNotNull(new EmbeddedDistributedExecutor(new TestMessageService(), 4));
        assertNotNull(new EmbeddedDistributedExecutor(new TestMessageService(), 2, 120));
    }

    @Test
    void testSupportsDistributedMode() {
        EmbeddedDistributedExecutor executor = new EmbeddedDistributedExecutor(new TestMessageService());
        assertTrue(executor.supportsDeploymentMode(
                io.nop.stream.core.execution.DeploymentMode.DISTRIBUTED));
        assertFalse(executor.supportsDeploymentMode(
                io.nop.stream.core.execution.DeploymentMode.LOCAL));
    }

    private static final IMessageSubscription STUB_SUBSCRIPTION = new IMessageSubscription() {
        @Override public void cancel() {}
        @Override public boolean isSuspended() { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public void suspend() {}
        @Override public void resume() {}
    };

    private static class TestMessageService implements IMessageService {
        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer consumer, MessageSubscribeOptions options) {
            return STUB_SUBSCRIPTION;
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
