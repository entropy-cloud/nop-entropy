package io.nop.lint.core.node;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Pre-order depth-first iterator over a {@link LintNode} subtree: yields the
 * root first, then each child's subtree in source order. Backed by an explicit
 * stack so deep trees do not recurse; each node is yielded exactly once.
 */
public final class NodeIterator implements Iterator<LintNode> {

    private LintNode[] stack = new LintNode[16];
    private int top;

    public NodeIterator(LintNode root) {
        push(root);
    }

    @Override
    public boolean hasNext() {
        return top > 0;
    }

    @Override
    public LintNode next() {
        if (top == 0) {
            throw new NoSuchElementException("traversal exhausted");
        }
        LintNode current = stack[--top];
        List<LintNode> children = current.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            push(children.get(i));
        }
        return current;
    }

    private void push(LintNode node) {
        if (top == stack.length) {
            LintNode[] grown = new LintNode[stack.length * 2];
            System.arraycopy(stack, 0, grown, 0, stack.length);
            stack = grown;
        }
        stack[top++] = node;
    }
}
