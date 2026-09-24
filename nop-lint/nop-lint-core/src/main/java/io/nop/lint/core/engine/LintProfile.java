package io.nop.lint.core.engine;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Execution profile (design 11 §2): a run-mode parameter over one shared
 * rule set and pipeline — behavioral differences come from analyzer
 * availability only, never from rule downgrading.
 *
 * <p>Capability ceilings: {@code fast} carries {@link LintCapability#L1}
 * only (editor budget, no type queries); {@code standard} declares L2 in
 * its ceiling (roadmap item 20); {@code deep} adds the analyzer-backed
 * capabilities {@link LintCapability#L3}/{@link LintCapability#L4}/
 * {@link LintCapability#SCOPE}/{@link LintCapability#METRICS} (roadmap
 * item 31, design 11 §2 deep row). Declaring is the profile's *ceiling*,
 * not a promise: the engine additionally requires each capability's
 * analyzer to be live at run time (L2: a wired {@code TypeResolver}; deep
 * analyzers: a wired {@link AnalyzerAvailability} probe answering live),
 * and a rule whose requirement cannot be served degrades (counted, logged,
 * never answered from a lower level) instead of running or faking. The
 * roadmap hard constraint holds in all versions: a profile never fakes a
 * higher level's result with a lower one.</p>
 */
public enum LintProfile {

    /**
     * Editor / pre-commit mode (design 11 §2: single-file soft budget).
     */
    FAST,

    /**
     * CI default mode; L2-capable when the run wires a live type resolver
     * (roadmap item 20), otherwise affected rules degrade explicitly.
     */
    STANDARD,

    /**
     * Nightly / deep-audit mode (design 11 §2 deep row): adds the deep-only
     * analyzers (dataflow, semantic, scope, metrics) to the ceiling — each
     * still requires a live availability probe at run time (roadmap item
     * 31); the analyzers themselves land with roadmap items 32–36.
     */
    DEEP;

    private static final Set<LintCapability> FAST_CAPABILITIES =
            Collections.unmodifiableSet(EnumSet.of(LintCapability.L1));
    private static final Set<LintCapability> STANDARD_CAPABILITIES =
            Collections.unmodifiableSet(EnumSet.of(LintCapability.L1, LintCapability.L2));
    private static final Set<LintCapability> DEEP_CAPABILITIES = Collections.unmodifiableSet(
            EnumSet.of(LintCapability.L1, LintCapability.L2, LintCapability.L3,
                    LintCapability.L4, LintCapability.SCOPE, LintCapability.METRICS));

    /**
     * The per-match xscript budget ceiling in the {@link #FAST} profile
     * (design 07 §3, design 11 §7: deadline defaults scale by profile —
     * fast 20ms / standard 100ms).
     */
    public static final int FAST_XSCRIPT_BUDGET_CAP_MS = 20;

    /**
     * The per-file soft budget of the {@link #FAST} profile in milliseconds
     * (design 11 §2 budget table).
     */
    public static final int FAST_FILE_BUDGET_MS = 20;

    /**
     * The per-file soft budget of the {@link #STANDARD} profile in
     * milliseconds (design 11 §2 budget table).
     */
    public static final int STANDARD_FILE_BUDGET_MS = 500;

    /**
     * The per-file soft budget of the {@link #DEEP} profile in milliseconds
     * (design 11 §2 budget table: 5s).
     */
    public static final int DEEP_FILE_BUDGET_MS = 5000;

    /**
     * The per-match xscript budget ceiling the degrade ladder tightens to
     * once the file budget is exhausted (design 11 §5 ladder level 4:
     * 100ms → 20ms).
     */
    public static final int LADDER_TIGHTENED_XSCRIPT_BUDGET_MS = FAST_XSCRIPT_BUDGET_CAP_MS;

    /**
     * Resolves the per-match xscript budget for a rule under this profile
     * (design 07 §3): the standard and deep profiles honor the rule's
     * declared {@code xscriptTimeoutMs} as-is; the fast profile tightens it
     * to {@code min(20ms, ruleValue)} so editor-path scripts stay under the
     * soft file budget. The rule value is always >= 1 (parser-validated).
     */
    public int xscriptBudgetMs(int ruleTimeoutMs) {
        if (this == FAST)
            return Math.min(FAST_XSCRIPT_BUDGET_CAP_MS, ruleTimeoutMs);
        return ruleTimeoutMs;
    }

    /**
     * The single-file soft budget of this profile in milliseconds (design
     * 11 §2: fast 20ms / standard 500ms / deep 5s). Soft: exhausting it
     * engages the degrade ladder and the pattern-stage circuit breaker
     * (design 11 §5, roadmap item 31), it never hard-aborts a file.
     */
    public int fileBudgetMs() {
        return switch (this) {
            case FAST -> FAST_FILE_BUDGET_MS;
            case STANDARD -> STANDARD_FILE_BUDGET_MS;
            case DEEP -> DEEP_FILE_BUDGET_MS;
        };
    }

    /**
     * The analyzer capability ceiling this profile declares; rules whose
     * {@code requires} exceed it are skipped (and counted), never
     * downgraded. A capability inside the ceiling still needs its analyzer
     * to be live at run time (L2: a wired, available resolver; deep
     * analyzers: a wired, live {@link AnalyzerAvailability} probe) —
     * otherwise the affected rules degrade explicitly.
     */
    public Set<LintCapability> capabilities() {
        return switch (this) {
            case FAST -> FAST_CAPABILITIES;
            case STANDARD -> STANDARD_CAPABILITIES;
            case DEEP -> DEEP_CAPABILITIES;
        };
    }
}
