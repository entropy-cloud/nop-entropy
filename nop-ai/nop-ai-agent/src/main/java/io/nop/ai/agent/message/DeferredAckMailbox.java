package io.nop.ai.agent.message;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory {@link IMailbox} implementing the 3-phase reservation
 * protocol (offer → poll → ack/nack) with bounded/unbounded capacity, nack
 * redelivery, and dead-lettering on {@code maxDeliveryAttempts} exhaustion.
 *
 * <p><b>Internal structure</b> (all guarded by a single {@link #lock}):
 * <ul>
 *     <li>{@link #pending} — a FIFO {@link ArrayDeque} of PENDING entries
 *         (ordered by offer/redelivery time).</li>
 *     <li>{@link #inFlight} — a {@link LinkedHashMap} of deliveryId → entry
 *         for entries that have been polled and are awaiting ack/nack.</li>
 *     <li>{@link #deadLetter} — a list of DEAD_LETTERED entries (retained for
 *         inspection; never redelivered).</li>
 * </ul>
 *
 * <p><b>Capacity</b>: {@code capacity <= 0} means unbounded (the default). A
 * bounded mailbox rejects {@code offer} with {@code false} when the total
 * number of PENDING + IN_FLIGHT entries reaches {@code capacity} (back-pressure
 * signal — never throws).
 *
 * <p><b>deliveryId</b> is a per-mailbox monotonically-increasing {@code long}
 * assigned by an {@link AtomicLong}. A redelivered message receives a
 * <strong>new</strong> deliveryId (and incremented deliveryCount), so ack/nack
 * always match the current in-flight instance precisely.
 *
 * <p><b>Dead-letter</b>: when {@code nack(id, true)} is called on an entry
 * whose {@code deliveryCount >= maxDeliveryAttempts}, the entry is marked
 * DEAD_LETTERED and moved to {@link #deadLetter} instead of being returned to
 * PENDING (preventing infinite redelivery).
 *
 * <p>See plan 216 (L4-5).
 */
public final class DeferredAckMailbox implements IMailbox {

    /** Default max delivery attempts before an entry is dead-lettered. */
    public static final int DEFAULT_MAX_DELIVERY_ATTEMPTS = 5;

    private final int capacity;
    private final int maxDeliveryAttempts;
    private final AtomicLong deliveryIdSeq = new AtomicLong(0);

    private final Deque<MailboxEntry> pending = new ArrayDeque<>();
    private final Map<Long, MailboxEntry> inFlight = new LinkedHashMap<>();
    private final List<MailboxEntry> deadLetter = new ArrayList<>();

    private final Object lock = new Object();

    /**
     * Create an unbounded mailbox with the default max delivery attempts.
     */
    public DeferredAckMailbox() {
        this(0, DEFAULT_MAX_DELIVERY_ATTEMPTS);
    }

    /**
     * Create a mailbox with explicit capacity and max delivery attempts.
     *
     * @param capacity            max number of PENDING + IN_FLIGHT entries;
     *                            {@code <= 0} means unbounded
     * @param maxDeliveryAttempts max delivery attempts before an entry is
     *                            dead-lettered on {@code nack(requeue=true)};
     *                            must be {@code >= 1}
     */
    public DeferredAckMailbox(int capacity, int maxDeliveryAttempts) {
        if (maxDeliveryAttempts < 1) {
            throw new IllegalArgumentException(
                    "DeferredAckMailbox: maxDeliveryAttempts must be >= 1, got " + maxDeliveryAttempts);
        }
        this.capacity = capacity;
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    @Override
    public boolean offer(AgentMessageEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        synchronized (lock) {
            if (capacity > 0 && (pending.size() + inFlight.size()) >= capacity) {
                return false;
            }
            long id = deliveryIdSeq.incrementAndGet();
            MailboxEntry entry = new MailboxEntry(
                    id, 1, MailboxDeliveryState.PENDING, envelope, System.currentTimeMillis(), 0L);
            pending.addLast(entry);
            return true;
        }
    }

    @Override
    public MailboxEntry poll() {
        synchronized (lock) {
            MailboxEntry entry = pending.pollFirst();
            if (entry == null) {
                return null;
            }
            MailboxEntry inFlightEntry = new MailboxEntry(
                    entry.getDeliveryId(),
                    entry.getDeliveryCount(),
                    MailboxDeliveryState.IN_FLIGHT,
                    entry.getEnvelope(),
                    entry.getOfferedAt(),
                    System.currentTimeMillis());
            inFlight.put(inFlightEntry.getDeliveryId(), inFlightEntry);
            return inFlightEntry;
        }
    }

    @Override
    public boolean ack(long deliveryId) {
        synchronized (lock) {
            MailboxEntry entry = inFlight.remove(deliveryId);
            if (entry == null) {
                return false;
            }
            // Entry is permanently removed (ACKED). No need to retain it.
            return true;
        }
    }

    @Override
    public boolean nack(long deliveryId, boolean requeue) {
        synchronized (lock) {
            MailboxEntry entry = inFlight.remove(deliveryId);
            if (entry == null) {
                return false;
            }
            if (requeue) {
                if (entry.getDeliveryCount() >= maxDeliveryAttempts) {
                    // Reached max attempts → dead-letter, do not redeliver.
                    MailboxEntry dead = new MailboxEntry(
                            entry.getDeliveryId(),
                            entry.getDeliveryCount(),
                            MailboxDeliveryState.DEAD_LETTERED,
                            entry.getEnvelope(),
                            entry.getOfferedAt(),
                            entry.getPolledAt());
                    deadLetter.add(dead);
                } else {
                    // Redeliver: assign a new deliveryId, increment deliveryCount.
                    long newId = deliveryIdSeq.incrementAndGet();
                    MailboxEntry requeued = new MailboxEntry(
                            newId,
                            entry.getDeliveryCount() + 1,
                            MailboxDeliveryState.PENDING,
                            entry.getEnvelope(),
                            entry.getOfferedAt(),
                            0L);
                    pending.addLast(requeued);
                }
            }
            // requeue=false → entry already removed from inFlight, mark NACKED (discarded).
            return true;
        }
    }

    @Override
    public int pendingCount() {
        synchronized (lock) {
            return pending.size();
        }
    }

    @Override
    public int inFlightCount() {
        synchronized (lock) {
            return inFlight.size();
        }
    }

    /**
     * @return the number of dead-lettered entries (retained, never redelivered).
     *         Test/inspection accessor.
     */
    public int deadLetterCount() {
        synchronized (lock) {
            return deadLetter.size();
        }
    }

    /**
     * @return a snapshot copy of the dead-lettered entries (for inspection).
     *         Test/inspection accessor.
     */
    public List<MailboxEntry> deadLetterEntries() {
        synchronized (lock) {
            return new ArrayList<>(deadLetter);
        }
    }

    /**
     * @return the configured capacity ({@code <= 0} means unbounded).
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * @return the configured max delivery attempts.
     */
    public int getMaxDeliveryAttempts() {
        return maxDeliveryAttempts;
    }
}
