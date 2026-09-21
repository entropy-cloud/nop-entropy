package io.nop.lint.core.pattern;

import io.nop.lint.core.node.LintNode;

/**
 * Matching strictness, v1 subset of the six-level design (design 04 §4):
 * SMART (the default) and AST. CST/Signature/Template are deferred.
 *
 * <p>Decision matrix (aligned with upstream ast-grep strictness.rs):</p>
 * <ul>
 * <li>SMART: a failing unnamed goal terminal skips the candidate; comments
 * (extra nodes) are skipped as candidates; trailing candidates must all be
 * skippable.</li>
 * <li>AST: a failing unnamed goal terminal skips <em>both</em> sides; comments
 * participate as ordinary named nodes (upstream: {@code should_skip_comment}
 * is false for AST — the design table's "AST skips comments" row was a
 * mislabel and is corrected in the design doc).</li>
 * </ul>
 */
public enum Strictness {

    SMART,
    AST;

    /**
     * Whether a failing <em>unnamed</em> goal terminal may be skipped (goal
     * advances). AST-level matching ignores the pattern's unnamed tokens.
     */
    boolean skipGoalUnnamed() {
        return this == AST;
    }

    /**
     * Whether a candidate node that does not match may be stepped over:
     * SMART skips unnamed nodes and extras (comments); AST skips only unnamed
     * nodes — comments are named and must be matched explicitly.
     */
    boolean canSkipCandidate(LintNode node) {
        if (!node.isNamed()) {
            return true;
        }
        return this == SMART && node.isExtra();
    }
}
