package io.nop.lint.core.pattern;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;

import java.util.List;

/**
 * Structural node equality used by meta-var consistency checks (design 04 §2
 * {@code does_node_match_exactly}): identical tree positions match by
 * identity; leaves compare kind and text; internal nodes compare kind and
 * children pairwise. Stricter than upstream's text-only leaf comparison —
 * same text under different kinds must not count as the same node.
 */
final class NodeExactEquality {

    /**
     * Recursion ceiling for pathological deep nesting, aligned in spirit with
     * {@code ReferentMatcher}'s expansion cap: a generated deep-chain input
     * fails closed with a diagnosable module exception instead of a bare
     * {@link StackOverflowError} (plan 08; contract note in design 04 §2).
     */
    private static final int MAX_DEPTH = 64;

    private NodeExactEquality() {
    }

    static boolean isExact(LintNode a, LintNode b) {
        return isExact(a, b, 0);
    }

    private static boolean isExact(LintNode a, LintNode b, int depth) {
        if (depth > MAX_DEPTH) {
            throw new NopLintException("meta-var consistency check exceeded " + MAX_DEPTH
                    + " nesting levels (pathological input; fail-closed)");
        }
        if (a.equals(b)) {
            return true;
        }
        if (a.kindId() != b.kindId()) {
            return false;
        }
        // localized: each side's children are materialized exactly once per
        // pair (each children() call rebuilds a tree-sitter cursor)
        List<LintNode> aChildren = a.children();
        List<LintNode> bChildren = b.children();
        if (aChildren.size() != bChildren.size()) {
            return false;
        }
        if (aChildren.isEmpty()) {
            return a.text().equals(b.text());
        }
        for (int i = 0; i < aChildren.size(); i++) {
            if (!isExact(aChildren.get(i), bChildren.get(i), depth + 1)) {
                return false;
            }
        }
        return true;
    }
}
