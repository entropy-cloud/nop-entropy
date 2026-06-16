package io.nop.ai.agent.message;

/**
 * Lifecycle state of a {@link MailboxEntry} inside an {@link IMailbox},
 * progressing through the 3-phase reservation protocol
 * (offer → poll → ack/nack).
 *
 * <ul>
 *     <li>{@link #PENDING} — the entry has been offered to the mailbox and is
 *         waiting to be polled by a consumer.</li>
 *     <li>{@link #IN_FLIGHT} — the entry has been polled and is currently
 *         being processed by a consumer; it has not yet been acked or nacked.</li>
 *     <li>{@link #ACKED} — the consumer has successfully acknowledged the
 *         entry; the mailbox permanently removes it.</li>
 *     <li>{@link #NACKED} — the consumer has negatively acknowledged the entry
 *         with {@code requeue=false}; the mailbox removes it without
 *         redelivery.</li>
 *     <li>{@link #DEAD_LETTERED} — the entry has reached
 *         {@code maxDeliveryAttempts} and the mailbox refuses to redeliver it
 *         again. It is retained as dead-letter and no longer redelivered.</li>
 * </ul>
 *
 * <p>State transitions:
 * <pre>
 *   offer  ──→ PENDING
 *   poll   ──→ PENDING ──→ IN_FLIGHT
 *   ack    ──→ IN_FLIGHT ──→ ACKED (removed)
 *   nack(requeue=false) ──→ IN_FLIGHT ──→ NACKED (removed)
 *   nack(requeue=true,  attempts &lt; max) ──→ IN_FLIGHT ──→ PENDING (redelivered, new deliveryId)
 *   nack(requeue=true,  attempts &gt;= max) ──→ IN_FLIGHT ──→ DEAD_LETTERED (retained)
 * </pre>
 */
public enum MailboxDeliveryState {
    PENDING,
    IN_FLIGHT,
    ACKED,
    NACKED,
    DEAD_LETTERED
}
