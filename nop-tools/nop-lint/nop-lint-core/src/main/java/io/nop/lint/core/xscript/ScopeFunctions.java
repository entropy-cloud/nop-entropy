package io.nop.lint.core.xscript;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.semantic.ScopeResolver;

import java.util.List;

/**
 * The {@code scope} xscript binding (roadmap item 33): the scope-analysis
 * queries a deep-profile rule calls as {@code scope.definition(node)} /
 * {@code scope.declaredVariables(node)} / {@code scope.shadows(node)} /
 * {@code scope.kind(node)}. The definition answer maps the resolver's byte
 * offset back to the CST node (greedy descent from the tree root — plan
 * Decision 7) and comes back as a {@link NodeWrapper}; null is the
 * legitimate no-definition answer. Failures surface through the xscript
 * failure path (skip and count), never as fabricated results.
 */
final class ScopeFunctions {

    private final ScopeResolver resolver;
    private final String filePath;
    private final byte[] source;
    private final NodeWrapper matchNode;

    ScopeFunctions(ScopeResolver resolver, String filePath, byte[] source, NodeWrapper matchNode) {
        this.resolver = resolver;
        this.filePath = filePath;
        this.source = source;
        this.matchNode = matchNode;
    }

    /**
     * The declaring node of the identifier at the queried node, or null
     * when nothing in the unit declares it.
     */
    public NodeWrapper definition(Object nodeArg) {
        int[] at = positionOf(nodeArg);
        long definitionByte = resolver.definitionOf(filePath, at[0], at[1]);
        if (definitionByte < 0) {
            return null;
        }
        LintNode root = rootOf();
        LintNode found = descend(root, (int) definitionByte);
        if (found == null) {
            throw new NopLintException("the scope definition byte offset " + definitionByte
                    + " in '" + filePath + "' has no containing CST node (binding invariant broken)");
        }
        return new NodeWrapper(found, new SourceMap(source));
    }

    public List<String> declaredVariables(Object nodeArg) {
        int[] at = positionOf(nodeArg);
        return resolver.declaredNames(filePath, at[0], at[1]);
    }

    public boolean shadows(Object nodeArg) {
        int[] at = positionOf(nodeArg);
        return resolver.shadows(filePath, at[0], at[1]);
    }

    public String kind(Object nodeArg) {
        int[] at = positionOf(nodeArg);
        return resolver.scopeKind(filePath, at[0], at[1]);
    }

    private int[] positionOf(Object nodeArg) {
        if (!(nodeArg instanceof NodeWrapper wrapper)) {
            throw new NopLintException("scope queries expect a node argument, got "
                    + (nodeArg == null ? "null" : nodeArg.getClass().getName()));
        }
        LintNode node = wrapper.unwrap();
        return new int[]{lineOf(node), columnOf(node)};
    }

    private int lineOf(LintNode node) {
        return lineCol(node)[0];
    }

    private int columnOf(LintNode node) {
        return lineCol(node)[1];
    }

    private int[] lineCol(LintNode node) {
        io.nop.lint.core.node.SourcePositions.LineCol pos =
                io.nop.lint.core.node.SourcePositions.lineColUtf16(source, node.range().startByte());
        return new int[]{pos.line(), pos.colUtf16()};
    }

    /**
     * The tree root: the match node's parent chain terminates at the root
     * (whose parent is null).
     */
    private LintNode rootOf() {
        LintNode current = matchNode.unwrap();
        LintNode parent = current.parent();
        while (parent != null) {
            current = parent;
            parent = current.parent();
        }
        return current;
    }

    /**
     * Greedy descent (plan Decision 7): from the root, repeatedly step into
     * the child whose range contains the byte offset — O(depth × branching),
     * no full-tree scan.
     */
    private LintNode descend(LintNode node, int byteOffset) {
        LintNode current = node;
        boolean descended = true;
        while (descended) {
            descended = false;
            for (LintNode child : current.children()) {
                if (child.range().startByte() <= byteOffset
                        && byteOffset < child.range().endByte()) {
                    current = child;
                    descended = true;
                    break;
                }
            }
        }
        return current;
    }
}
