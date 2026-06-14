package io.nop.ai.agent.message;

import io.nop.api.core.message.IMessageSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Pass-through {@link IAgentMessenger} used as the default when no message
 * service is configured (e.g. {@code DefaultAgentEngine} with no messenger
 * explicitly wired). Consistent with the {@code NoOpSkillProvider} /
 * {@code NoOpContentGuardrail} sibling pattern.
 *
 * <p><b>No Silent No-Op:</b> {@link #request} fails fast with
 * {@link UnsupportedOperationException} rather than returning a silently
 * empty/null future — so callers discover missing wiring immediately instead of
 * hanging on a future that never completes. {@link #send} is a debug-log no-op
 * (fire-and-forget has no observable return to violate), and
 * {@link #registerHandler} returns a no-op subscription (registration is
 * inert when no transport exists).
 *
 * <p>See plan 166 Phase 2 + Minimum Rule #24 (No Silent No-Op).
 */
public final class NoOpAgentMessenger implements IAgentMessenger {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpAgentMessenger.class);

    private static final NoOpAgentMessenger INSTANCE = new NoOpAgentMessenger();

    private NoOpAgentMessenger() {
    }

    public static IAgentMessenger noOp() {
        return INSTANCE;
    }

    @Override
    public void send(AgentMessageEnvelope envelope) {
        LOG.debug("nop.ai.agent.message.noop-send: kind={}, targetTopic={}",
                envelope == null ? "null" : envelope.getKind(),
                envelope == null ? "null" : envelope.getTargetTopic());
    }

    @Override
    public CompletableFuture<Object> request(AgentMessageEnvelope requestEnvelope, Duration timeout) {
        throw new UnsupportedOperationException(
                "not yet implemented: no message service configured");
    }

    @Override
    public IMessageSubscription registerHandler(String topic, IAgentMessageHandler handler) {
        return new NoOpSubscription();
    }

    private static final class NoOpSubscription implements IMessageSubscription {
        private volatile boolean cancelled = false;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isSuspended() {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void suspend() {
            // no-op
        }

        @Override
        public void resume() {
            // no-op
        }
    }
}
