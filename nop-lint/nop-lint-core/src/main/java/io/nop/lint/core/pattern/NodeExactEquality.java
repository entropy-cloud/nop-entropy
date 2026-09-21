package io.nop.lint.core.pattern;

import io.nop.lint.core.node.LintNode;

/**
 * Structural node equality used by meta-var consistency checks (design 04 §2
 * {@code does_node_match_exactly}): identical tree positions match by
 * identity; leaves compare kind and text; internal nodes compare kind and
 * children pairwise. Stricter than upstream's text-only leaf comparison —
 * same text under different kinds must not count as the same node.
 */
final class NodeExactEquality {

    private NodeExactEquality() {
    }

    static boolean isExact(LintNode a, LintNode b) {
        if (a.equals(b)) {
            return true;
        }
        if (a.kindId() != b.kindId()) {
            return false;
        }
        if (a.children().isEmpty() && b.children().isEmpty()) {
            return a.text().equals(b.text());
        }
        var aChildren = a.children();
        var bChildren = b.children();
        if (aChildren.size() != bChildren.size()) {
            return false;
        }
        for (int i = 0; i < aChildren.size(); i++) {
            if (!isExact(aChildren.get(i), bChildren.get(i))) {
                return false;
            }
        }
        return true;
    }
}
