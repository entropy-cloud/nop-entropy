package io.nop.lint.core.xscript;

import io.nop.lint.core.node.LintNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The xscript view of one {@link LintNode} (design 07 §2.2, the only node
 * surface scripts can touch). Method-per-method it maps onto the facade:
 * tree navigation stays inside the parsed tree, and {@code range()} converts
 * the node's byte range into the 1-based line/column form the design's API
 * contract promises, using the {@link SourceMap} of the parsed source.
 *
 * <p>Navigation results keep the design's explicitness contract:
 * {@code child}/{@code ancestor}/{@code descendant} return null when no node
 * qualifies, {@code children}/{@code siblings} return empty lists (never
 * null); a root node has no siblings. {@code descendant} scans the subtree
 * in pre-order and returns the first kind match, excluding the node itself;
 * {@code siblings} always excludes the receiver. {@code children}/
 * {@code siblings} enumerate <em>named</em> nodes in source order — the
 * grammar's anonymous punctuation is not part of the script API.</p>
 */
public final class NodeWrapper {

    private final LintNode node;
    private final SourceMap sourceMap;

    NodeWrapper(LintNode node, SourceMap sourceMap) {
        this.node = Objects.requireNonNull(node, "node must not be null");
        this.sourceMap = Objects.requireNonNull(sourceMap, "sourceMap must not be null");
    }

    /**
     * The wrapped facade node (engine-side access; scripts have no path to
     * this type).
     */
    LintNode unwrap() {
        return node;
    }

    /**
     * The node's kind name.
     */
    public String kind() {
        return node.kind();
    }

    /**
     * The node's source text.
     */
    public String text() {
        return node.text();
    }

    /**
     * The named attribute's value, or null when absent or when the backend
     * carries no attribute dimension (roadmap item 29: the orm-unique-key
     * rule judges attribute presence/emptiness through this surface). A
     * present-but-empty attribute returns the empty string, so scripts can
     * distinguish missing from blank with a single null-or-blank test.
     */
    public String attrValue(String name) {
        return node.attrValue(name);
    }

    /**
     * The first child in the given grammar field slot, or null.
     */
    public NodeWrapper child(String fieldName) {
        LintNode child = node.childByField(fieldName);
        return child == null ? null : new NodeWrapper(child, sourceMap);
    }

    /**
     * The named children in source order.
     */
    public List<NodeWrapper> children() {
        return wrapAll(node.namedChildren());
    }

    /**
     * The named children whose kind equals {@code kind}, in source order.
     */
    public List<NodeWrapper> children(String kind) {
        List<NodeWrapper> result = new ArrayList<>();
        for (LintNode child : node.namedChildren()) {
            if (child.kind().equals(kind)) {
                result.add(new NodeWrapper(child, sourceMap));
            }
        }
        return result;
    }

    /**
     * The nearest ancestor whose kind equals {@code kind}, or null.
     */
    public NodeWrapper ancestor(String kind) {
        LintNode current = node.parent();
        while (current != null) {
            if (current.kind().equals(kind)) {
                return new NodeWrapper(current, sourceMap);
            }
            current = current.parent();
        }
        return null;
    }

    /**
     * The first pre-order descendant whose kind equals {@code kind}
     * (excluding this node), or null.
     */
    public NodeWrapper descendant(String kind) {
        boolean self = true;
        for (LintNode current : node) {
            if (self) {
                self = false;
                continue;
            }
            if (current.kind().equals(kind)) {
                return new NodeWrapper(current, sourceMap);
            }
        }
        return null;
    }

    /**
     * The siblings of this node (its parent's named children minus itself),
     * in source order; empty for the root.
     */
    public List<NodeWrapper> siblings() {
        return siblings(null);
    }

    /**
     * The siblings of this node filtered to {@code kind} (all siblings when
     * {@code kind} is null); empty for the root.
     */
    public List<NodeWrapper> siblings(String kind) {
        LintNode parent = node.parent();
        if (parent == null) {
            return List.of();
        }
        List<NodeWrapper> result = new ArrayList<>();
        for (LintNode sibling : parent.namedChildren()) {
            if (sibling.equals(node)) {
                continue;
            }
            if (kind == null || sibling.kind().equals(kind)) {
                result.add(new NodeWrapper(sibling, sourceMap));
            }
        }
        return result;
    }

    /**
     * The node's position as 1-based {@code startLine}/{@code startCol}/
     * {@code endLine}/{@code endCol}; columns count UTF-8 bytes from the
     * line start (design 07 §2.2 note: the facade's byte offsets carry no
     * line structure, so the source map converts them once per file).
     */
    public Map<String, Object> range() {
        int start = node.range().startByte();
        int end = node.range().endByte();
        int endOffset = end > start ? end - 1 : start;
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("startLine", sourceMap.lineOf(start));
        range.put("startCol", sourceMap.columnOf(start));
        range.put("endLine", sourceMap.lineOf(endOffset));
        range.put("endCol", sourceMap.columnOf(endOffset));
        return range;
    }

    private List<NodeWrapper> wrapAll(List<LintNode> nodes) {
        List<NodeWrapper> result = new ArrayList<>(nodes.size());
        for (LintNode child : nodes) {
            result.add(new NodeWrapper(child, sourceMap));
        }
        return result;
    }
}
