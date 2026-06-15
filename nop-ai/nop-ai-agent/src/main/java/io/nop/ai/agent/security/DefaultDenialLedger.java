package io.nop.ai.agent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shipped default {@link IDenialLedger} implementing in-memory per-session
 * denial counting with threshold-based pause (design §6.2 / design doc §4.9
 * decision 3). This is the engine default, replacing the former
 * {@link NoOpDenialLedger} default (plan 200).
 *
 * <p><b>Counting scheme</b>: a {@link ConcurrentHashMap} keyed by session id,
 * with each session's count in an {@link AtomicInteger}. After
 * {@link #DEFAULT_DENIAL_THRESHOLD} per-session denials, the session is
 * considered paused ({@link #isPaused} returns {@code true}).
 *
 * <p><b>Anonymous sessions</b>: a null {@code sessionId} is not counted
 * (anonymous denials cannot accumulate a pause state). This matches the
 * {@link DBDenialLedger} semantics and is documented behavior, not a silent
 * skip.
 *
 * <p><b>Persistence</b>: in-memory only. The count does not survive
 * ledger-instance reconstruction. Integrators who need cross-process /
 * crash-recovery persistence should register {@link DBDenialLedger} via
 * {@code DefaultAgentEngine.setDenialLedger(new DBDenialLedger(dataSource))}.
 *
 * <p>{@link NoOpDenialLedger} is retained as a public opt-in for integrators
 * who need the "no counting, no pausing" behavior.
 */
public final class DefaultDenialLedger implements IDenialLedger {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDenialLedger.class);

    /**
     * The default denial threshold (design §6.2 {@code denialThreshold = 3}):
     * after this many per-session denials the session is paused.
     */
    public static final int DEFAULT_DENIAL_THRESHOLD = 3;

    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final int denialThreshold;

    public DefaultDenialLedger() {
        this(DEFAULT_DENIAL_THRESHOLD);
    }

    public DefaultDenialLedger(int denialThreshold) {
        if (denialThreshold <= 0) {
            throw new IllegalArgumentException("denialThreshold must be positive, got: " + denialThreshold);
        }
        this.denialThreshold = denialThreshold;
    }

    public int getDenialThreshold() {
        return denialThreshold;
    }

    @Override
    public DenialRecordOutcome recordDenial(DenialRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("DenialRecord must not be null");
        }
        String sessionId = record.getSessionId();
        if (sessionId == null) {
            return DenialRecordOutcome.of(0, false);
        }
        AtomicInteger counter = counts.computeIfAbsent(sessionId, k -> new AtomicInteger(0));
        int newCount = counter.incrementAndGet();
        boolean exceeded = newCount >= denialThreshold;
        if (exceeded) {
            LOG.warn("Session {} reached denial threshold (count={}, threshold={})",
                    sessionId, newCount, denialThreshold);
        }
        return DenialRecordOutcome.of(newCount, exceeded);
    }

    @Override
    public boolean isPaused(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        AtomicInteger counter = counts.get(sessionId);
        return counter != null && counter.get() >= denialThreshold;
    }

    @Override
    public int getDenialCount(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        AtomicInteger counter = counts.get(sessionId);
        return counter != null ? counter.get() : 0;
    }

    @Override
    public void reset(String sessionId) {
        if (sessionId == null) {
            return;
        }
        counts.remove(sessionId);
    }
}
