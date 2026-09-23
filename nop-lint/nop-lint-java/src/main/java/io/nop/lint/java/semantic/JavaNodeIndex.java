package io.nop.lint.java.semantic;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The position-key index over one parsed JavaParser compilation unit (the
 * resolver's primary lookup surface — the engine feeds the resolver
 * {@code (filePath, line0, col0)} positions, never tree-sitter nodes; plan
 * F2 adjudication).
 *
 * <p>Structure (round-1 F7 adjudication): an exact-hit range HashMap plus
 * tree descent for the minimal containing node — O(depth) with a bounded
 * constant, preferred over a sorted left-walk table whose predecessor
 * sibling tail measured avg 6.5 / max 11+ steps on real files. Ranges are
 * UTF-8 byte spans with an exclusive end (the JavaParser INCLUSIVE end
 * column converts via {@code byteOf(endLine, endCol + 1)}).
 *
 * <p>The node-keyed range map is an IDENTITY map (closure-audit finding,
 * 2026-09-24): JavaParser 3.26 {@code Node} overrides equals/hashCode
 * structurally (EqualsVisitor/HashCodeVisitor — position-blind), so a plain
 * HashMap collapses structurally-identical subtrees (repeated literals,
 * repeated identifier references, {@code return null;}, empty blocks) and
 * every duplicate would silently inherit the LAST occurrence's range —
 * returning wrong types without any exception. Identity keying keeps every
 * node's own range.</p></p>
 */
public final class JavaNodeIndex {

    private final CompilationUnit cu;
    private final Map<Node, int[]> ranges;
    private final Map<Long, Node> exactByRange;

    private JavaNodeIndex(CompilationUnit cu, Map<Node, int[]> ranges, Map<Long, Node> exactByRange) {
        this.cu = cu;
        this.ranges = ranges;
        this.exactByRange = exactByRange;
    }

    /**
     * Builds the index over every ranged node of the compilation unit.
     */
    public static JavaNodeIndex build(CompilationUnit cu, LineColBytes lineCols) {
        // identity keying: structurally-equal siblings (repeated literals,
        // references, `return null;`) must keep their OWN ranges — see the
        // class javadoc
        Map<Node, int[]> ranges = new IdentityHashMap<>();
        Map<Long, Node> exact = new HashMap<>();
        cu.findAll(Node.class).forEach(node -> {
            Optional<com.github.javaparser.Range> range = node.getRange();
            if (range.isEmpty()) {
                return;
            }
            int start = lineCols.byteOf(range.get().begin.line, range.get().begin.column);
            int end = lineCols.byteOf(range.get().end.line, range.get().end.column + 1);
            ranges.put(node, new int[]{start, end});
            exact.putIfAbsent(key(start, end), node);
        });
        return new JavaNodeIndex(cu, ranges, exact);
    }

    private static long key(int startByte, int endByte) {
        return ((long) startByte << 32) | (endByte & 0xFFFFFFFFL);
    }

    /**
     * The minimal node whose byte range contains the position
     * (start ≤ pos < exclusive end), by tree descent from the compilation
     * unit. Null when the position falls outside it.
     */
    public Node minimalContaining(int bytePos) {
        return descend(cu, bytePos);
    }

    /**
     * The exact-range hit for a byte span, or null.
     */
    public Node exactAt(int startByte, int endByte) {
        return exactByRange.get(key(startByte, endByte));
    }

    /**
     * The recorded byte range {start, endExclusive} of a node, or null when
     * the node carries no range.
     */
    public int[] rangeOf(Node node) {
        return ranges.get(node);
    }

    private Node descend(Node node, int bytePos) {
        int[] range = ranges.get(node);
        if (range == null || bytePos < range[0] || bytePos >= range[1]) {
            return null;
        }
        for (Node child : node.getChildNodes()) {
            Node deeper = descend(child, bytePos);
            if (deeper != null) {
                return deeper;
            }
        }
        return node;
    }
}
