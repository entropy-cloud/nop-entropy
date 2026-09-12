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
    private final byte[] source;

    private TSTree(Language language, SubtreeArena arena, int rootId, byte[] source) {
        this.language = language;
        this.arena = arena;
        this.rootId = rootId;
        this.source = source;
    }

    /**
     * Deep-copies the tree rooted at {@code rootId} from {@code parseArena} into
     * a fresh arena owned by the returned tree, retaining {@code source} — the
     * bytes the tree was parsed from — for incremental-edit bookkeeping
     * (changed-range content comparison).
     */
    public static TSTree snapshot(Language language, SubtreeArena parseArena, int rootId, byte[] source) {
        SubtreeArena own = new SubtreeArena();
        int copyRoot = copy(parseArena, own, rootId, language);
        return new TSTree(language, own, copyRoot, source);
    }

    /**
     * Takes ownership of a freshly-parsed arena without copying. Equivalent to
     * {@link #snapshot} when the parse arena has no other user (a fresh
     * arena per parse — the GLR parser instance is discarded on return), which
     * makes the deep copy pure overhead; snapshot remains the right tool when
     * the arena must survive the transfer.
     */
    public static TSTree adopt(Language language, SubtreeArena parseArena, int rootId, byte[] source) {
        return new TSTree(language, parseArena, rootId, source);
    }

    private static int copy(SubtreeArena src, SubtreeArena dst, int id, Language language) {
        Subtree node = src.get(id);
        int[] children = new int[node.childCount()];
        for (int i = 0; i < children.length; i++) {
            children[i] = copy(src, dst, node.child(i), language);
        }
        int copyId;
        if (src.isMissing(id)) {
            copyId = dst.allocateMissing(node.symbol(), node.padding());
        } else if (node.symbol() == language.builtinErrorSymbol() && children.length == 0) {
            copyId = dst.allocateErrorLeaf(node.symbol(), node.padding(), src.lookaheadCharOf(id));
        } else {
            copyId = dst.allocate(node.state(), node.symbol(), node.extra(), node.padding(), children);
        }
        dst.setSize(copyId, src.sizeOf(id));
        return copyId;
    }

    public Language language() {
        return language;
    }

    /**
     * The UTF-8 source bytes this tree was parsed from. Read-only by
     * convention; the incremental-edit machinery reads them for content
     * comparison and never mutates them.
     */
    public byte[] source() {
        return source;
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
        StringBuilder sb = new StringBuilder(estimateRenderLength());
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
        StringBuilder sb = new StringBuilder(estimateRenderLength());
        writeFlat(rootId, true, 0, null, includeFields, sb, new Meta());
        return sb.toString();
    }

    /**
     * Upper bound for the rendered s-expression: every arena node contributes
     * at most one {@code (name )} group plus indentation.
     */
    private int estimateRenderLength() {
        return Math.min(arena.size() * 24 + 64, 256 * 1024 * 1024);
    }

    private void writeFlat(int id, boolean isRoot, int aliasSymbol, String fieldName,
                           boolean includeFields, StringBuilder sb, Meta meta) {
        Subtree node = arena.get(id);
        if (arena.isMissing(id)) {
            int symbol = aliasSymbol != 0 ? aliasSymbol : node.symbol();
            if (!isRoot) {
                if (fieldName != null) {
                    sb.append(' ').append(fieldName).append(": ");
                } else {
                    sb.append(' ');
                }
            }
            sb.append("(MISSING ");
            appendSymbolArgument(symbol, sb);
            sb.append(')');
            return;
        }
        if (isLexerErrorLeaf(id)) {
            if (!isRoot) {
                if (fieldName != null) {
                    sb.append(' ').append(fieldName).append(": ");
                } else {
                    sb.append(' ');
                }
            }
            sb.append("(UNEXPECTED ");
            appendCharLiteral(arena.lookaheadCharOf(id), sb);
            sb.append(')');
            return;
        }
        int symbol = aliasSymbol != 0 ? aliasSymbol : node.symbol();
        boolean visible = isVisible(symbol);
        if (!visible && !isRoot) {
            int structuralIndex = 0;
            for (int i = 0; i < node.childCount(); i++) {
                int child = node.child(i);
                if (isChainContainer(child)) {
                    writeChainRun(node, structuralIndex, child, fieldName, includeFields, sb, meta);
                    structuralIndex += chainRunWidth(child);
                    continue;
                }
                childMeta(node, structuralIndex, child, fieldName, includeFields, meta);
                if (arena.get(child).extra() == 0) {
                    structuralIndex++;
                }
                writeFlat(child, false, meta.alias, meta.fieldName, includeFields, sb, meta);
            }
            return;
        }
        if (isRoot && !visible) {
            if (node.childCount() > 0) {
                sb.append('(').append(symbolLabel(symbol));
                writeFlatChildren(node, false, null, includeFields, sb, meta);
                sb.append(')');
            } else if (language.symbolNamed(symbol)) {
                sb.append('(').append(symbolLabel(symbol)).append(')');
            } else {
                sb.append("(\"").append(symbolLabel(symbol)).append("\")");
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
        sb.append('(').append(symbolLabel(symbol));
        writeFlatChildren(node, visible, fieldName, includeFields, sb, meta);
        sb.append(')');
    }

    private void writeFlatChildren(Subtree node, boolean nodeVisible, String parentFieldName,
                                   boolean includeFields, StringBuilder sb, Meta meta) {
        int structuralIndex = 0;
        for (int i = 0; i < node.childCount(); i++) {
            int child = node.child(i);
            if (isChainContainer(child)) {
                writeChainRun(node, structuralIndex, child, nodeVisible ? null : parentFieldName, includeFields, sb, meta);
                structuralIndex += chainRunWidth(child);
                continue;
            }
            String fallback = nodeVisible ? null : parentFieldName;
            childMeta(node, structuralIndex, child, fallback, includeFields, meta);
            if (arena.get(child).extra() == 0) {
                structuralIndex++;
            }
            writeFlat(child, false, meta.alias, meta.fieldName, includeFields, sb, meta);
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
        return arena.get(id).symbol() == language.chainContainerSymbol();
    }

    private void writeChainRun(Subtree contextNode, int contextStructuralIndex, int containerId,
                               String fieldFallback, boolean includeFields, StringBuilder sb, Meta meta) {
        Subtree container = arena.get(containerId);
        int structuralIndex = contextStructuralIndex;
        for (int i = 0; i < container.childCount(); i++) {
            int child = container.child(i);
            if (isChainContainer(child)) {
                writeChainRun(contextNode, structuralIndex, child, fieldFallback, includeFields, sb, meta);
                structuralIndex += chainRunWidth(child);
                continue;
            }
            childMeta(contextNode, structuralIndex, child, fieldFallback, includeFields, meta);
            if (arena.get(child).extra() == 0) {
                structuralIndex++;
            }
            writeFlat(child, false, meta.alias, meta.fieldName, includeFields, sb, meta);
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
    private void childMeta(Subtree node, int structuralIndex, int childId,
                           String fieldFallback, boolean includeFields, Meta meta) {
        int alias = 0;
        String fieldName = fieldFallback;
        int productionId = node.state();
        if (arena.get(childId).extra() == 0 && productionId != 0) {
            alias = language.aliasAt(productionId, structuralIndex);
            if (includeFields) {
                int fieldId = language.fieldIdAt(productionId, structuralIndex);
                if (fieldId != 0) {
                    fieldName = language.fieldName(fieldId);
                }
            }
        }
        meta.alias = alias;
        meta.fieldName = fieldName;
    }

    private record Child(int id, int aliasSymbol) {
    }

    /**
     * Reusable per-render holder for the alias and field name computed for one
     * child; threaded through the write recursion so no per-child allocation
     * is needed. Mutable by design — each frame reads both fields into its own
     * parameters before recursing.
     */
    private static final class Meta {
        int alias;
        String fieldName;
    }

    private void writeNode(int id, int depth, StringBuilder sb, int aliasSymbol) {
        Subtree node = arena.get(id);
        if (arena.isMissing(id)) {
            int symbol = aliasSymbol != 0 ? aliasSymbol : node.symbol();
            sb.append("(MISSING ");
            appendSymbolArgument(symbol, sb);
            sb.append(')');
            return;
        }
        if (isLexerErrorLeaf(id)) {
            sb.append("(UNEXPECTED ");
            appendCharLiteral(arena.lookaheadCharOf(id), sb);
            sb.append(')');
            return;
        }
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
            sb.append('(').append(symbolLabel(symbol)).append(')');
            return;
        }
        sb.append('(').append(symbolLabel(symbol));
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
            if (isVisible(effective) || arena.isMissing(child) || isLexerErrorLeaf(child)) {
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
        if (symbol == language.builtinErrorSymbol()) {
            return true;
        }
        if (symbol == language.builtinErrorRepeatSymbol()) {
            return false;
        }
        return symbol >= 0 && symbol < language.symbolCount() + language.aliasCount()
                && language.symbolVisible(symbol)
                && language.symbolNamed(symbol);
    }

    /**
     * Rendered node label for a (possibly builtin) symbol: builtin ERROR has no
     * grammar-table entry, so it never goes through {@code symbolName}.
     */
    private String symbolLabel(int symbol) {
        if (symbol == language.builtinErrorSymbol()) {
            return "ERROR";
        }
        return language.symbolName(symbol);
    }

    /**
     * The symbol argument of {@code (MISSING x)}: named tokens render bare,
     * anonymous tokens quoted (C {@code ts_subtree__write_to_string}).
     */
    private void appendSymbolArgument(int symbol, StringBuilder sb) {
        String name = symbol == language.builtinErrorSymbol() ? "ERROR" : language.symbolName(symbol);
        if (symbol != language.builtinErrorSymbol() && language.symbolNamed(symbol)) {
            sb.append(name);
        } else {
            sb.append('"').append(name).append('"');
        }
    }

    private boolean isLexerErrorLeaf(int id) {
        Subtree node = arena.get(id);
        return node.symbol() == language.builtinErrorSymbol()
                && node.childCount() == 0
                && arena.sizeOf(id) > 0;
    }

    /**
     * C {@code ts_subtree__write_char_to_string}: printable ASCII as
     * {@code 'c'}, the usual escapes, other codepoints as their decimal value.
     */
    private static void appendCharLiteral(int c, StringBuilder sb) {
        if (c == -1) {
            sb.append("INVALID");
        } else if (c == 0) {
            sb.append("'\\0'");
        } else if (c == '\n') {
            sb.append("'\\n'");
        } else if (c == '\t') {
            sb.append("'\\t'");
        } else if (c == '\r') {
            sb.append("'\\r'");
        } else if (c >= 32 && c < 127) {
            sb.append('\'').append((char) c).append('\'');
        } else {
            sb.append(c);
        }
    }
}