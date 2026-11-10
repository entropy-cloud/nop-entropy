package io.nop.ai.agent.message;

import io.nop.api.core.time.CoreMetrics;
import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.core.NopAiCoreErrors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(DeferredAckMailbox.class);

    /** Default max delivery attempts before an entry is dead-lettered. */
    public static final int DEFAULT_MAX_DELIVERY_ATTEMPTS = 5;

    /**
     * 3.1-C5-017（plan 2556 裁定 E）: default dead-letter capacity bound.
     * A full dead-letter list evicts its OLDEST entry (FIFO) with a WARN —
     * the dead-letter list is a diagnostic retention buffer, not an
     * unbounded ledger.
     */
    public static final int DEFAULT_DEAD_LETTER_CAPACITY = 1000;

    /**
     * 3.1-C5-017（plan 2556 裁定 E）: default staleness threshold for in-flight
     * entries. Aligned with {@code DBMessageService.DEFAULT_STALE_CLAIM_TIMEOUT_MS}
     * (5 min): a consumer that polled but never acked/nacked within this
     * window is presumed crashed, and its entry is lazily replayed.
     */
    public static final long DEFAULT_STALE_INFLIGHT_TIMEOUT_MS = 5 * 60 * 1000L;

    private final int capacity;
    private final int maxDeliveryAttempts;
    private final int deadLetterCapacity;
    private volatile long staleInFlightTimeoutMs = DEFAULT_STALE_INFLIGHT_TIMEOUT_MS;
    private final AtomicLong deliveryIdSeq = new AtomicLong(0);

    private final Deque<MailboxEntry> pending = new ArrayDeque<>();
    private final Map<Long, MailboxEntry> inFlight = new LinkedHashMap<>();
    private final List<MailboxEntry> deadLetter = new ArrayList<>();
    /**
     * Plan 2288 (WI7): correlationId → deliveryId dedup index. Stable keys
     * (non-blank correlationId) dedupe repeated offers — "duplicate offer ==
     * single entry". Cleaned on ack, dead-letter AND nack(requeue=false);
     * retained across nack(requeue=true) redelivery. Guarded by {@link #lock}.
     */
    private final Map<String, Long> keysByCorrelationId = new HashMap<>();

    private final Object lock = new Object();

    /**
     * Create an unbounded mailbox with the default max delivery attempts and
     * the default bounded dead-letter capacity
     * ({@link #DEFAULT_DEAD_LETTER_CAPACITY}).
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
        this(capacity, maxDeliveryAttempts, DEFAULT_DEAD_LETTER_CAPACITY);
    }

    /**
     * 3.1-C5-017（plan 2556 裁定 E）: full-control constructor.
     *
     * @param deadLetterCapacity max retained DEAD_LETTERED entries;
     *                           {@code <= 0} means unbounded. When full, the
     *                           OLDEST dead-lettered entry is evicted (FIFO)
     *                           with a WARN.
     */
    public DeferredAckMailbox(int capacity, int maxDeliveryAttempts, int deadLetterCapacity) {
        if (maxDeliveryAttempts < 1) {
            throw new NopAiAgentException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiCoreErrors.ARG_MSG,
                    "DeferredAckMailbox: maxDeliveryAttempts must be >= 1, got " + maxDeliveryAttempts);
        }
        this.capacity = capacity;
        this.maxDeliveryAttempts = maxDeliveryAttempts;
        this.deadLetterCapacity = deadLetterCapacity;
    }

    @Override
    public boolean offer(AgentMessageEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        synchronized (lock) {
            // 3.1-C5-017（plan 2556 裁定 E）: lazy stale-inFlight sweep driven
            // by the critical sections (no scheduler thread).
            sweepStaleInFlight(CoreMetrics.currentTimeMillis());
            // Plan 2288 (WI7): dedup check BEFORE the capacity check — a
            // duplicate offer is a no-op returning true even when full
            String key = envelope.getCorrelationId();
            if (key != null && !key.isEmpty() && keysByCorrelationId.containsKey(key)) {
                return true; // duplicate offer == single entry
            }
            if (capacity > 0 && (pending.size() + inFlight.size()) >= capacity) {
                return false;
            }
            long id = deliveryIdSeq.incrementAndGet();
            MailboxEntry entry = new MailboxEntry(
                    id, 1, MailboxDeliveryState.PENDING, envelope, CoreMetrics.currentTimeMillis(), 0L);
            pending.addLast(entry);
            if (key != null && !key.isEmpty()) {
                keysByCorrelationId.put(key, id);
            }
            return true;
        }
    }

    @Override
    public MailboxEntry poll() {
        synchronized (lock) {
            // 3.1-C5-017（plan 2556 裁定 E）: lazy stale-inFlight sweep.
            sweepStaleInFlight(CoreMetrics.currentTimeMillis());
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
                    CoreMetrics.currentTimeMillis());
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
            // Plan 2288 (WI7): release the dedup key — the same correlationId
            // may legitimately be offered again later
            String key = entry.getEnvelope().getCorrelationId();
            if (key != null && !key.isEmpty()) {
                keysByCorrelationId.remove(key);
            }
            return true;
        }
    }

    @Override
    public boolean nack(long deliveryId, boolean requeue) {
        synchronized (lock) {
            // 3.1-C5-017（plan 2556 裁定 E）: lazy stale-inFlight sweep.
            sweepStaleInFlight(CoreMetrics.currentTimeMillis());
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
                    // 3.1-C5-017（plan 2556 裁定 E）: bounded retention — a full
                    // dead-letter list evicts its OLDEST entry (FIFO) with WARN.
                    addDeadLetter(dead);
                    // Plan 2288 (WI7): dead-letter releases the dedup key
                    String key = entry.getEnvelope().getCorrelationId();
                    if (key != null && !key.isEmpty()) {
                        keysByCorrelationId.remove(key);
                    }
                } else {
                    // Redeliver: assign a new deliveryId, increment deliveryCount.
                    // Plan 2288 (WI7): dedup index RETAINED across requeue —
                    // the same envelope is still in flight logically
                    long newId = deliveryIdSeq.incrementAndGet();
                    MailboxEntry requeued = new MailboxEntry(
                            newId,
                            entry.getDeliveryCount() + 1,
                            MailboxDeliveryState.PENDING,
                            entry.getEnvelope(),
                            entry.getOfferedAt(),
                            0L);
                    pending.addLast(requeued);
                    String key = entry.getEnvelope().getCorrelationId();
                    if (key != null && !key.isEmpty()) {
                        keysByCorrelationId.put(key, newId);
                    }
                }
            } else {
                // Plan 2288 (WI7): nack(requeue=false) discards the entry —
                // release the dedup key so a later legitimate offer of the
                // same correlationId is not suppressed forever
                String key = entry.getEnvelope().getCorrelationId();
                if (key != null && !key.isEmpty()) {
                    keysByCorrelationId.remove(key);
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

    /**
     * 3.1-C5-017（plan 2556 裁定 E）: append a dead-lettered entry, enforcing
     * the bounded retention. When the list is at {@link #deadLetterCapacity}
     * (if {@code > 0}), the OLDEST dead-lettered entry is evicted (FIFO) with
     * a WARN before the new entry is appended. MUST hold {@link #lock}.
     */
    private void addDeadLetter(MailboxEntry dead) {
        if (deadLetterCapacity > 0 && deadLetter.size() >= deadLetterCapacity) {
            MailboxEntry evicted = deadLetter.remove(0);
            LOG.warn("DeferredAckMailbox: dead-letter capacity {} reached — evicted OLDEST "
                            + "dead-lettered entry (deliveryId={}, offeredAt={}) to retain the newest",
                    deadLetterCapacity, evicted.getDeliveryId(), evicted.getOfferedAt());
        }
        deadLetter.add(dead);
    }

    /**
     * 3.1-C5-017（plan 2556 裁定 E）: lazy stale-inFlight sweep. Called inside
     * the {@link #lock} critical sections of {@code offer}/{@code poll}/{@code nack}
     * (no dedicated scheduler thread). Entries polled more than
     * {@link #staleInFlightTimeoutMs} ago (aligned with
     * {@code DBMessageService} 5min stale-claim semantics) are presumed to
     * have lost their consumer and are replayed with the same semantics as
     * {@code nack(requeue=true)}: deliveryCount below
     * {@link #maxDeliveryAttempts} → requeue with a fresh deliveryId;
     * otherwise dead-letter (bounded retention via {@link #addDeadLetter}).
     *
     * @return the number of stale entries replayed/dead-lettered
     */
    private int sweepStaleInFlight(long now) {
        if (staleInFlightTimeoutMs <= 0 || inFlight.isEmpty()) {
            return 0;
        }
        List<MailboxEntry> stale = null;
        for (MailboxEntry e : inFlight.values()) {
            if (e.getPolledAt() > 0 && now - e.getPolledAt() >= staleInFlightTimeoutMs) {
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(e);
            }
        }
        if (stale == null) {
            return 0;
        }
        for (MailboxEntry e : stale) {
            inFlight.remove(e.getDeliveryId());
            String key = e.getEnvelope().getCorrelationId();
            if (e.getDeliveryCount() >= maxDeliveryAttempts) {
                MailboxEntry dead = new MailboxEntry(
                        e.getDeliveryId(),
                        e.getDeliveryCount(),
                        MailboxDeliveryState.DEAD_LETTERED,
                        e.getEnvelope(),
                        e.getOfferedAt(),
                        e.getPolledAt());
                addDeadLetter(dead);
                if (key != null && !key.isEmpty()) {
                    keysByCorrelationId.remove(key);
                }
                LOG.warn("DeferredAckMailbox: stale in-flight entry (deliveryId={}, polledAt={}, "
                        + "deliveryCount={}) exceeded stale timeout {}ms after max delivery attempts "
                        + "— dead-lettered", e.getDeliveryId(), e.getPolledAt(), e.getDeliveryCount(),
                        staleInFlightTimeoutMs);
            } else {
                long newId = deliveryIdSeq.incrementAndGet();
                pending.addLast(new MailboxEntry(
                        newId,
                        e.getDeliveryCount() + 1,
                        MailboxDeliveryState.PENDING,
                        e.getEnvelope(),
                        e.getOfferedAt(),
                        0L));
                if (key != null && !key.isEmpty()) {
                    keysByCorrelationId.put(key, newId);
                }
                LOG.warn("DeferredAckMailbox: stale in-flight entry (deliveryId={}, polledAt={}) "
                                + "exceeded stale timeout {}ms — replayed as deliveryId={} "
                                + "(deliveryCount={})",
                        e.getDeliveryId(), e.getPolledAt(), staleInFlightTimeoutMs,
                        newId, e.getDeliveryCount() + 1);
            }
        }
        return stale.size();
    }

    /**
     * 3.1-C5-017（plan 2556 裁定 E）: test/inspection seam — run the lazy
     * stale-inFlight sweep with a simulated clock instead of waiting for the
     * next offer/poll/nack on real time.
     *
     * @param nowMs simulated wall-clock millis
     * @return the number of stale entries replayed/dead-lettered
     */
    public int sweepStaleInFlightForTest(long nowMs) {
        synchronized (lock) {
            return sweepStaleInFlight(nowMs);
        }
    }

    /**
     * 3.1-C5-017（plan 2556 裁定 E）: configure the stale-inFlight threshold.
     * {@code <= 0} disables the sweep. Default is
     * {@link #DEFAULT_STALE_INFLIGHT_TIMEOUT_MS} (5 min, aligned with
     * {@code DBMessageService}).
     */
    public void setStaleInFlightTimeoutMs(long timeoutMs) {
        this.staleInFlightTimeoutMs = timeoutMs;
    }

    /**
     * 3.1-C5-017（plan 2556 裁定 E）: bounded-default mailbox factory for the
     * explicit-enable wiring path. Production default remains
     * {@code mailboxFactory = null} (no mailboxes constructed — default-off
     * preserved); integrators that enable session mailboxes should wire THIS
     * factory (or an equivalent bounded one) so every constructed mailbox is
     * bounded (dead-letter retention capped at
     * {@link #DEFAULT_DEAD_LETTER_CAPACITY} by the default constructor).
     * Owner-doc envelope: session mailboxes are opt-in (plan 216).
     */
    public static java.util.function.Function<String, IMailbox> boundedDefaultFactory() {
        return sid -> new DeferredAckMailbox();
    }

    /**
     * @return the configured dead-letter retention bound ({@code <= 0} means
     *         unbounded).
     */
    public int getDeadLetterCapacity() {
        return deadLetterCapacity;
    }
}
