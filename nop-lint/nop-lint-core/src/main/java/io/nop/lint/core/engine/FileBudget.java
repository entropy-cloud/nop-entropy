package io.nop.lint.core.engine;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * The per-lint-call budget and degrade-ladder state (roadmap item 31, design
 * 11 §2/§5; plan Decisions 2/7/8). One instance per {@code LintEngine.lint}
 * call — a {@code --fix} multipass performs several lint calls per physical
 * file, each with a fresh budget (plan Decision 7).
 *
 * <p>Two clocks share the profile's single budget value (the minimal
 * mechanism that makes both design §5 responses simultaneously true):
 * the <em>total</em> clock covers the whole lint call — its exhaustion
 * engages the degrade ladder (analyzers close in order, cheaper work
 * continues) — while the <em>pattern-stage</em> clock accumulates only the
 * time spent matching. The pattern clock exceeding the budget trips the
 * circuit breaker: the file's remaining rules abort, because matching is
 * the one stage that can never degrade (design §5 "运行期软预算" last line
 * of defense). When both clocks are over budget at a rule boundary the
 * breaker wins (plan R2 Minor A: abort takes precedence over degrade).</p>
 *
 * <p>All clock reads come from an injectable monotonic supplier (default
 * {@link System#nanoTime()}), so tests drive exhaustion deterministically.
 * The in-script deadline enforcement ({@code LintDeadlineExecutor}) is
 * deliberately outside this abstraction and always uses the real clock
 * (plan R2 Minor C).</p>
 */
final class FileBudget {

    /**
     * The fast profile's per-file xscript time slice in milliseconds
     * (design 11 §5 fast 预算闭合: shared by all matches; exhausted →
     * remaining matches skip xscript, counted, pattern-layer result stands).
     */
    static final int FAST_XSCRIPT_SLICE_MS = 10;

    private final LongSupplier clock;
    private final long budgetNanos;
    private final long totalDeadlineNanos;
    private final long sliceDeadlineNanos;
    private long patternNanos;
    private boolean ladderEngaged;
    private final Set<LintCapability> closedCapabilities = EnumSet.noneOf(LintCapability.class);
    private boolean xscriptTightened;
    private boolean fixClosed;

    private FileBudget(LongSupplier clock, long budgetNanos, long sliceDeadlineNanos) {
        this.clock = clock;
        this.budgetNanos = budgetNanos;
        this.totalDeadlineNanos = clock.getAsLong() + budgetNanos;
        this.sliceDeadlineNanos = sliceDeadlineNanos;
    }

    /**
     * A budget for one lint call under {@code profile}: the design 11 §2
     * soft budget, plus the fast profile's 10ms xscript slice.
     */
    static FileBudget start(LintProfile profile) {
        return start(profile, System::nanoTime);
    }

    /**
     * A budget with an injectable clock (package-visible test seam; plan
     * Decision 8 — public API unchanged).
     */
    static FileBudget start(LintProfile profile, LongSupplier clock) {
        long slice = profile == LintProfile.FAST
                ? clock.getAsLong() + TimeUnit.MILLISECONDS.toNanos(FAST_XSCRIPT_SLICE_MS)
                : 0L;
        return new FileBudget(clock,
                TimeUnit.MILLISECONDS.toNanos(profile.fileBudgetMs()), slice);
    }

    /**
     * A budget that never expires (tests that exercise the pipeline without
     * engaging ladder or breaker; explicit by name, not a silent default).
     */
    static FileBudget withoutLimits() {
        return new FileBudget(() -> 0L, Long.MAX_VALUE, 0L);
    }

    /**
     * The budget's monotonic clock, exposed for the runner's pattern-stage
     * timing so the injected test clock drives the pattern clock too (plan
     * Decision 8; the default is the real {@link System#nanoTime()}).
     */
    long now() {
        return clock.getAsLong();
    }

    /**
     * True when the pattern stage alone has consumed the file budget — the
     * circuit-breaker condition (abort the remaining rules).
     */
    boolean patternStageExceeded() {
        return patternNanos >= budgetNanos;
    }

    /**
     * Adds one matching phase's duration to the pattern-stage clock.
     */
    void addPatternNanos(long deltaNanos) {
        if (deltaNanos > 0) {
            this.patternNanos += deltaNanos;
        }
    }

    /**
     * True when the lint call as a whole has consumed its soft budget — the
     * degrade-ladder trigger (analyzers close, cheaper work continues).
     */
    boolean totalExpired() {
        return clock.getAsLong() >= totalDeadlineNanos;
    }

    /**
     * True when the fast profile's xscript time slice is exhausted
     * (remaining slice below the 1ms deadline floor). Always false outside
     * the fast profile (no slice there).
     */
    boolean xscriptSliceExpired() {
        return sliceDeadlineNanos != 0L && remainingSliceNanos() < TimeUnit.MILLISECONDS.toNanos(1);
    }

    /**
     * The remaining xscript slice in nanoseconds; 0 when this profile has
     * no slice (the caller never mixes slice math into non-fast deadlines).
     */
    long remainingSliceNanos() {
        if (sliceDeadlineNanos == 0L) {
            return 0L;
        }
        return Math.max(0L, sliceDeadlineNanos - clock.getAsLong());
    }

    /**
     * Engages the ladder once per lint call (idempotent): records the
     * capabilities whose analyzer access the closure revokes (only those
     * actually open — a wired probe or live resolver), tightens xscript
     * deadlines, and closes fix generation when the profile opened it.
     */
    void engageLadder(Set<LintCapability> closed, boolean xscriptTightened, boolean fixClosed) {
        if (ladderEngaged) {
            return;
        }
        this.ladderEngaged = true;
        this.closedCapabilities.addAll(closed);
        this.xscriptTightened = xscriptTightened;
        this.fixClosed = fixClosed;
    }

    boolean ladderEngaged() {
        return ladderEngaged;
    }

    /**
     * The pattern-stage clock's current consumption in nanoseconds
     * (breaker logging).
     */
    long patternNanos() {
        return patternNanos;
    }

    /**
     * The capabilities the engaged ladder closed (empty before engagement).
     */
    Set<LintCapability> closedCapabilities() {
        return closedCapabilities;
    }

    boolean xscriptTightened() {
        return xscriptTightened;
    }

    boolean fixClosed() {
        return fixClosed;
    }
}
