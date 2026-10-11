package io.nop.lint.core.testing;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.cursor.TSTreeCursor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Observable equivalence oracle for the incremental-parse correctness gate
 * (plan item 16): two trees are equivalent when their full preorder cursor
 * traversals produce identical {@code type@startByte..endByte} sequences.
 *
 * <p>{@code TSNode} is a record over live tree/arena references, so its
 * built-in equality can never compare nodes of two independently parsed
 * trees — the oracle therefore builds the comparable profile explicitly,
 * keeping the judgement observable and diagnosable (first differing entry is
 * reported).</p>
 */
public final class TreeEquivalenceOracle {

    private TreeEquivalenceOracle() {
    }

    /**
     * The full preorder profile of {@code tree}: one {@code type@start..end}
     * entry per visible node, in {@code TSTreeCursor} traversal order.
     */
    public static List<String> profile(TSTree tree) {
        Objects.requireNonNull(tree, "tree must not be null");
        List<String> entries = new ArrayList<>();
        TSTreeCursor cursor = tree.cursor();
        while (true) {
            TSNode node = cursor.currentNode();
            entries.add(node.type() + "@" + node.startByte() + ".." + node.endByte());
            if (cursor.gotoFirstChild()) {
                continue;
            }
            boolean exhausted = false;
            while (true) {
                if (cursor.gotoNextSibling()) {
                    break;
                }
                if (!cursor.gotoParent()) {
                    exhausted = true;
                    break;
                }
            }
            if (exhausted) {
                return entries;
            }
        }
    }

    /**
     * True when both trees produce identical preorder profiles.
     */
    public static boolean equivalent(TSTree expected, TSTree actual) {
        Objects.requireNonNull(expected, "expected tree must not be null");
        Objects.requireNonNull(actual, "actual tree must not be null");
        return profile(expected).equals(profile(actual));
    }

    /**
     * {@link #equivalent} with a diff-reporting failure message: the first
     * mismatching profile entry (and its index) names exactly where the two
     * traversals diverge.
     */
    public static void assertEquivalent(TSTree expected, TSTree actual, String description) {
        List<String> expectedProfile = profile(expected);
        List<String> actualProfile = profile(actual);
        if (expectedProfile.equals(actualProfile)) {
            return;
        }
        int limit = Math.min(expectedProfile.size(), actualProfile.size());
        int divergeAt = limit;
        for (int i = 0; i < limit; i++) {
            if (!expectedProfile.get(i).equals(actualProfile.get(i))) {
                divergeAt = i;
                break;
            }
        }
        String detail = divergeAt < limit
                ? "first difference at entry " + divergeAt + ": expected "
                + expectedProfile.get(divergeAt) + " but was " + actualProfile.get(divergeAt)
                : "profiles share " + limit + " entries but differ in length: expected "
                + expectedProfile.size() + " vs actual " + actualProfile.size();
        throw new AssertionError(description + " failed (" + detail + "); expected profile="
                + expectedProfile + " actual profile=" + actualProfile);
    }
}
