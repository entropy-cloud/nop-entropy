package io.nop.lint.core.engine;

import io.nop.lint.core.node.LintNode;

import java.util.HashSet;
import java.util.Set;

/**
 * File-level kind occurrence set (design 03 §1.3 "kind 过滤", design 11 §3
 * "管线第 1 步"): one pre-order traversal collecting every kind id present
 * in the tree. Each rule's target kinds are intersected against this set for
 * an O(1)-per-kind decision, so disjoint rules never reach their matcher.
 */
final class KindIndex {

    private KindIndex() {
    }

    /**
     * Collects the kind ids of every node in {@code root}'s subtree (root
     * included).
     */
    static Set<Integer> collect(LintNode root) {
        Set<Integer> kinds = new HashSet<>();
        for (LintNode node : root) {
            kinds.add(node.kindId());
        }
        return kinds;
    }
}
