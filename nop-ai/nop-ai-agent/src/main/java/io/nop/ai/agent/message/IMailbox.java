package io.nop.ai.agent.message;

/**
 * Deferred-acknowledgement mailbox contract for buffering inter-agent messages
 * until a consumer explicitly confirms (ack) or rejects (nack) them. This is
 * the Agent-domain primitive that decouples <em>message arrival</em> from
 * <em>message processing completion</em>: a producer {@link #offer offers} a
 * message, a consumer later {@link #poll polls} it, processes it, then
 * {@link #ack acks} (success) or {@link #nack nacks} (failure).
 *
 * <p>The contract implements a <strong>3-phase reservation protocol</strong>:
 *
 * <ol>
 *     <li><b>Reserve / Offer</b> — {@link #offer} delivers an
 *         {@link AgentMessageEnvelope} to the mailbox. The mailbox assigns a
 *         unique {@code deliveryId} and stores the entry as
 *         {@link MailboxDeliveryState#PENDING PENDING}. When the mailbox is
 *         bounded and full, {@code offer} returns {@code false} (a back-pressure
 *         signal — the producer may retry, degrade, or drop). It never throws
 *         on capacity exhaustion.</li>
 *     <li><b>Poll / In-flight</b> — {@link #poll} non-blockingly retrieves the
 *         earliest PENDING entry, marks it
 *         {@link MailboxDeliveryState#IN_FLIGHT IN_FLIGHT}, and returns it.
 *         When no PENDING entry exists, {@code poll} returns {@code null}.</li>
 *     <li><b>Ack / Nack</b> — {@link #ack} confirms successful processing: the
 *         entry is marked {@link MailboxDeliveryState#ACKED ACKED} and
 *         permanently removed. {@link #nack} rejects the entry: with
 *         {@code requeue=true} the entry returns to PENDING (a new
 *         {@code deliveryId} is assigned and {@code deliveryCount} increments)
 *         for redelivery — unless {@code deliveryCount} has already reached
 *         {@code maxDeliveryAttempts}, in which case the entry is marked
 *         {@link MailboxDeliveryState#DEAD_LETTERED DEAD_LETTERED} and retained
 *         (no further redelivery); with {@code requeue=false} the entry is
 *         marked {@link MailboxDeliveryState#NACKED NACKED} and removed.</li>
 * </ol>
 *
 * <p><b>Thread safety</b>: implementations must be safe for concurrent use by
 * the producer thread (offer) and consumer thread(s) (poll/ack/nack). Offer
 * may run concurrently with poll; ack/nack for a given deliveryId are not
 * expected to be concurrent (a single consumer processes an in-flight entry
 * serially), but the implementation must still guard the shared internal
 * collections.
 *
 * <p><b>Returned {@code MailboxEntry} instances</b> are immutable snapshots.
 * Mutating the mailbox's internal state (e.g. acking an id) does not retro-
 * actively change an entry previously handed out by {@link #poll}; the
 * consumer reads {@link MailboxEntry#getState()} at its own discretion but the
 * authoritative state lives inside the mailbox.
 *
 * <p>See plan 216 (L4-5).
 */
public interface IMailbox {

    /**
     * Phase 1 — deliver a message envelope to this mailbox. The mailbox
     * assigns a unique deliveryId, wraps the envelope in a
     * {@link MailboxEntry} with state PENDING, and stores it.
     *
     * @param envelope the message to deliver (must not be null)
     * @return {@code true} if the message was accepted; {@code false} if the
     *         mailbox is bounded and currently full (back-pressure signal —
     *         never throws on capacity exhaustion)
     * @throws NullPointerException if {@code envelope} is null
     */
    boolean offer(AgentMessageEnvelope envelope);

    /**
     * Phase 2 — non-blockingly retrieve and reserve the earliest PENDING
     * entry. The entry is marked IN_FLIGHT inside the mailbox and returned to
     * the caller for processing.
     *
     * @return the earliest PENDING entry as an immutable snapshot, or
     *         {@code null} when no PENDING entry is available
     */
    MailboxEntry poll();

    /**
     * Phase 3 — acknowledge successful processing of the entry identified by
     * {@code deliveryId}. The entry is marked ACKED and permanently removed
     * from the mailbox.
     *
     * @param deliveryId the delivery identifier returned by
     *                   {@link MailboxEntry#getDeliveryId()} on the polled entry
     * @return {@code true} if an IN_FLIGHT entry with this id was acked;
     *         {@code false} if no such IN_FLIGHT entry exists (unknown / already
     *         resolved id)
     */
    boolean ack(long deliveryId);

    /**
     * Phase 3 — negatively acknowledge the entry identified by
     * {@code deliveryId}.
     *
     * <p>With {@code requeue=true}: if the entry's deliveryCount is below
     * {@code maxDeliveryAttempts}, it returns to PENDING with a new
     * deliveryId and incremented deliveryCount (redelivery); otherwise it is
     * marked DEAD_LETTERED and retained (no further redelivery).
     *
     * <p>With {@code requeue=false}: the entry is marked NACKED and removed
     * (no redelivery).
     *
     * @param deliveryId the delivery identifier returned by
     *                   {@link MailboxEntry#getDeliveryId()} on the polled entry
     * @param requeue    {@code true} to return the entry to PENDING for
     *                   redelivery (subject to {@code maxDeliveryAttempts});
     *                   {@code false} to discard it
     * @return {@code true} if an IN_FLIGHT entry with this id was nacked;
     *         {@code false} if no such IN_FLIGHT entry exists
     */
    boolean nack(long deliveryId, boolean requeue);

    /**
     * @return the number of entries currently in the PENDING state
     */
    int pendingCount();

    /**
     * @return the number of entries currently in the IN_FLIGHT state
     */
    int inFlightCount();
}
