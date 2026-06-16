package io.nop.ai.agent.message;

import java.util.Objects;

/**
 * Immutable wrapper carrying an {@link AgentMessageEnvelope} through the
 * 3-phase reservation lifecycle of an {@link IMailbox}, plus the bookkeeping
 * the mailbox needs to track delivery, redelivery, and dead-lettering.
 *
 * <p>A {@code MailboxEntry} is created at {@link IMailbox#offer offer} time
 * with {@code deliveryCount=1} and state {@link MailboxDeliveryState#PENDING}.
 * On each redelivery (via {@link IMailbox#nack nack(requeue=true)}) a
 * <strong>new</strong> {@code MailboxEntry} is produced with a fresh
 * {@code deliveryId} and an incremented {@code deliveryCount}; the previous
 * entry instance is not mutated.
 *
 * <p>Instances are immutable: all fields are set at construction time. State
 * transitions are expressed by the mailbox producing a new entry (or by the
 * consumer reading {@link #getState} after the mailbox internally tracks the
 * transition).
 */
public final class MailboxEntry {

    private final long deliveryId;
    private final int deliveryCount;
    private final MailboxDeliveryState state;
    private final AgentMessageEnvelope envelope;
    private final long offeredAt;
    private final long polledAt;

    public MailboxEntry(long deliveryId, int deliveryCount, MailboxDeliveryState state,
                        AgentMessageEnvelope envelope, long offeredAt, long polledAt) {
        if (state == null) {
            throw new IllegalArgumentException("MailboxEntry: state must not be null");
        }
        this.deliveryId = deliveryId;
        this.deliveryCount = deliveryCount;
        this.state = state;
        this.envelope = envelope;
        this.offeredAt = offeredAt;
        this.polledAt = polledAt;
    }

    /**
     * Unique per-mailbox monotonically-increasing delivery identifier assigned
     * by the mailbox at offer/redelivery time. {@code ack}/{@code nack} match
     * the entry by this id. A redelivered message receives a <strong>new</strong>
     * deliveryId.
     */
    public long getDeliveryId() {
        return deliveryId;
    }

    /**
     * Number of times the underlying message has been delivered. Starts at 1
     * on the first offer and increments on each {@code nack(requeue=true)}
     * redelivery.
     */
    public int getDeliveryCount() {
        return deliveryCount;
    }

    /**
     * Current lifecycle state of this entry inside the mailbox.
     */
    public MailboxDeliveryState getState() {
        return state;
    }

    /**
     * The carried message envelope (never null for a live entry).
     */
    public AgentMessageEnvelope getEnvelope() {
        return envelope;
    }

    /**
     * Wall-clock timestamp (millis) when the entry was first offered to the
     * mailbox. {@code 0} means "not recorded".
     */
    public long getOfferedAt() {
        return offeredAt;
    }

    /**
     * Wall-clock timestamp (millis) when the entry was last polled (transitioned
     * to IN_FLIGHT). {@code 0} means the entry has never been polled.
     */
    public long getPolledAt() {
        return polledAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MailboxEntry)) return false;
        MailboxEntry that = (MailboxEntry) o;
        return deliveryId == that.deliveryId
                && deliveryCount == that.deliveryCount
                && offeredAt == that.offeredAt
                && polledAt == that.polledAt
                && state == that.state
                && Objects.equals(envelope, that.envelope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deliveryId, deliveryCount, state, envelope, offeredAt, polledAt);
    }

    @Override
    public String toString() {
        return "MailboxEntry{deliveryId=" + deliveryId
                + ", deliveryCount=" + deliveryCount
                + ", state=" + state
                + ", offeredAt=" + offeredAt
                + ", polledAt=" + polledAt
                + ", envelope=" + envelope + '}';
    }
}
