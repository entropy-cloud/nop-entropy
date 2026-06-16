package io.nop.ai.agent.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * {@link IAgentMessageHandler} adapter that bridges the existing messenger
 * subscription model to a deferred-ack {@link IMailbox}. When a message
 * arrives on a topic the messenger is subscribed to, this handler offers the
 * envelope to the associated mailbox and returns {@code null} (ASYNC messages
 * need no response).
 *
 * <p>This lets a deferred-ack mailbox be wired into the message flow via the
 * existing {@link IAgentMessenger#registerHandler} API — <strong>without</strong>
 * changing the {@code IAgentMessenger} interface (zero surface intrusion). The
 * consumer (Actor execution loop or test consumer) later {@link IMailbox#poll
 * polls} the mailbox and explicitly {@link IMailbox#ack acks}/
 * {@link IMailbox#nack nacks} the entry.
 *
 * <p><b>Offer-failure handling</b>: when {@code offer} returns {@code false}
 * (bounded mailbox full, or a {@link NoOpMailbox}), the handler logs a WARN
 * and returns {@code null}. Under fire-and-forget ASYNC semantics, dropping
 * the message is an acceptable degradation; the WARN makes the degradation
 * visible rather than silently swallowed.
 *
 * <p>See plan 216 (L4-5).
 */
public final class MailboxMessageHandler implements IAgentMessageHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MailboxMessageHandler.class);

    private final IMailbox mailbox;

    public MailboxMessageHandler(IMailbox mailbox) {
        this.mailbox = Objects.requireNonNull(mailbox, "mailbox");
    }

    public IMailbox getMailbox() {
        return mailbox;
    }

    @Override
    public Object onMessage(AgentMessageEnvelope envelope) {
        boolean accepted = mailbox.offer(envelope);
        if (!accepted) {
            LOG.warn("nop.ai.agent.mailbox.offer-rejected: mailbox refused message "
                            + "(bounded full or NoOp); message dropped under fire-and-forget ASYNC semantics. "
                            + "kind={}, targetTopic={}, correlationId={}",
                    envelope == null ? "null" : envelope.getKind(),
                    envelope == null ? "null" : envelope.getTargetTopic(),
                    envelope == null ? "null" : envelope.getCorrelationId());
        }
        // ASYNC message — no response expected.
        return null;
    }
}
