package io.nop.lint.core.pattern;

import io.nop.lint.core.node.LintNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Lazy traversal sequences for the relational operators. Iterators are
 * pre-order and allocation-light; nothing materializes the traversed nodes,
 * so {@code end}-horizon searches early-exit on the first inner match.
 * Sibling sequences are short lists (they double as neighbor sequences).
 */
final class RelationalSequences {

    private RelationalSequences() {
    }

    /**
     * The ancestor chain of {@code start}, beginning at its direct parent.
     */
    static Iterator<LintNode> ancestors(LintNode start) {
        return new Iterator<>() {
            private LintNode next = start.parent();

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public LintNode next() {
                LintNode current = next;
                if (current == null) {
                    throw new NoSuchElementException();
                }
                next = current.parent();
                return current;
            }
        };
    }

    /**
     * The siblings preceding {@code node} (same parent), nearest first.
     */
    static List<LintNode> previousSiblings(LintNode node) {
        List<LintNode> siblings = siblingsOf(node);
        int index = siblings.indexOf(node);
        if (index <= 0) {
            return List.of();
        }
        List<LintNode> reversed = new ArrayList<>(index);
        for (int i = index - 1; i >= 0; i--) {
            reversed.add(siblings.get(i));
        }
        return reversed;
    }

    /**
     * The siblings following {@code node} (same parent), nearest first.
     */
    static List<LintNode> nextSiblings(LintNode node) {
        List<LintNode> siblings = siblingsOf(node);
        int index = siblings.indexOf(node);
        if (index < 0 || index + 1 >= siblings.size()) {
            return List.of();
        }
        return siblings.subList(index + 1, siblings.size());
    }

    /**
     * The descendants of {@code roots} in pre-order (the roots themselves
     * come first, each followed by its own subtree).
     */
    static Iterator<LintNode> descendants(List<LintNode> roots) {
        if (roots.isEmpty()) {
            return Collections.emptyIterator();
        }
        return new Iterator<>() {
            private final ArrayDeque<Iterator<LintNode>> stack = new ArrayDeque<>();

            {
                stack.push(roots.iterator());
            }

            @Override
            public boolean hasNext() {
                while (!stack.isEmpty()) {
                    if (stack.peek().hasNext()) {
                        return true;
                    }
                    stack.pop();
                }
                return false;
            }

            @Override
            public LintNode next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                LintNode node = stack.peek().next();
                List<LintNode> children = node.children();
                if (!children.isEmpty()) {
                    stack.push(children.iterator());
                }
                return node;
            }
        };
    }

    private static List<LintNode> siblingsOf(LintNode node) {
        LintNode parent = node.parent();
        return parent == null ? List.of() : parent.children();
    }
}
