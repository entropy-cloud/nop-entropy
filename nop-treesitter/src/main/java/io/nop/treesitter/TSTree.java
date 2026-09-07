package io.nop.treesitter;

import io.nop.treesitter.cursor.TSTreeCursor;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.subtree.Subtree;
import io.nop.treesitter.subtree.SubtreeArena;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable parse tree: an owned snapshot of the parse-time arena plus the
 * root subtree id.
 *
 * <p>The wrapper owns a private {@link SubtreeArena} that was populated by
 * copying the reachable nodes of the parse arena; after {@link #snapshot} the
 * parse arena can be mutated or discarded freely. Navigation happens through
 * {@link #root()} and the arena API; {@link #toSExpression()} renders the tree
 * in the canonical upstream corpus form — only named and visible nodes appear,
 * invisible / unnamed nodes are flattened through transparently, and children
 * whose reduce production carries an alias sequence are relabeled to the alias
 * symbol (e.g. Java's {@code type_identifier} in generic contexts).</p>
 *
 * <p>The parser stores the reduce production id in the subtree's {@code state}
 * slot; {@code snapshot} preserves it so alias relabeling survives into the
 * immutable tree.</p>
 */
public final class TSTree {

    private final Language language;
    private final SubtreeArena arena;
    private final int rootId;

    private TSTree(Language language, SubtreeArena arena, int rootId) {
        this.language = language;
        this.arena = arena;
        this.rootId = rootId;
    }

    /**
     * Deep-copies the tree rooted at {@code rootId} from {@code parseArena} into
     * a fresh arena owned by the returned tree.
     */
    public static TSTree snapshot(Language language, SubtreeArena parseArena, int rootId) {
        SubtreeArena own = new SubtreeArena();
        int copyRoot = copy(parseArena, own, rootId);
        return new TSTree(language, own, copyRoot);
    }

    private static int copy(SubtreeArena src, SubtreeArena dst, int id) {
        Subtree node = src.get(id);
        int[] children = new int[node.childCount()];
        for (int i = 0; i < children.length; i++) {
            children[i] = copy(src, dst, node.child(i));
        }
        return dst.allocate(node.state(), node.symbol(), node.extra(), node.padding(), children);
    }

    public Language language() {
        return language;
    }

    /**
     * Id of the root subtree in the tree's own arena.
     */
    public int root() {
        return rootId;
    }

    /**
     * The tree's arena. Read-only by convention: the tree snapshot must not be
     * mutated while it is in use.
     */
    public SubtreeArena arena() {
        return arena;
    }

    /**
     * A {@link TSNode} handle for the tree root.
     */
    public TSNode rootNode() {
        return new TSNode(this, rootId, 0);
    }

    /**
     * A navigation cursor rooted at the tree root (C {@code TSTreeCursor}).
     */
    public TSTreeCursor cursor() {
        return new TSTreeCursor(rootNode());
    }

    /**
     * Canonical s-expression in the upstream corpus format: named visible nodes
     * only (with alias relabeling applied), two-space indentation per level,
     * invisible / unnamed nodes flattened through.
     */
    public String toSExpression() {
        StringBuilder sb = new StringBuilder();
        writeNode(rootId, 0, sb, 0);
        return sb.toString();
    }

    /**
     * Single-line s-expression in the C runtime's {@code ts_node_string} format:
     * every node preceded by a space, {@code field: (child)} prefixes for
     * field-mapped children when {@code includeFields} is set, alias symbols
     * applied, invisible / unnamed nodes flattened through transparently (their
     * field names propagate to their children), and the root always written.
     */
    public String toSexpString(boolean includeFields) {
        StringBuilder sb = new StringBuilder();
        writeFlat(rootId, true, 0, null, includeFields, sb);
        return sb.toString();
    }

    private void writeFlat(int id, boolean isRoot, int aliasSymbol, String fieldName,
                           boolean includeFields, StringBuilder sb) {
        Subtree node = arena.get(id);
        int symbol = aliasSymbol != 0 ? aliasSymbol : node.symbol();
        boolean visible = isVisible(symbol);
        if (!visible && !isRoot) {
            int structuralIndex = 0;
            for (int i = 0; i < node.childCount(); i++) {
                int child = node.child(i);
                if (isChainContainer(child)) {
                    writeChainRun(node, structuralIndex, child, fieldName, includeFields, sb);
                    structuralIndex += chainRunWidth(child);
                    continue;
                }
                ChildMeta meta = childMeta(node, structuralIndex, child, fieldName, includeFields);
                if (arena.get(child).extra() == 0) {
                    structuralIndex++;
                }
                writeFlat(child, false, meta.alias(), meta.fieldName(), includeFields, sb);
            }
            return;
        }
        if (isRoot && !visible) {
            if (node.childCount() > 0) {
                sb.append('(').append(language.symbolName(symbol));
                writeFlatChildren(node, false, null, includeFields, sb);
                sb.append(')');
            } else if (language.symbolNamed(symbol)) {
                sb.append('(').append(language.symbolName(symbol)).append(')');
            } else {
                sb.append("(\"").append(language.symbolName(symbol)).append("\")");
            }
            return;
        }
        if (!isRoot) {
            if (fieldName != null) {
                sb.append(' ').append(fieldName).append(": ");
            } else {
                sb.append(' ');
            }
        }
        sb.append('(').append(language.symbolName(symbol));
        writeFlatChildren(node, visible, fieldName, includeFields, sb);
        sb.append(')');
    }

    private void writeFlatChildren(Subtree node, boolean nodeVisible, String parentFieldName,
                                   boolean includeFields, StringBuilder sb) {
        int structuralIndex = 0;
        for (int i = 0; i < node.childCount(); i++) {
            int child = node.child(i);
            if (isChainContainer(child)) {
                writeChainRun(node, structuralIndex, child, nodeVisible ? null : parentFieldName, includeFields, sb);
                structuralIndex += chainRunWidth(child);
                continue;
            }
            String fallback = nodeVisible ? null : parentFieldName;
            ChildMeta meta = childMeta(node, structuralIndex, child, fallback, includeFields);
            if (arena.get(child).extra() == 0) {
                structuralIndex++;
            }
            writeFlat(child, false, meta.alias(), meta.fieldName(), includeFields, sb);
        }
    }

    /**
     * The reserved chain-container symbol: nodes with more than
     * {@link Subtree#MAX_CHILDREN} children are split into an invisible
     * container chain. The containers are not grammar nodes — their children
     * continue the enclosing node's structural index, alias sequence and field
     * map, exactly as if they were the enclosing node's direct children.
     */
    private boolean isChainContainer(int id) {
        return arena.get(id).symbol() == language.symbolCount() + language.aliasCount();
    }

    private void writeChainRun(Subtree contextNode, int contextStructuralIndex, int containerId,
                               String fieldFallback, boolean includeFields, StringBuilder sb) {
        Subtree container = arena.get(containerId);
        int structuralIndex = contextStructuralIndex;
        for (int i = 0; i < container.childCount(); i++) {
            int child = container.child(i);
            if (isChainContainer(child)) {
                writeChainRun(contextNode, structuralIndex, child, fieldFallback, includeFields, sb);
                structuralIndex += chainRunWidth(child);
                continue;
            }
            ChildMeta meta = childMeta(contextNode, structuralIndex, child, fieldFallback, includeFields);
            if (arena.get(child).extra() == 0) {
                structuralIndex++;
            }
            writeFlat(child, false, meta.alias(), meta.fieldName(), includeFields, sb);
        }
    }

    private int chainRunWidth(int containerId) {
        Subtree container = arena.get(containerId);
        int width = 0;
        for (int i = 0; i < container.childCount(); i++) {
            int child = container.child(i);
            if (isChainContainer(child)) {
                width += chainRunWidth(child);
            } else if (arena.get(child).extra() == 0) {
                width++;
            }
        }
        return width;
    }

    /**
     * The alias symbol and field name a child at {@code structuralIndex} of
     * {@code node} renders with, mirroring the C runtime's frame setup in
     * {@code ts_subtree__write_to_string}: the node's alias sequence provides
     * the alias, the node's field map provides the field name (inherited
     * entries skipped), and {@code fieldFallback} (the node's own field name,
     * propagated through flattened invisible nodes) applies otherwise.
     */
    private ChildMeta childMeta(Subtree node, int structuralIndex, int childId,
                                String fieldFallback, boolean includeFields) {
        int alias = 0;
        String fieldName = fieldFallback;
        int productionId = node.state();
        if (arena.get(childId).extra() == 0 && productionId != 0) {
            alias = language.aliasAt(productionId, structuralIndex);
            if (includeFields) {
                for (Language.FieldMapEntry entry : language.fieldMap(productionId)) {
                    if (!entry.inherited() && entry.childIndex() == structuralIndex) {
                        fieldName = language.fieldName(entry.fieldId());
                        break;
                    }
                }
            }
        }
        return new ChildMeta(alias, fieldName);
    }

    private record ChildMeta(int alias, String fieldName) {
    }

    private record Child(int id, int aliasSymbol) {
    }

    private void writeNode(int id, int depth, StringBuilder sb, int aliasSymbol) {
        Subtree node = arena.get(id);
        int symbol = aliasSymbol != 0 ? aliasSymbol : node.symbol();
        if (!isVisible(symbol)) {
            for (int i = 0; i < node.childCount(); i++) {
                writeNode(node.child(i), depth, sb, 0);
            }
            return;
        }

        List<Child> visibleChildren = new ArrayList<>();
        collectVisible(node, visibleChildren);
        if (visibleChildren.isEmpty()) {
            sb.append('(').append(language.symbolName(symbol)).append(')');
            return;
        }
        sb.append('(').append(language.symbolName(symbol));
        for (Child child : visibleChildren) {
            sb.append('\n');
            for (int i = 0; i <= depth; i++) {
                sb.append("  ");
            }
            writeNode(child.id(), depth + 1, sb, child.aliasSymbol());
        }
        sb.append(')');
    }

    private void collectVisible(Subtree node, List<Child> out) {
        int structuralIndex = 0;
        for (int i = 0; i < node.childCount(); i++) {
            int child = node.child(i);
            int effective = effectiveSymbol(node, child);
            if (isVisible(effective)) {
                int own = arena.get(child).symbol();
                out.add(new Child(child, effective != own ? effective : 0));
            } else {
                collectVisible(arena.get(child), out);
            }
            if (arena.get(child).extra() == 0) {
                structuralIndex++;
            }
        }
    }

    /**
     * The symbol a child of {@code parent} renders as: the parent's reduce
     * production alias at the child's structural index when present, else the
     * child's own symbol (C {@code ts_language_alias_at}).
     */
    private int effectiveSymbol(Subtree parent, int childId) {
        int productionId = parent.state();
        if (productionId == 0) {
            return arena.get(childId).symbol();
        }
        int alias = language.aliasAt(productionId, structuralIndexOf(parent, childId));
        return alias != 0 ? alias : arena.get(childId).symbol();
    }

    private int structuralIndexOf(Subtree parent, int childId) {
        int structuralIndex = 0;
        for (int i = 0; i < parent.childCount(); i++) {
            int c = parent.child(i);
            if (c == childId) {
                return structuralIndex;
            }
            if (arena.get(c).extra() == 0) {
                structuralIndex++;
            }
        }
        return structuralIndex;
    }

    private boolean isVisible(int symbol) {
        return symbol >= 0 && symbol < language.symbolCount() + language.aliasCount()
                && language.symbolVisible(symbol)
                && language.symbolNamed(symbol);
    }
}