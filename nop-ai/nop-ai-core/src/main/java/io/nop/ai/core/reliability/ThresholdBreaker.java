package io.nop.ai.core.reliability;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_BREAKER_ENTRY_TTL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_CORE_INVALID_STATE;
import static io.nop.ai.core.NopAiCoreErrors.ARG_DETAIL;
import io.nop.api.core.time.CoreMetrics;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Functional {@link ICircuitBreaker} implementing the three-state
 * consecutive-failure circuit-breaker state machine (design
 * {@code nop-ai-agent-reliability.md} §5.1 / plan 210 / L3-1).
 *
 * <p>Per model identity (the {@code provider:model} composite key built by
 * {@code ModelKeys.buildModelKey}) the breaker maintains an
 * independent state machine:
 * <ul>
 *   <li><b>CLOSED</b> (initial) — all calls allowed. Each recorded failure
 *       increments a consecutive-failure counter; when the counter reaches
 *       {@code failureThreshold} the breaker transitions to <b>OPEN</b>.</li>
 *   <li><b>OPEN</b> — all calls rejected ({@code allowCall} returns false).
 *       The transition is lazy: no background timer is started. The next
 *       {@link #allowCall(String)} after {@code cooldownMs} has elapsed since
 *       the OPEN transition transitions the breaker to <b>HALF_OPEN</b> and
 *       admits that caller as the single probe.</li>
 *   <li><b>HALF_OPEN</b> — exactly one probe call is admitted (the first
 *       caller that wins the probe slot); concurrent callers are rejected as
 *       if still OPEN. A successful probe ({@link #recordSuccess})
 *       transitions back to CLOSED and clears the failure counter; a failed
 *       probe ({@link #recordFailure}) transitions back to OPEN and restarts
 *       the cooldown clock. <b>Probe timeout escape (P2-REL)</b>: if the
 *       probe never reports back (cancelled / hung / thread killed) within
 *       {@code probeTimeoutMs} of being admitted, the next {@link #allowCall}
 *       re-takes the probe slot and admits that caller as the new probe
 *       (logged) — a stuck probe can no longer wedge the breaker in
 *       HALF_OPEN forever.</li>
 * </ul>
 *
 * <p><b>Thread safety</b>: state is tracked per model-key in a
 * {@link ConcurrentHashMap}. All state-machine transitions and entry removal
 * happen inside per-key {@code entries.compute} critical sections (the same
 * per-key serialization pattern as {@code ConcurrencyRegistry}), so
 * concurrent callers to the <i>same</i> model-key see consistent state, while
 * concurrent callers to <i>different</i> model-keys proceed in parallel.
 * {@link #getState} reads the {@code volatile} state field without locking
 * for diagnostics (a slightly-stale snapshot of just the state is acceptable;
 * the decision-making methods {@link #allowCall} / {@link #recordSuccess} /
 * {@link #recordFailure} always run inside the per-key critical section and
 * see a consistent snapshot).
 *
 * <p><b>Invariants</b>:
 * <ul>
 *   <li>A breaker that has never recorded any outcome for a model-key
 *       reports CLOSED and allows calls (the healthy default).</li>
 *   <li>The failure counter only counts <i>consecutive</i> failures: a
 *       success at any point resets it to zero. Therefore a retry cycle
 *       that ultimately succeeds (transient failures followed by success)
 *       does not accumulate debt — the breaker only trips on failures with
 *       no intervening success.</li>
 *   <li>The {@link CircuitState} public enum stays at three states. The
 *       probe-in-flight exclusivity of HALF_OPEN is enforced by an internal
 *       boolean flag per entry, not by a fourth public state.</li>
 * </ul>
 *
 * <p><b>Entry-table shrink (P2 round-4)</b>: a <i>pristine</i> entry —
 * state CLOSED, zero consecutive failures, no probe in flight — is
 * semantically identical to an absent entry (the healthy default), so it is
 * dropped from the map inside the same per-key critical section that
 * returned it to the pristine state. Entries that carry failure memory
 * (CLOSED with failures &gt; 0) or OPEN entries were previously never
 * dropped (the only unbounded-growth exemption in this class); since
 * 3.1-C7-007 (plan 2556 裁定 I) they are bounded by a lazy TTL
 * ({@code nop.ai.breaker.entry-ttl-ms}, default 1h): inside the
 * {@link #allowCall} compute critical section, a failure-memory entry whose
 * {@code lastTransition} is older than the TTL is forgotten and the key
 * restarts fresh. Entries with a probe in flight remain exempt (a probe
 * must not be invalidated under the caller). This matches the zero-count
 * shrink semantics of {@code ConcurrencyRegistry}: the table only holds
 * entries that remember something <i>recently</i>, so gateways routing to
 * arbitrary model keys do not grow the table without bound.
 *
 * <p><b>Probe generation token (3.1-C7-005, plan 2556 裁定 G)</b>: each
 * probe-slot admission bumps a per-key {@code probeGeneration}. Callers
 * capture {@link #currentGeneration(String)} together with the
 * {@code allowCall} result (for the engine coordinator: on the FINAL routed
 * model key) and pass it back via the generation-aware
 * {@link #recordSuccess(String, long)} / {@link #recordFailure(String, long, Throwable)}.
 * A receipt whose generation no longer matches (its probe escaped past a
 * later re-admission via the probe-timeout escape) is a stale receipt: it is
 * WARN-logged and discarded without flipping any state. A generation-less
 * receipt ({@code -1}) falls back to legacy semantics with a WARN-once
 * (compat fallback, explicitly adjudicated).
 *
 * <p>State is in-memory only (per breaker instance). Persistence /
 * cross-process sharing is a Non-Goal successor (design §11 deferred).
 */
public final class ThresholdBreaker implements ICircuitBreaker {

    private static final Logger LOG = LoggerFactory.getLogger(ThresholdBreaker.class);

    /** Default consecutive-failure threshold before the breaker trips. */
    public static final int DEFAULT_FAILURE_THRESHOLD = 3;
    /** Default cooldown in milliseconds before a tripped breaker probes. */
    public static final long DEFAULT_COOLDOWN_MS = 60_000L;
    /**
     * Default probe timeout in milliseconds (same order of magnitude as the
     * default cooldown): a HALF_OPEN probe that never reports back within
     * this window is treated as stuck and its slot is re-taken by the next
     * {@link #allowCall} caller. Raised from 30s to 90s by 3.1-C7-005 (plan
     * 2556 裁定 G) — the escape is now guarded by the probe generation token,
     * so a longer window no longer multiplies stale-receipt risk while still
     * tolerating long legitimate probes.
     */
    public static final long DEFAULT_PROBE_TIMEOUT_MS = 90_000L;
    /** Default lazy TTL for failure-memory entries (3.1-C7-007, plan 2556 裁定 I). */
    public static final long DEFAULT_ENTRY_TTL_MS = 3_600_000L;

    private final int failureThreshold;
    private final long cooldownMs;
    private final long probeTimeoutMs;
    private final long entryTtlMs;
    private final ConcurrentMap<String, BreakerEntry> entries = new ConcurrentHashMap<>();

    /** WARN-once flag for the legacy (generation-less) receipt fallback. */
    private final AtomicBoolean legacyReceiptWarned = new AtomicBoolean(false);

    /**
     * Construct a breaker with the default threshold (3), cooldown (60s),
     * probe timeout (90s) and entry TTL (1h, from
     * {@code nop.ai.breaker.entry-ttl-ms}).
     */
    public ThresholdBreaker() {
        this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN_MS, DEFAULT_PROBE_TIMEOUT_MS);
    }

    /**
     * @param failureThreshold consecutive failures required to trip the
     *                         breaker (must be &gt;= 1)
     * @param cooldownMs      milliseconds the breaker stays OPEN before
     *                        admitting a HALF_OPEN probe (must be &gt;= 0;
     *                        0 = probe on the very next call after tripping)
     */
    public ThresholdBreaker(int failureThreshold, long cooldownMs) {
        this(failureThreshold, cooldownMs, DEFAULT_PROBE_TIMEOUT_MS);
    }

    /**
     * @param failureThreshold consecutive failures required to trip the
     *                         breaker (must be &gt;= 1)
     * @param cooldownMs      milliseconds the breaker stays OPEN before
     *                        admitting a HALF_OPEN probe (must be &gt;= 0;
     *                        0 = probe on the very next call after tripping)
     * @param probeTimeoutMs  milliseconds a HALF_OPEN probe may stay in flight
     *                        before it is considered stuck and its slot is
     *                        re-taken by the next {@link #allowCall} caller
     *                        (must be &gt;= 0; 0 = escape disabled)
     */
    public ThresholdBreaker(int failureThreshold, long cooldownMs, long probeTimeoutMs) {
        this(failureThreshold, cooldownMs, probeTimeoutMs, CFG_AI_BREAKER_ENTRY_TTL.get());
    }

    /**
     * @param failureThreshold consecutive failures required to trip the
     *                         breaker (must be &gt;= 1)
     * @param cooldownMs      milliseconds the breaker stays OPEN before
     *                        admitting a HALF_OPEN probe (must be &gt;= 0;
     *                        0 = probe on the very next call after tripping)
     * @param probeTimeoutMs  milliseconds a HALF_OPEN probe may stay in flight
     *                        before it is considered stuck (must be &gt;= 0;
     *                        0 = escape disabled)
     * @param entryTtlMs      lazy TTL for failure-memory entries (OPEN /
     *                        CLOSED-with-failures), swept inside the allowCall
     *                        critical section (3.1-C7-007, plan 2556 裁定 I)
     *                        (must be &gt;= 0; 0 = TTL disabled)
     */
    public ThresholdBreaker(int failureThreshold, long cooldownMs, long probeTimeoutMs, long entryTtlMs) {
        if (failureThreshold < 1) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiCoreErrors.ARG_MSG,
                    "ThresholdBreaker failureThreshold must be >= 1: " + failureThreshold);
        }
        if (cooldownMs < 0) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiCoreErrors.ARG_MSG,
                    "ThresholdBreaker cooldownMs must be >= 0: " + cooldownMs);
        }
        if (probeTimeoutMs < 0) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiCoreErrors.ARG_MSG,
                    "ThresholdBreaker probeTimeoutMs must be >= 0: " + probeTimeoutMs);
        }
        if (entryTtlMs < 0) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiCoreErrors.ARG_MSG,
                    "ThresholdBreaker entryTtlMs must be >= 0: " + entryTtlMs);
        }
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
        this.probeTimeoutMs = probeTimeoutMs;
        this.entryTtlMs = entryTtlMs;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public long getProbeTimeoutMs() {
        return probeTimeoutMs;
    }

    public long getEntryTtlMs() {
        return entryTtlMs;
    }

    @Override
    public boolean allowCall(String modelKey) {
        if (modelKey == null) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiCoreErrors.ARG_MSG, "modelKey must not be null");
        }
        boolean[] holder = new boolean[1];
        entries.compute(modelKey, (k, existing) -> {
            long now = CoreMetrics.currentTimeMillis();
            // 3.1-C7-007 (plan 2556 裁定 I): lazy TTL sweep inside the same
            // per-key critical section — a failure-memory entry (OPEN, or
            // CLOSED with failures) that has not transitioned within
            // entryTtlMs is forgotten (table shrinks; the key restarts fresh).
            // HALF_OPEN entries (probe in flight) and pristine entries are
            // exempt (the latter already shrink immediately below).
            if (existing != null && entryTtlMs > 0
                    && (existing.state == CircuitState.OPEN
                    || (existing.state == CircuitState.CLOSED && existing.consecutiveFailures > 0))
                    && now - existing.lastTransition >= entryTtlMs) {
                LOG.info("ThresholdBreaker failure-memory entry for model {} expired after {} ms without "
                        + "transition; forgetting (lazy TTL sweep).", modelKey, entryTtlMs);
                // The key restarts fresh (healthy default = CLOSED) → the call is allowed.
                holder[0] = true;
                return null;
            }
            BreakerEntry entry = existing != null ? existing : new BreakerEntry();
            switch (entry.state) {
                case CLOSED:
                    holder[0] = true;
                    // Shrink: a pristine CLOSED entry is semantically identical to
                    // absence (healthy default) → drop it so arbitrary model-key
                    // routing does not grow the table without bound.
                    return removeIfPristine(entry) ? null : entry;
                case OPEN:
                    // Lazy cooldown check: no background timer. If the cooldown
                    // has elapsed, transition to HALF_OPEN and admit this caller
                    // as the single probe.
                    if (now - entry.openedAt >= cooldownMs) {
                        entry.state = CircuitState.HALF_OPEN;
                        entry.probeInFlight = true;
                        entry.probeStartedAt = now;
                        // 3.1-C7-005 (plan 2556 裁定 G): probe entry bumps the
                        // generation; the admitted caller captures it via
                        // currentGeneration and hands it back on record*.
                        entry.probeGeneration++;
                        entry.lastTransition = now;
                        holder[0] = true;
                    }
                    return entry;
                case HALF_OPEN:
                    // A probe is in flight: only the probe caller is admitted;
                    // concurrent callers are rejected as if still OPEN.
                    if (!entry.probeInFlight) {
                        entry.probeInFlight = true;
                        entry.probeStartedAt = now;
                        entry.probeGeneration++;
                        holder[0] = true;
                    } else {
                        // Probe-timeout escape (P2-REL): a probe that never reports
                        // back (cancelled / hung / thread killed) would otherwise
                        // wedge the breaker in HALF_OPEN forever, rejecting every
                        // subsequent call. Once probeTimeoutMs has elapsed, treat
                        // the probe as stuck and re-take its slot for this caller.
                        if (probeTimeoutMs > 0 && now - entry.probeStartedAt >= probeTimeoutMs) {
                            LOG.warn("ThresholdBreaker HALF_OPEN probe for model {} did not report within {} ms; "
                                            + "re-taking the probe slot (escape from a stuck probe). state stays HALF_OPEN.",
                                    modelKey, probeTimeoutMs);
                            entry.probeStartedAt = now;
                            // Escape re-admission invalidates the escaped probe's
                            // generation: its late receipt will be discarded.
                            entry.probeGeneration++;
                            holder[0] = true;
                        }
                    }
                    return entry;
                default:
                    throw new NopAiCoreException(ERR_AI_CORE_INVALID_STATE).param(ARG_DETAIL, "Unknown circuit state: " + entry.state);
            }
        });
        return holder[0];
    }

    /**
     * Read-only cooldown query (plan 2509 R2-06 adjudication 7(b) C7-005): reports whether a
     * call to this model would be admissible <i>without</i> consuming the HALF_OPEN probe slot —
     * {@code true} for any non-OPEN state, or for OPEN whose cooldown has already elapsed.
     *
     * <p>Unlike {@link #allowCall(String)} this never mutates the breaker (no OPEN → HALF_OPEN
     * transition, no probe-slot take), so selection views can consult cooldown progress without
     * burning phantom probe slots. Declared deviation from the plan's "zero breaker change"
     * constraint: read-only addition to an internal implementation class (not part of the
     * {@code ICircuitBreaker} platform SPI), zero write-face change.
     *
     * @param modelKey non-null composite model identity ({@code provider:model})
     * @return {@code true} when the cooldown gate would admit a call right now
     */
    public boolean cooldownElapsed(String modelKey) {
        if (modelKey == null) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiCoreErrors.ARG_MSG, "modelKey must not be null");
        }
        BreakerEntry entry = entries.get(modelKey);
        if (entry == null || entry.state != CircuitState.OPEN) {
            return true;
        }
        return CoreMetrics.currentTimeMillis() - entry.openedAt >= cooldownMs;
    }

    @Override
    public CircuitState getState(String modelKey) {
        if (modelKey == null) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiCoreErrors.ARG_MSG, "modelKey must not be null");
        }
        BreakerEntry entry = entries.get(modelKey);
        // An untracked model-key has never recorded any outcome → CLOSED.
        return entry != null ? entry.state : CircuitState.CLOSED;
    }

    @Override
    public void recordSuccess(String modelKey) {
        checkModelKey(modelKey);
        entries.compute(modelKey, (k, existing) -> existing == null ? null : applySuccess(existing, modelKey));
    }

    @Override
    public void recordFailure(String modelKey) {
        checkModelKey(modelKey);
        entries.compute(modelKey, (k, existing) ->
                applyFailure(existing != null ? existing : new BreakerEntry(), modelKey));
    }

    // ========================================================================
    // Probe generation token (3.1-C7-005, plan 2556 裁定 G)
    // ========================================================================

    @Override
    public long currentGeneration(String modelKey) {
        checkModelKey(modelKey);
        BreakerEntry entry = entries.get(modelKey);
        return entry != null ? entry.probeGeneration : 0L;
    }

    @Override
    public void recordSuccess(String modelKey, long gen) {
        checkModelKey(modelKey);
        if (gen == GENERATION_UNKNOWN) {
            warnLegacyReceiptOnce();
            recordSuccess(modelKey);
            return;
        }
        entries.compute(modelKey, (k, existing) -> {
            // 3.1-C7-005 coordination fix (plan 2556 group 2, nop-ai-agent
            // verification blocker): an ABSENT entry IS generation 0 —
            // currentGeneration(key) returns 0 for an unknown key, so a gen=0
            // receipt for a key with no entry yet is a MATCHING receipt (the
            // first outcome for a fresh model), not a stale escape. A success
            // on an untracked key is a no-op (mirrors legacy recordSuccess).
            if (existing == null) {
                return gen == 0L ? null : discardStaleSuccess(modelKey, gen, null);
            }
            if (existing.probeGeneration != gen) {
                return discardStaleSuccess(modelKey, gen, existing);
            }
            return applySuccess(existing, modelKey);
        });
    }

    @Override
    public void recordFailure(String modelKey, long gen, Throwable err) {
        checkModelKey(modelKey);
        if (gen == GENERATION_UNKNOWN) {
            warnLegacyReceiptOnce();
            recordFailure(modelKey);
            return;
        }
        entries.compute(modelKey, (k, existing) -> {
            // 3.1-C7-005 coordination fix: absent entry == generation 0 (see
            // recordSuccess(String, long)) — the first failure receipt for a
            // fresh key must count, or the breaker can never leave CLOSED.
            if (existing == null && gen == 0L) {
                return applyFailure(new BreakerEntry(), modelKey);
            }
            if (existing == null || existing.probeGeneration != gen) {
                LOG.warn("ThresholdBreaker stale failure receipt for model {} discarded: receiptGen={}, "
                                + "currentGen={} (state untouched).",
                        modelKey, gen, existing != null ? existing.probeGeneration : 0L);
                return existing;
            }
            return applyFailure(existing, modelKey);
        });
    }

    /**
     * 3.1-C7-005 coordination fix: shared stale-success discard (WARN + keep
     * current entry untouched).
     */
    private BreakerEntry discardStaleSuccess(String modelKey, long gen, BreakerEntry current) {
        LOG.warn("ThresholdBreaker stale success receipt for model {} discarded: receiptGen={}, "
                        + "currentGen={} (state untouched).",
                modelKey, gen, current != null ? current.probeGeneration : 0L);
        return current;
    }

    private void warnLegacyReceiptOnce() {
        if (legacyReceiptWarned.compareAndSet(false, true)) {
            LOG.warn("ThresholdBreaker received a generation-less receipt (GENERATION_UNKNOWN); "
                    + "falling back to legacy semantics. Callers should capture currentGeneration at allowCall "
                    + "(3.1-C7-005, plan 2556 裁定 G).");
        }
    }

    private static void checkModelKey(String modelKey) {
        if (modelKey == null) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiCoreErrors.ARG_MSG, "modelKey must not be null");
        }
    }

    /**
     * Shared success-transition body (legacy + generation-aware paths). Must
     * run inside the per-key {@code entries.compute} critical section.
     */
    private BreakerEntry applySuccess(BreakerEntry entry, String modelKey) {
        switch (entry.state) {
            case CLOSED:
                entry.consecutiveFailures = 0;
                // Shrink: counter reset to the healthy default → pristine entry
                // is semantically identical to absence → drop it.
                return removeIfPristine(entry) ? null : entry;
            case HALF_OPEN:
                // Probe succeeded → reset to CLOSED, clear the counter,
                // release the probe slot. The entry is now pristine → drop it
                // (absence = CLOSED healthy default).
                entry.state = CircuitState.CLOSED;
                entry.consecutiveFailures = 0;
                entry.probeInFlight = false;
                return null;
            case OPEN:
                // Defensive: a success was reported while OPEN (the call
                // should have been rejected). Reset the counter; leave the
                // state as OPEN — the operator-configured cooldown still
                // gates the next probe.
                entry.consecutiveFailures = 0;
                return entry;
            default:
                throw new NopAiCoreException(ERR_AI_CORE_INVALID_STATE).param(ARG_DETAIL, "Unknown circuit state: " + entry.state);
        }
    }

    /**
     * Shared failure-transition body (legacy + generation-aware paths). Must
     * run inside the per-key {@code entries.compute} critical section.
     */
    private BreakerEntry applyFailure(BreakerEntry entry, String modelKey) {
        long now = CoreMetrics.currentTimeMillis();
        switch (entry.state) {
            case CLOSED:
                entry.consecutiveFailures++;
                entry.lastTransition = now;
                if (entry.consecutiveFailures >= failureThreshold) {
                    entry.state = CircuitState.OPEN;
                    entry.openedAt = now;
                }
                // Failure memory must survive → entry stays (never dropped
                // while it remembers failures below the threshold). It is
                // now bounded by the lazy entry TTL (裁定 I) instead of the
                // previous unbounded exemption.
                return entry;
            case HALF_OPEN:
                // Probe failed → back to OPEN, restart the cooldown clock,
                // release the probe slot.
                entry.state = CircuitState.OPEN;
                entry.openedAt = now;
                entry.probeInFlight = false;
                entry.lastTransition = now;
                return entry;
            case OPEN:
                // Defensive: a failure was reported while OPEN (the call
                // should have been rejected). Leave the state as OPEN;
                // do not extend the cooldown (the failure is spurious).
                return entry;
            default:
                throw new NopAiCoreException(ERR_AI_CORE_INVALID_STATE).param(ARG_DETAIL, "Unknown circuit state: " + entry.state);
        }
    }

    /**
     * A <i>pristine</i> entry — CLOSED, zero consecutive failures, no probe in
     * flight — is semantically identical to an absent entry (the healthy
     * default), so it may be dropped from the table (entry-table shrink,
     * matching the {@code ConcurrencyRegistry} zero-count shrink semantics).
     */
    private static boolean removeIfPristine(BreakerEntry entry) {
        return entry.state == CircuitState.CLOSED && entry.consecutiveFailures == 0 && !entry.probeInFlight;
    }

    /**
     * Per-model-key mutable state holder. All fields are mutated under the
     * entry's monitor (synchronized on the entry instance). The
     * {@code volatile state} field is also read lock-free by
     * {@link #getState} for diagnostics.
     */
    private static final class BreakerEntry {
        volatile CircuitState state = CircuitState.CLOSED;
        int consecutiveFailures = 0;
        long openedAt = 0L;
        boolean probeInFlight = false;
        // Timestamp (CoreMetrics.currentTimeMillis) of the last probe-slot
        // (re)taking; the HALF_OPEN probe-timeout escape compares against it.
        long probeStartedAt = 0L;
        // 3.1-C7-005 (plan 2556 裁定 G): bumped on every probe-slot admission
        // (HALF_OPEN transition, slot re-take, escape re-take). Receipts
        // carrying an older generation are stale escapes → discarded.
        long probeGeneration = 0L;
        // 3.1-C7-007 (plan 2556 裁定 I): timestamp of the last state-relevant
        // transition; drives the lazy failure-memory TTL sweep in allowCall.
        long lastTransition = 0L;
    }
}
