package io.nop.cluster.elector;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polling leader elector base. The election loop is a one-shot self-continuing schedule
 * chain (30-pt insulation table #14: the platform periodic insulation layer only rewrites
 * the fixedRate/fixedDelay facilities and has zero automatic coverage over one-shot
 * self-rescheduling chains — the cross-module insulation disposition #14), so the
 * loop-supervision obligations are implemented here at the site:
 *
 * <ul>
 *   <li><b>C4-004 (plan 2496 Phase 4)</b>: the pending round future is retained and
 *       centrally observed — a round future that completes exceptionally (execution-leg
 *       rejection on the shared worker pool is the audited kill path) triggers a
 *       business-identified alarm (CL-014) and a backoff re-arm, instead of the silent
 *       permanent death the audit reconstructed;</li>
 *   <li><b>CL-011</b>: the base class guards the round body with a Throwable-wide barrier
 *       and re-arms from a {@code finally} — an Error escaping a subclass
 *       {@code checkElection()} (or a subclass tail re-schedule that never runs) can no
 *       longer kill the loop silently. Subclass tails that re-arm via
 *       {@link #scheduleCheck()} keep working unchanged (the per-round re-arm flag
 *       prevents double-scheduling two parallel chains);</li>
 *   <li><b>CL-014</b>: every broken-link signal is reported with the business identifier
 *       (elector class + clusterId) instead of only the executor's generic
 *       {@code nop.err.thread-pool.execute-fail};</li>
 *   <li><b>rejection semantics</b>: a synchronous rejection of the schedule call itself
 *       (timer leg dead / executor destroyed) is terminal per the platform insulation
 *       design §3.1 (no resurrection against a dead facility) but is reported with the
 *       business identifier; the C4-002/C1-004 stop/restart lifecycle face is owned by
 *       R2-18 and intentionally not touched here (no cancel-on-stop is added).</li>
 * </ul>
 */
public abstract class AbstractPollingLeaderElector extends AbstractLeaderElector {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractPollingLeaderElector.class);

    /** CL-011 re-arm backoff: lower bound for the delay after a broken-link re-arm. */
    private static final long REARM_BACKOFF_BASE_MS = 1000L;
    /** CL-011 re-arm backoff cap — a failing loop must not spin on the shared timer. */
    private static final long REARM_BACKOFF_MAX_MS = 60_000L;

    protected IScheduledExecutor scheduledExecutor;

    /** C4-004: the pending round future, retained for centralized observation. */
    private volatile CompletableFuture<?> pendingCheck;

    /** CL-011 backoff for the next broken-link re-arm (reset after a healthy round). */
    private volatile long rearmBackoffMs = REARM_BACKOFF_BASE_MS;

    /**
     * Per-round re-arm flag. A fresh flag object is installed when a guarded round starts;
     * {@link #scheduleCheck(long)} marks the flag it finds installed <em>at schedule
     * time</em>. Because rounds are serial (a next round is only ever scheduled by the
     * previous round's tail/re-arm), the flag a tail marks is always its own round's flag —
     * the guarded round's {@code finally} re-arms only when no successor was scheduled.
     */
    private volatile AtomicBoolean roundRearmed;

    public void setScheduledExecutor(IScheduledExecutor scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
    }

    @Override
    protected void doStart() {
        if (scheduledExecutor == null) {
            scheduledExecutor = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
        }

        scheduleCheck(0);
    }

    /**
     * Schedules the next election round. Subclass tails keep calling this unchanged; the
     * scheduled body is the guarded round wrapper, never the raw {@code checkElection()}.
     */
    protected void scheduleCheck() {
        scheduleCheck(getCheckIntervalMs());
    }

    private void scheduleCheck(long delayMs) {
        AtomicBoolean roundFlag = this.roundRearmed;
        IScheduledExecutor executor = this.scheduledExecutor;
        if (executor == null || executor.isDestroyed()) {
            // Synchronous rejection on the timer leg: the facility is gone — terminal per
            // design §3.1 (no resurrection against a dead facility), but reported with the
            // business identifier instead of vanishing.
            reportLoopBroken("schedule-rejected", "executor-unavailable", null);
            return;
        }
        try {
            CompletableFuture<?> future = executor.schedule(() -> {
                runGuardedElectionRound();
                return null;
            }, delayMs, TimeUnit.MILLISECONDS);
            this.pendingCheck = future;
            if (roundFlag != null) {
                roundFlag.set(true);
            }
            // C4-004 centralized observation: the audited rejection kill path is the
            // execution leg — the executor completes the round future exceptionally with
            // zero re-scheduling. The alarm + one backoff re-arm here is the module-level
            // supervision the platform insulation design assigns to the caller side.
            future.whenComplete((r, e) -> {
                if (e != null && !(e instanceof CancellationException)) {
                    reportLoopBroken("round-future-failed", "execution-leg-failed", e);
                    if (!isStopping()) {
                        rearmAfterBreakdown();
                    }
                }
            });
        } catch (Throwable e) {
            reportLoopBroken("schedule-rejected", "schedule-call-failed", e);
        }
    }

    /**
     * Guarded round body: the site-side Throwable barrier (30-pt #14 explicit admission —
     * the platform insulation layer has zero automatic coverage over this one-shot
     * self-continuing chain). The {@code finally} re-arms only when no successor round was
     * scheduled (Error escaped the subclass body, or the subclass tail re-arm failed); a
     * healthy round resets the re-arm backoff.
     */
    private void runGuardedElectionRound() {
        AtomicBoolean roundFlag = new AtomicBoolean(false);
        this.roundRearmed = roundFlag;
        try {
            checkElection();
        } catch (Throwable e) {
            // CL-014: the escaping Throwable is reported with the business identifier —
            // pre-fix this path killed the chain with zero business-identified trace.
            reportLoopBroken("round-body-failed", "check-election-threw", e);
        } finally {
            if (!roundFlag.get() && !isStopping()) {
                rearmAfterBreakdown();
            } else {
                rearmBackoffMs = REARM_BACKOFF_BASE_MS;
            }
        }
    }

    /** CL-011: base-class backoff re-arm after a broken link (1s doubling to 60s cap). */
    private void rearmAfterBreakdown() {
        long delay = rearmBackoffMs;
        rearmBackoffMs = Math.min(rearmBackoffMs * 2, REARM_BACKOFF_MAX_MS);
        scheduleCheck(delay);
    }

    /**
     * CL-014: the broken-link report point carrying the business identifier (elector
     * class + clusterId). One log key so operations can tell which business loop died.
     */
    private void reportLoopBroken(String signal, String reason, Throwable e) {
        if (e == null) {
            LOG.error("nop.err.cluster.leader-elector.loop-broken:signal={},reason={},elector={},clusterId={}",
                    signal, reason, getClass().getSimpleName(), getClusterId());
        } else {
            LOG.error("nop.err.cluster.leader-elector.loop-broken:signal={},reason={},elector={},clusterId={}",
                    signal, reason, getClass().getSimpleName(), getClusterId(), e);
        }
    }

    /**
     * The pending round future — the C4-004 retention point (the future is no longer
     * discarded at the schedule site) and the observation seam for supervision tests.
     */
    public CompletableFuture<?> getPendingCheckForObservation() {
        return pendingCheck;
    }

    protected abstract Void checkElection();
}
