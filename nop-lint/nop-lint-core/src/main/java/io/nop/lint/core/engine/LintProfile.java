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
 * its ceiling (roadmap item 20) — declaring is the profile's *ceiling*, not
 * a promise: the engine additionally requires a live {@code TypeResolver}
 * at run time, and a rule whose L2 requirement cannot be served degrades
 * (counted, logged, never answered from a lower level) instead of running
 * or faking. The roadmap hard constraint holds in all versions: a profile
 * never fakes a higher level's result with a lower one.</p>
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
    STANDARD;

    private static final Set<LintCapability> FAST_CAPABILITIES =
            Collections.unmodifiableSet(EnumSet.of(LintCapability.L1));
    private static final Set<LintCapability> STANDARD_CAPABILITIES =
            Collections.unmodifiableSet(EnumSet.of(LintCapability.L1, LintCapability.L2));

    /**
     * The per-match xscript budget ceiling in the {@link #FAST} profile
     * (design 07 §3, design 11 §7: deadline defaults scale by profile —
     * fast 20ms / standard 100ms).
     */
    public static final int FAST_XSCRIPT_BUDGET_CAP_MS = 20;

    /**
     * Resolves the per-match xscript budget for a rule under this profile
     * (design 07 §3): the standard profile honors the rule's declared
     * {@code xscriptTimeoutMs} as-is; the fast profile tightens it to
     * {@code min(20ms, ruleValue)} so editor-path scripts stay under the
     * soft file budget. The rule value is always >= 1 (parser-validated).
     */
    public int xscriptBudgetMs(int ruleTimeoutMs) {
        if (this == FAST)
            return Math.min(FAST_XSCRIPT_BUDGET_CAP_MS, ruleTimeoutMs);
        return ruleTimeoutMs;
    }

    /**
     * The analyzer capability ceiling this profile declares; rules whose
     * {@code requires} exceed it are skipped (and counted), never
     * downgraded. A capability inside the ceiling still needs its analyzer
     * to be live at run time (L2: a wired, available resolver) — otherwise
     * the affected rules degrade explicitly.
     */
    public Set<LintCapability> capabilities() {
        return this == FAST ? FAST_CAPABILITIES : STANDARD_CAPABILITIES;
    }
}
