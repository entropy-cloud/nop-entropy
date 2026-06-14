package io.nop.ai.agent.message;

import io.nop.api.core.message.IMessageSubscription;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNoOpAgentMessenger {

    @Test
    void noOpReturnsSameSingletonInstance() {
        IAgentMessenger a = NoOpAgentMessenger.noOp();
        IAgentMessenger b = NoOpAgentMessenger.noOp();
        assertSame(a, b);
    }

    @Test
    void sendDoesNotThrow() {
        IAgentMessenger noOp = NoOpAgentMessenger.noOp();
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "c1", AgentMessageKind.ASYNC, "p");
        assertDoesNotThrow(() -> noOp.send(env));
    }

    @Test
    void sendDoesNotThrowOnNullEnvelope() {
        // send must be a safe no-op; it should not NPE on null either (debug log only)
        IAgentMessenger noOp = NoOpAgentMessenger.noOp();
        assertDoesNotThrow(() -> noOp.send(null));
    }

    @Test
    void requestFailsFastWithUnsupportedOperationException() {
        IAgentMessenger noOp = NoOpAgentMessenger.noOp();
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "c1", AgentMessageKind.REQUEST, "p");

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> noOp.request(env, Duration.ofSeconds(1)));
        assertTrue(ex.getMessage().contains("no message service configured"),
                "exception message should explain the missing wiring: " + ex.getMessage());
    }

    @Test
    void registerHandlerReturnsNonNullCancellableSubscription() {
        IAgentMessenger noOp = NoOpAgentMessenger.noOp();
        IMessageSubscription subscription = noOp.registerHandler("agent.B.inbox", envelope -> null);
        assertNotNull(subscription);
        assertDoesNotThrow(subscription::cancel);
        assertTrue(subscription.isCancelled());
    }

    @Test
    void requestNeverReturnsSilentlyCompletingFuture() {
        // The hard requirement: request must NOT return a silently-empty/null
        // future. It must throw. This guards against silent no-op (Rule #24).
        IAgentMessenger noOp = NoOpAgentMessenger.noOp();
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "c1", AgentMessageKind.REQUEST, "p");
        try {
            CompletableFuture<Object> f = noOp.request(env, Duration.ofMillis(50));
            // If we somehow get here, the future must not silently complete empty.
            throw new AssertionError("NoOpAgentMessenger.request should have thrown, but returned: " + f);
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}
