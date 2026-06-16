package io.nop.ai.agent.message;

/**
 * Pass-through {@link IMailbox} used as the explicit no-op default. Every
 * method returns a result that unambiguously signals "no operation occurred":
 * {@link #offer} returns {@code false} (refused), {@link #poll} returns
 * {@code null} (empty), {@link #ack}/{@link #nack} return {@code false}
 * (nothing resolved), and counts return {@code 0}.
 *
 * <p>This is an <strong>explicit</strong> no-op, not a silent success
 * (Minimum Rules #24): callers that depend on buffering/deferred-ack
 * semantics can distinguish a real mailbox from the no-op by checking
 * {@code offer}'s return value, rather than mistaking a silent success for
 * real delivery.
 *
 * <p>See plan 216 (L4-5).
 */
public final class NoOpMailbox implements IMailbox {

    private static final NoOpMailbox INSTANCE = new NoOpMailbox();

    private NoOpMailbox() {
    }

    public static IMailbox noOp() {
        return INSTANCE;
    }

    @Override
    public boolean offer(AgentMessageEnvelope envelope) {
        // Explicit refusal: the no-op mailbox never buffers.
        return false;
    }

    @Override
    public MailboxEntry poll() {
        return null;
    }

    @Override
    public boolean ack(long deliveryId) {
        return false;
    }

    @Override
    public boolean nack(long deliveryId, boolean requeue) {
        return false;
    }

    @Override
    public int pendingCount() {
        return 0;
    }

    @Override
    public int inFlightCount() {
        return 0;
    }
}
