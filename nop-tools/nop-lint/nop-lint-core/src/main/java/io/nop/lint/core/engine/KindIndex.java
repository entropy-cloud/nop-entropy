package io.nop.lint.core.engine;

import io.nop.lint.core.node.LintNode;

import java.util.Arrays;

/**
 * File-level kind occurrence set (design 03 §1.3 "kind 过滤", design 11 §3
 * "管线第 1 步"): one pre-order traversal collecting every kind id present
 * in the tree, as a sorted distinct int array. Each rule's target kinds are
 * intersected against this array for an O(log K)-per-rule decision, so
 * disjoint rules never reach their matcher. Primitive collection throughout —
 * the traversal runs once per file and must not box per node (plan 08).
 */
final class KindIndex {

    private KindIndex() {
    }

    /**
     * Collects the kind ids of every node in {@code root}'s subtree (root
     * included).
     */
    static int[] collect(LintNode root) {
        int[] kinds = new int[64];
        int count = 0;
        for (LintNode node : root) {
            int kindId = node.kindId();
            if (count == kinds.length) {
                kinds = Arrays.copyOf(kinds, kinds.length * 2);
            }
            kinds[count++] = kindId;
        }
        if (count == 0) {
            return new int[0];
        }
        Arrays.sort(kinds, 0, count);
        int distinct = 1;
        for (int i = 1; i < count; i++) {
            if (kinds[i] != kinds[distinct - 1]) {
                kinds[distinct++] = kinds[i];
            }
        }
        return distinct == count ? kinds : Arrays.copyOf(kinds, distinct);
    }
}
