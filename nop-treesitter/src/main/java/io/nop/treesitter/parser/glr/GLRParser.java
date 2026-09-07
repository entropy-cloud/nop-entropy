package io.nop.treesitter.parser.glr;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.lexer.Lexer;
import io.nop.treesitter.subtree.Subtree;
import io.nop.treesitter.subtree.SubtreeArena;

import java.util.ArrayList;
import java.util.List;

/**
 * GLR parser driving the language's parse table with a graph-structured stack,
 * following the tree-sitter C runtime semantics (lib/src/parser.c + stack.c):
 * conflict action groups fork into multiple stack versions, versions that
 * converge on the same state at the same position merge back, reductions
 * rebuild the parent node for every distinct path through the graph, and the
 * best complete tree is selected by dynamic precedence / structural comparison.
 * The linear single-version case stays on the same fast path (no ambiguity →
 * no forking).
 *
 * <p>Semantics kept identical to the C runtime within the plan scope: extras
 * are stack entries swept into their enclosing node on reduce; repeated rules
 * reduce to nested hidden nodes; a reduce carries the production id through the
 * arena (the subtree's {@code state} slot) so {@code TSTree} can apply
 * alias-sequence relabeling when rendering. Unexpected tokens raise
 * {@link TreeSitterException} — error recovery is roadmap item 11.</p>
 */
public final class GLRParser {

    private static final int MAX_VERSION_COUNT = 6;
    private static final int MAX_VERSION_COUNT_OVERFLOW = 4;
    private static final int MAX_LINK_COUNT = 8;
    private static final int NO_VERSION = -1;
    private static final int NO_LINK = Subtree.NO_ID;

    private static final int STATUS_ACTIVE = 0;
    private static final int STATUS_HALTED = 1;

    private final Language language;
    private final SubtreeArena arena;
    private final byte[] source;
    private final boolean preferShift;

    private GSSNode[] gss;
    private int gssCount;
    private Version[] versions;
    private int versionCount;

    private int[] subtreeSize;
    private int[] subtreeDynPrec;

    private int cachedParseState = -1;
    private int cachedPosition = -1;
    private Lexer.Token cachedToken;

    private int finishedRoot = Subtree.NO_ID;

    private GLRParser(Language language, SubtreeArena arena, byte[] source, ParserOptions options) {
        this.language = language;
        this.arena = arena;
        this.source = source;
        this.preferShift = options.preferShift();
        this.gss = new GSSNode[16];
        this.versions = new Version[8];
        this.subtreeSize = new int[16];
        this.subtreeDynPrec = new int[16];
    }

    public static int parse(Language language, SubtreeArena arena, byte[] source) {
        return parse(language, arena, source, ParserOptions.DEFAULT);
    }

    public static int parse(Language language, SubtreeArena arena, byte[] source, ParserOptions options) {
        return new GLRParser(language, arena, source, options).run();
    }

    // ------------------------------------------------------------------
    // Main loop
    // ------------------------------------------------------------------

    private int run() {
        int base = newGSSNode(Language.INITIAL_STATE, 0);
        addVersion(base, STATUS_ACTIVE);
        int lastPosition = 0;
        int operationCount = 0;
        for (;;) {
            for (int v = 0; v < versionCount; v++) {
                while (isActive(v)) {
                    if (++operationCount > 4_000_000) {
                        throw new TreeSitterException("parse error: operation limit exceeded "
                                + "(grammar likely loops at byte offset " + headPosition(v) + ")");
                    }
                    advance(v);
                    int position = headPosition(v);
                    if (position > lastPosition || (v > 0 && position == lastPosition)) {
                        lastPosition = position;
                        break;
                    }
                }
            }
            condense();
            if (versionCount == 0) {
                break;
            }
        }
        if (finishedRoot == Subtree.NO_ID) {
            throw new TreeSitterException("parse error: no stack version reached a complete tree");
        }
        return finishedRoot;
    }

    // ------------------------------------------------------------------
    // Advance: process one (state, lookahead) for one version
    // ------------------------------------------------------------------

    private void advance(int version) {
        GSSNode head = gss[versions[version].head];
        int state = head.state;
        int position = head.position;
        Lexer.Token token;
        try {
            token = getToken(state, position);
        } catch (TreeSitterException e) {
            halt(version);
            return;
        }
        int symbol = token.symbol();
        for (;;) {
            int cell = language.tableCell(state, symbol);
            if (cell == 0) {
                if (token.keyword()) {
                    int capture = language.keywordCaptureToken();
                    if (symbol != capture && capture != 0 && language.tableCell(state, capture) != 0) {
                        symbol = capture;
                        continue;
                    }
                }
                halt(version);
                return;
            }
            Language.ActionGroup group = language.actionGroup(cell);
            if (group == null) {
                throw new TreeSitterException("table cell " + cell + " for state " + state
                        + ", symbol '" + language.symbolName(symbol) + "' names an undeclared action group");
            }
            boolean didReduce = false;
            int lastReductionVersion = NO_VERSION;
            for (Language.Action action : group.actions()) {
                switch (action.type()) {
                    case Language.Action.SHIFT -> {
                        if (action.repetition()) {
                            continue;
                        }
                        shift(version, token, symbol, action.extra() ? state : action.state(), action.extra());
                        return;
                    }
                    case Language.Action.REDUCE -> {
                        if (preferShift && groupHasShift(group)) {
                            continue;
                        }
                        int reductionVersion = reduce(version, action);
                        didReduce = true;
                        if (reductionVersion != NO_VERSION) {
                            lastReductionVersion = reductionVersion;
                        }
                    }
                    case Language.Action.ACCEPT -> {
                        accept(version, token);
                        return;
                    }
                    case Language.Action.RECOVER -> throw new UnsupportedOperationException(
                            "parse action type 'recover' is not implemented (error recovery is roadmap item 11)");
                    default -> throw new UnsupportedOperationException(
                            "unimplemented parse action type: " + action.type());
                }
            }
            if (lastReductionVersion != NO_VERSION) {
                renumberVersion(lastReductionVersion, version);
                state = gss[versions[version].head].state;
                continue;
            }
            if (didReduce) {
                halt(version);
                return;
            }
            halt(version);
            return;
        }
    }

    private static boolean groupHasShift(Language.ActionGroup group) {
        for (Language.Action a : group.actions()) {
            if (a.type() == Language.Action.SHIFT && !a.repetition() && !a.extra()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Shift / reduce / accept
    // ------------------------------------------------------------------

    private void shift(int version, Lexer.Token token, int symbol, int nextState, boolean extra) {
        int id = arena.allocate(0, symbol, extra ? 1 : 0, token.startOffset());
        int size = token.endOffset() - gss[versions[version].head].position;
        recordSubtreeSize(id, size, 0);
        push(version, id, nextState);
    }

    private int reduce(int version, Language.Action action) {
        return reduce(version, action.symbol(), action.childCount(), action.dynamicPrecedence(), action.productionId());
    }

    private int reduce(int version, int symbol, int count, int dynamicPrecedence, int productionId) {
        int initialVersionCount = versionCount;
        int topPosition = gss[versions[version].head].position;
        List<Slice> pop = popCount(version, count);
        if (pop.isEmpty()) {
            throw new TreeSitterException("parse error: reduce " + language.symbolName(symbol)
                    + " with " + count + " children, but the stack ran out of entries");
        }
        int removedVersionCount = 0;
        java.util.Map<Integer, List<Slice>> groups = new java.util.LinkedHashMap<>();
        for (Slice slice : pop) {
            int sliceVersion = slice.version - removedVersionCount;
            if (sliceVersion > MAX_VERSION_COUNT + MAX_VERSION_COUNT_OVERFLOW) {
                removeVersion(sliceVersion);
                removedVersionCount++;
                continue;
            }
            groups.computeIfAbsent(sliceVersion, k -> new ArrayList<>()).add(slice);
        }
        for (java.util.Map.Entry<Integer, List<Slice>> entry : groups.entrySet()) {
            int sliceVersion = entry.getKey();
            List<Slice> group = entry.getValue();

            Slice chosen = group.get(0);
            int chosenNode = buildParent(symbol, group.get(0), topPosition, productionId, dynamicPrecedence);
            for (int g = 1; g < group.size(); g++) {
                Slice candidate = group.get(g);
                int candidateNode = buildParent(symbol, candidate, topPosition, productionId, dynamicPrecedence);
                if (shouldReplace(chosenNode, candidateNode)) {
                    chosen = candidate;
                    chosenNode = candidateNode;
                }
            }

            int belowState = gss[versions[sliceVersion].head].state;
            int nextState = language.tableCell(belowState, symbol);
            if (nextState == 0) {
                removeVersion(sliceVersion);
                removedVersionCount++;
                continue;
            }
            push(sliceVersion, chosenNode, nextState);
            int trailing = 0;
            while (trailing < chosen.subtrees().size()
                    && arena.get(chosen.subtrees().get(chosen.subtrees().size() - 1 - trailing)).extra() != 0) {
                trailing++;
            }
            for (int j = trailing - 1; j >= 0; j--) {
                push(sliceVersion, chosen.subtrees().get(chosen.subtrees().size() - 1 - j), nextState);
            }

            for (int j = 0; j < sliceVersion; j++) {
                if (j == version) {
                    continue;
                }
                if (merge(j, sliceVersion)) {
                    removedVersionCount++;
                    break;
                }
            }
        }
        return versionCount > initialVersionCount ? initialVersionCount : NO_VERSION;
    }

    private int buildParent(int symbol, Slice slice, int topPosition, int productionId, int dynamicPrecedence) {
        List<Integer> collected = slice.subtrees();
        int trailing = 0;
        while (trailing < collected.size()
                && arena.get(collected.get(collected.size() - 1 - trailing)).extra() != 0) {
            trailing++;
        }
        int childCount = collected.size() - trailing;
        int[] children = new int[childCount];
        int parentSize = topPosition - slice.bottomPosition();
        for (int k = 0; k < childCount; k++) {
            children[k] = collected.get(k);
        }
        for (int k = 0; k < trailing; k++) {
            parentSize -= subtreeSize[collected.get(collected.size() - 1 - k)];
        }
        return buildNode(symbol, children, childCount, productionId, dynamicPrecedence, parentSize);
    }

    /**
     * True when {@code candidate} replaces {@code current} as the parent for a
     * set of collapsed slices — the C {@code ts_parser__select_tree} applied to
     * two parent candidates: higher dynamic precedence wins, ties fall to the
     * structural comparison.
     */
    private boolean shouldReplace(int current, int candidate) {
        int currentPrec = subtreeDynPrec[current];
        int candidatePrec = subtreeDynPrec[candidate];
        if (candidatePrec > currentPrec) {
            return true;
        }
        return candidatePrec == currentPrec && compareTrees(candidate, current) < 0;
    }

    private int buildNode(int symbol, int[] children, int childCount, int productionId,
                          int dynamicPrecedence, int size) {
        int dynPrec = dynamicPrecedence;
        for (int child : children) {
            dynPrec += subtreeDynPrec[child];
        }
        int node;
        if (childCount <= Subtree.MAX_CHILDREN) {
            node = arena.allocate(productionId, symbol, 0, 0, children);
        } else {
            int rest = buildChain(arena, language.symbolCount() + language.aliasCount(),
                    children, 7, childCount);
            node = arena.allocate(productionId, symbol, 0, 0,
                    children[0], children[1], children[2], children[3],
                    children[4], children[5], children[6], rest);
        }
        recordSubtreeSize(node, size, dynPrec);
        return node;
    }

    private static int buildChain(SubtreeArena arena, int containerSymbol,
                                  int[] children, int from, int to) {
        int n = to - from;
        if (n <= Subtree.MAX_CHILDREN) {
            int[] part = java.util.Arrays.copyOfRange(children, from, to);
            return arena.allocate(0, containerSymbol, 0, 0, part);
        }
        int rest = buildChain(arena, containerSymbol, children, from + 7, to);
        return arena.allocate(0, containerSymbol, 0, 0,
                children[from], children[from + 1], children[from + 2], children[from + 3],
                children[from + 4], children[from + 5], children[from + 6], rest);
    }

    private void accept(int version, Lexer.Token endToken) {
        int endId = arena.allocate(0, Lexer.END_SYMBOL, 1, endToken.startOffset());
        recordSubtreeSize(endId, 0, 0);
        push(version, endId, gss[versions[version].head].state);

        List<Slice> pop = popAll(version);
        for (Slice slice : pop) {
            List<Integer> trees = slice.subtrees;
            int rootIndex = -1;
            for (int i = trees.size() - 1; i >= 0; i--) {
                if (arena.get(trees.get(i)).extra() == 0) {
                    rootIndex = i;
                    break;
                }
            }
            if (rootIndex < 0) {
                throw new TreeSitterException("parse error: accept reached without a completed root node");
            }
            Subtree root = arena.get(trees.get(rootIndex));
            List<Integer> all = new ArrayList<>(trees.size() - 1 + root.childCount());
            for (int i = 0; i < rootIndex; i++) {
                all.add(trees.get(i));
            }
            for (int i = 0; i < root.childCount(); i++) {
                all.add(root.child(i));
            }
            for (int i = rootIndex + 1; i < trees.size(); i++) {
                all.add(trees.get(i));
            }
            int[] children = new int[all.size()];
            for (int i = 0; i < all.size(); i++) {
                children[i] = all.get(i);
            }
            int size = gss[versions[slice.version].head].position;
            int dynPrec = subtreeDynPrec[trees.get(rootIndex)];
            int rootId;
            if (children.length <= Subtree.MAX_CHILDREN) {
                rootId = arena.allocate(root.state(), root.symbol(), 0, 0, children);
            } else {
                int rest = buildChain(arena, language.symbolCount() + language.aliasCount(),
                        children, 3, children.length);
                rootId = arena.allocate(root.state(), root.symbol(), 0, 0,
                        children[0], children[1], children[2], rest);
            }
            recordSubtreeSize(rootId, size, dynPrec);
            selectTree(rootId);
        }
        if (!pop.isEmpty()) {
            removeVersion(pop.get(0).version);
        }
        halt(version);
    }

    private void selectTree(int candidate) {
        if (finishedRoot == Subtree.NO_ID) {
            finishedRoot = candidate;
            return;
        }
        int leftPrec = subtreeDynPrec[finishedRoot];
        int rightPrec = subtreeDynPrec[candidate];
        if (rightPrec > leftPrec) {
            finishedRoot = candidate;
            return;
        }
        if (rightPrec == leftPrec && compareTrees(candidate, finishedRoot) < 0) {
            finishedRoot = candidate;
        }
    }

    /**
     * Structural comparison mirroring {@code ts_subtree_compare}: symbol id,
     * then child count, then children recursively from the last child backward.
     * Returns -1 when {@code left} is "earlier", 1 when {@code right} is.
     */
    private int compareTrees(int leftId, int rightId) {
        int[] work = new int[64];
        int depth = 0;
        work[depth++] = leftId;
        work[depth++] = rightId;
        while (depth > 0) {
            int r = work[--depth];
            int l = work[--depth];
            Subtree ls = arena.get(l);
            Subtree rs = arena.get(r);
            if (ls.symbol() < rs.symbol()) {
                return -1;
            }
            if (rs.symbol() < ls.symbol()) {
                return 1;
            }
            int lc = ls.childCount();
            int rc = rs.childCount();
            if (lc < rc) {
                return -1;
            }
            if (rc < lc) {
                return 1;
            }
            for (int i = lc; i > 0; i--) {
                if (depth + 2 > work.length) {
                    work = java.util.Arrays.copyOf(work, work.length * 2);
                }
                work[depth++] = ls.child(i - 1);
                work[depth++] = rs.child(i - 1);
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // Token handling
    // ------------------------------------------------------------------

    private Lexer.Token getToken(int parseState, int position) {
        if (cachedParseState == parseState && cachedPosition == position) {
            return cachedToken;
        }
        Lexer.Token token = Lexer.next(language, source, position, parseState);
        cachedParseState = parseState;
        cachedPosition = position;
        cachedToken = token;
        return token;
    }

    // ------------------------------------------------------------------
    // Graph-structured stack
    // ------------------------------------------------------------------

    private int newGSSNode(int state, int position) {
        if (gssCount == gss.length) {
            gss = java.util.Arrays.copyOf(gss, gss.length * 2);
        }
        GSSNode node = new GSSNode();
        node.state = state;
        node.position = position;
        node.linkNodes = new int[MAX_LINK_COUNT];
        node.linkSubtrees = new int[MAX_LINK_COUNT];
        java.util.Arrays.fill(node.linkNodes, NO_LINK);
        java.util.Arrays.fill(node.linkSubtrees, NO_LINK);
        node.linkCount = 0;
        gss[gssCount] = node;
        return gssCount++;
    }

    private void push(int version, int subtreeId, int state) {
        GSSNode head = gss[versions[version].head];
        int newNode = newGSSNode(state, head.position + subtreeSize[subtreeId]);
        gss[newNode].linkNodes[0] = versions[version].head;
        gss[newNode].linkSubtrees[0] = subtreeId;
        gss[newNode].linkCount = 1;
        versions[version].head = newNode;
    }

    private void addVersion(int head, int status) {
        if (versionCount == versions.length) {
            versions = java.util.Arrays.copyOf(versions, versions.length * 2);
        }
        versions[versionCount] = new Version(head, status);
        versionCount++;
    }

    private boolean isActive(int version) {
        return versions[version].status == STATUS_ACTIVE;
    }

    private void halt(int version) {
        versions[version].status = STATUS_HALTED;
    }

    private int headPosition(int version) {
        return gss[versions[version].head].position;
    }

    /**
     * Moves the head of {@code source} onto {@code target} (which must be an
     * earlier index) and removes the source slot — the C runtime's
     * {@code ts_stack_renumber_version}.
     */
    private void renumberVersion(int source, int target) {
        if (source == target) {
            return;
        }
        if (target > source) {
            throw new IllegalStateException("renumberVersion requires target < source");
        }
        versions[target] = versions[source];
        removeVersion(source);
    }

    private void removeVersion(int index) {
        System.arraycopy(versions, index + 1, versions, index, versionCount - index - 1);
        versionCount--;
    }

    private void condense() {
        for (int i = 0; i < versionCount; i++) {
            if (!isActive(i)) {
                removeVersion(i);
                i--;
                continue;
            }
            for (int j = 0; j < i; j++) {
                if (!isActive(j)) {
                    continue;
                }
                if (merge(j, i)) {
                    i--;
                    break;
                }
            }
        }
        while (versionCount > MAX_VERSION_COUNT) {
            removeVersion(MAX_VERSION_COUNT);
        }
    }

    private boolean canMerge(int v1, int v2) {
        if (!isActive(v1) || !isActive(v2)) {
            return false;
        }
        GSSNode h1 = gss[versions[v1].head];
        GSSNode h2 = gss[versions[v2].head];
        return h1.state == h2.state && h1.position == h2.position;
    }

    private boolean merge(int v1, int v2) {
        if (!canMerge(v1, v2)) {
            return false;
        }
        GSSNode h2 = gss[versions[v2].head];
        for (int i = 0; i < h2.linkCount; i++) {
            addLink(versions[v1].head, h2.linkNodes[i], h2.linkSubtrees[i]);
        }
        removeVersion(v2);
        return true;
    }

    /**
     * Merges a link into a GSS node, mirroring {@code stack_node_add_link}:
     * deduplicates equivalent links between the same pair of nodes, and when the
     * existing and new link targets are themselves mergeable, merges the new
     * target's links into the existing target recursively.
     */
    private void addLink(int selfNode, int linkNode, int linkSubtree) {
        if (linkNode == selfNode) {
            return;
        }
        GSSNode self = gss[selfNode];
        for (int i = 0; i < self.linkCount; i++) {
            if (subtreeEquivalent(self.linkSubtrees[i], linkSubtree)) {
                if (self.linkNodes[i] == linkNode) {
                    if (linkSubtree != NO_LINK && subtreeDynPrec[linkSubtree] > subtreeDynPrec[self.linkSubtrees[i]]) {
                        self.linkSubtrees[i] = linkSubtree;
                    }
                    return;
                }
                GSSNode existingTarget = gss[self.linkNodes[i]];
                GSSNode newTarget = gss[linkNode];
                if (existingTarget.state == newTarget.state && existingTarget.position == newTarget.position) {
                    for (int j = 0; j < newTarget.linkCount; j++) {
                        addLink(self.linkNodes[i], newTarget.linkNodes[j], newTarget.linkSubtrees[j]);
                    }
                    return;
                }
            }
        }
        if (self.linkCount == MAX_LINK_COUNT) {
            return;
        }
        self.linkNodes[self.linkCount] = linkNode;
        self.linkSubtrees[self.linkCount] = linkSubtree;
        self.linkCount++;
    }

    private boolean subtreeEquivalent(int a, int b) {
        if (a == NO_LINK) {
            return b == NO_LINK;
        }
        if (b == NO_LINK) {
            return false;
        }
        Subtree sa = arena.get(a);
        Subtree sb = arena.get(b);
        return sa.symbol() == sb.symbol()
                && sa.childCount() == sb.childCount()
                && sa.extra() == sb.extra()
                && sa.padding() == sb.padding()
                && subtreeSize[a] == subtreeSize[b];
    }

    // ------------------------------------------------------------------
    // Popping paths through the graph
    // ------------------------------------------------------------------

    private record Slice(int version, int bottomPosition, List<Integer> subtrees) {
    }

    /**
     * Collects every path from the version head down to the node where
     * {@code count} non-extra entries have been crossed, producing one slice
     * per distinct path (bottom-up subtree order) — the C
     * {@code ts_stack_pop_count}. Iterators are processed FIFO (breadth-first,
     * links 1..k-1 before link 0), matching the C {@code stack__iter} so slice
     * order — and with it the reduce-time child selection — matches upstream.
     */
    private List<Slice> popCount(int version, int count) {
        List<Slice> result = new ArrayList<>();
        List<Iter> work = new ArrayList<>();
        work.add(new Iter(versions[version].head, new ArrayList<>(), 0, true));
        while (!work.isEmpty()) {
            List<Iter> batch = new ArrayList<>(work);
            work.clear();
            for (Iter it : batch) {
                if (it.nonExtraCount == count) {
                    List<Integer> reversed = new ArrayList<>(it.subtrees);
                    java.util.Collections.reverse(reversed);
                    addSlice(version, it.node, reversed, result);
                    continue;
                }
                GSSNode node = gss[it.node];
                if (node.linkCount == 0) {
                    continue;
                }
                for (int j = 1; j <= node.linkCount; j++) {
                    int link = (j == node.linkCount) ? 0 : j;
                    Iter next = (link == 0) ? it : new Iter(it.node, new ArrayList<>(it.subtrees), it.nonExtraCount, it.pending);
                    int sub = node.linkSubtrees[link];
                    if (sub != NO_LINK) {
                        next.subtrees.add(sub);
                        if (arena.get(sub).extra() == 0) {
                            next.nonExtraCount++;
                        }
                    } else {
                        next.nonExtraCount++;
                    }
                    next.node = node.linkNodes[link];
                    work.add(next);
                }
            }
        }
        return result;
    }

    /** Collects every path down to the base node — the C {@code ts_stack_pop_all}. */
    private List<Slice> popAll(int version) {
        List<Slice> result = new ArrayList<>();
        List<Iter> work = new ArrayList<>();
        work.add(new Iter(versions[version].head, new ArrayList<>(), 0, true));
        while (!work.isEmpty()) {
            List<Iter> batch = new ArrayList<>(work);
            work.clear();
            for (Iter it : batch) {
                GSSNode node = gss[it.node];
                if (node.linkCount == 0) {
                    List<Integer> reversed = new ArrayList<>(it.subtrees);
                    java.util.Collections.reverse(reversed);
                    addSlice(version, it.node, reversed, result);
                    continue;
                }
                for (int j = 1; j <= node.linkCount; j++) {
                    int link = (j == node.linkCount) ? 0 : j;
                    Iter next = (link == 0) ? it : new Iter(it.node, new ArrayList<>(it.subtrees), it.nonExtraCount, it.pending);
                    int sub = node.linkSubtrees[link];
                    if (sub != NO_LINK) {
                        next.subtrees.add(sub);
                        if (arena.get(sub).extra() == 0) {
                            next.nonExtraCount++;
                        }
                    } else {
                        next.nonExtraCount++;
                    }
                    next.node = node.linkNodes[link];
                    work.add(next);
                }
            }
        }
        return result;
    }

    /**
     * Registers a slice under an existing version that already has the same
     * stop node, or creates a fresh version for it — the C
     * {@code ts_stack__add_slice}.
     */
    private void addSlice(int originalVersion, int stopNode, List<Integer> subtrees, List<Slice> out) {
        for (int i = out.size() - 1; i >= 0; i--) {
            Slice s = out.get(i);
            if (s.version < versionCount && versions[s.version].head == stopNode) {
                out.add(new Slice(s.version, gss[stopNode].position, subtrees));
                return;
            }
        }
        int version = versionCount;
        addVersion(stopNode, versions[originalVersion].status);
        out.add(new Slice(version, gss[stopNode].position, subtrees));
    }

    private static final class Iter {
        int node;
        List<Integer> subtrees;
        int nonExtraCount;
        boolean pending;

        Iter(int node, List<Integer> subtrees, int nonExtraCount, boolean pending) {
            this.node = node;
            this.subtrees = subtrees;
            this.nonExtraCount = nonExtraCount;
            this.pending = pending;
        }
    }

    private static final class GSSNode {
        int state;
        int position;
        int[] linkNodes;
        int[] linkSubtrees;
        int linkCount;
    }

    private static final class Version {
        int head;
        int status;

        Version(int head, int status) {
            this.head = head;
            this.status = status;
        }
    }

    // ------------------------------------------------------------------
    // Side tables
    // ------------------------------------------------------------------

    private void recordSubtreeSize(int id, int size, int dynPrec) {
        if (id >= subtreeSize.length) {
            int cap = Math.max(subtreeSize.length * 2, id + 1);
            subtreeSize = java.util.Arrays.copyOf(subtreeSize, cap);
            subtreeDynPrec = java.util.Arrays.copyOf(subtreeDynPrec, cap);
        }
        subtreeSize[id] = size;
        subtreeDynPrec[id] = dynPrec;
    }

    private TreeSitterException parseError(Lexer.Token token, int state, String reason) {
        return new TreeSitterException("parse error at byte offset " + token.startOffset()
                + " (state " + state + "): " + reason);
    }
}