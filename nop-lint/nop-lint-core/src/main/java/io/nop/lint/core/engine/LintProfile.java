package io.nop.lint.core.engine;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Execution profile (design 11 §2): a run-mode parameter over one shared
 * rule set and pipeline — behavioral differences come from analyzer
 * availability only, never from rule downgrading.
 *
 * <p>v1 capability sets (design 11 §8 Phase 1): L2 is not delivered yet, so
 * {@code standard} temporarily equals {@code fast} plus an empty constraint
 * set — both profiles carry {@link LintCapability#L1} only. Later waves
 * extend standard/deep from here; the roadmap hard constraint holds in all
 * versions: a profile never fakes a higher level's result with a lower one
 * (unsatisfied rules are counted via {@code skippedByProfile}).</p>
 */
public enum LintProfile {

    /**
     * Editor / pre-commit mode (design 11 §2: single-file soft budget).
     */
    FAST,

    /**
     * CI default mode; in v1 its capability set is identical to
     * {@link #FAST} by design (see design 11 §8 Phase 1).
     */
    STANDARD;

    private static final Set<LintCapability> V1_CAPABILITIES =
            Collections.unmodifiableSet(EnumSet.of(LintCapability.L1));

    /**
     * The analyzer capabilities this profile provides; rules whose
     * {@code requires} are not a subset are skipped (and counted), never
     * downgraded.
     */
    public Set<LintCapability> capabilities() {
        return V1_CAPABILITIES;
    }
}
