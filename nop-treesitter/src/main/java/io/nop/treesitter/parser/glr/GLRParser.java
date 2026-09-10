package io.nop.treesitter.parser.glr;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.lexer.Lexer;
import io.nop.treesitter.parser.incremental.IncrementalStats;
import io.nop.treesitter.parser.incremental.ReuseCursor;
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
    private static final int MAX_SUMMARY_DEPTH = 16;
    private static final int MAX_COST_DIFFERENCE = 18 * 100;

    private static final int ERROR_COST_PER_RECOVERY = 500;
    private static final int ERROR_COST_PER_MISSING_TREE = 110;
    private static final int ERROR_COST_PER_SKIPPED_TREE = 100;
    private static final int ERROR_COST_PER_SKIPPED_LINE = 30;
    private static final int ERROR_COST_PER_SKIPPED_CHAR = 1;
    private static final int MISSING_LEAF_ERROR_COST =
            ERROR_COST_PER_MISSING_TREE + ERROR_COST_PER_RECOVERY;

    private static final int STATUS_ACTIVE = 0;
    private static final int STATUS_HALTED = 1;
    private static final int STATUS_PAUSED = 2;

    private final Language language;
    private final SubtreeArena arena;
    private final byte[] source;
    private final boolean preferShift;
    private final ReuseCursor reuse;
    private final IncrementalStats stats;

    private GSSNode[] gss;
    private int gssCount;
    private Version[] versions;
    private int versionCount;

    private int[] subtreeSize;
    private int[] subtreeDynPrec;
    private int[] subtreeErrorCost;

    private int cachedParseState = -1;
    private int cachedPosition = -1;
    private Lexer.Token cachedToken;
    private int cachedErrorChar;

    private int finishedRoot = Subtree.NO_ID;
    private int acceptCount;

    private int pendingReusedLeaf = Subtree.NO_ID;
    private int currentLexStamp;

    private GLRParser(Language language, SubtreeArena arena, byte[] source, ParserOptions options) {
        this(language, arena, source, options, null, null);
    }

    private GLRParser(Language language, SubtreeArena arena, byte[] source, ParserOptions options,
                      ReuseCursor reuse, IncrementalStats stats) {
        this.language = language;
        this.arena = arena;
        this.source = source;
        this.preferShift = options.preferShift();
        this.reuse = reuse;
        this.stats = stats;
        this.gss = new GSSNode[16];
        this.versions = new Version[8];
        this.subtreeSize = new int[16];
        this.subtreeDynPrec = new int[16];
        this.subtreeErrorCost = new int[16];
    }

    public static int parse(Language language, SubtreeArena arena, byte[] source) {
        return parse(language, arena, source, ParserOptions.DEFAULT);
    }

    public static int parse(Language language, SubtreeArena arena, byte[] source, ParserOptions options) {
        return new GLRParser(language, arena, source, options).run();
    }

    /**
     * Incremental parse over {@code source} with subtree reuse from the previous
     * tree: the {@code reuse} cursor offers old-tree leaves at each head
     * position and accepted candidates are pushed without re-lexing, exactly as
     * if the lexer had produced them (C {@code ts_parser__reuse_node}).
     */
    public static int parse(Language language, SubtreeArena arena, byte[] source, ParserOptions options,
                            ReuseCursor reuse, IncrementalStats stats) {
        return new GLRParser(language, arena, source, options, reuse, stats).run();
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
            int minErrorCost = condense();
            if (finishedRoot != Subtree.NO_ID && subtreeErrorCost[finishedRoot] < minErrorCost) {
                versionCount = 0;
                break;
            }
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
        currentLexStamp = language.externalLexState(state) != 0 ? NO_LEX_STATE : language.lexState(state);
        pendingReusedLeaf = reuseLeafForPosition(state, position);
        Lexer.Token token;
        if (pendingReusedLeaf != Subtree.NO_ID) {
            Subtree reused = arena.get(pendingReusedLeaf);
            token = new Lexer.Token(reused.symbol(), position, position + arena.sizeOf(pendingReusedLeaf));
        } else {
            token = getToken(version, state, position);
        }
        int symbol = token.symbol();
        for (;;) {
            int cell = isBuiltinErrorSymbol(symbol) ? 0 : language.tableCell(state, symbol);
            if (cell == 0) {
                if (token.keyword()) {
                    int capture = language.keywordCaptureToken();
                    if (symbol != capture && capture != 0 && language.tableCell(state, capture) != 0) {
                        symbol = capture;
                        continue;
                    }
                }
                if (state == Language.ERROR_STATE) {
                    PausedToken paused = PausedToken.of(token, cachedErrorChar);
                    recoverFromError(version, materializeLookahead(version, paused), paused);
                } else {
                    pause(version, PausedToken.of(token, cachedErrorChar));
                }
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
                    case Language.Action.RECOVER -> {
                        PausedToken paused = PausedToken.of(token, cachedErrorChar);
                        int lookahead = pendingReusedLeaf != Subtree.NO_ID
                                ? consumePendingReusedLeaf()
                                : materializeLookahead(version, paused);
                        recoverFromError(version, lookahead, paused);
                        return;
                    }
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

    private boolean isBuiltinErrorSymbol(int symbol) {
        return symbol == language.builtinErrorSymbol() || symbol == language.builtinErrorRepeatSymbol();
    }

    private int consumePendingReusedLeaf() {
        int id = pendingReusedLeaf;
        pendingReusedLeaf = Subtree.NO_ID;
        return id;
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
        int id;
        if (pendingReusedLeaf != Subtree.NO_ID) {
            id = pendingReusedLeaf;
            if (stats != null) {
                stats.recordReuse();
            }
        } else {
            id = arena.allocate(currentLexStamp, symbol, extra ? 1 : 0, token.startOffset());
            int size = token.endOffset() - gss[versions[version].head].position;
            recordSubtreeSize(id, size, 0, 0);
            arena.setSize(id, token.endOffset() - token.startOffset());
        }
        pendingReusedLeaf = Subtree.NO_ID;
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
            if (sliceVersion < 0 || sliceVersion >= versionCount) {
                continue;
            }
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

            for (int j = 0; j < sliceVersion && j < versionCount; j++) {
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
        return buildNode(symbol, children, childCount, productionId, dynamicPrecedence, parentSize,
                slice.bottomPosition());
    }

    /**
     * True when {@code candidate} replaces {@code current} as the parent for a
     * set of collapsed slices — the C {@code ts_parser__select_tree} applied to
     * two parent candidates: lower subtree error cost wins first, then higher
     * dynamic precedence, then — for equal nonzero error cost — the candidate,
     * and finally the structural comparison.
     */
    private boolean shouldReplace(int current, int candidate) {
        int currentErr = subtreeErrorCost[current];
        int candidateErr = subtreeErrorCost[candidate];
        if (candidateErr < currentErr) {
            return true;
        }
        if (currentErr < candidateErr) {
            return false;
        }
        int currentPrec = subtreeDynPrec[current];
        int candidatePrec = subtreeDynPrec[candidate];
        if (candidatePrec > currentPrec) {
            return true;
        }
        if (currentPrec > candidatePrec) {
            return false;
        }
        if (currentErr > 0) {
            return true;
        }
        return compareTrees(candidate, current) < 0;
    }

    private int buildNode(int symbol, int[] children, int childCount, int productionId,
                          int dynamicPrecedence, int size, int bottomPosition) {
        int dynPrec = dynamicPrecedence;
        for (int child : children) {
            dynPrec += subtreeDynPrec[child];
        }
        int firstStart = childCount > 0 ? arena.get(children[0]).padding() : bottomPosition;
        int node;
        if (childCount <= Subtree.MAX_CHILDREN) {
            node = arena.allocate(productionId, symbol, 0, firstStart, children);
        } else {
            int rest = buildChain(arena, language.chainContainerSymbol(), children, 7, childCount);
            node = arena.allocate(productionId, symbol, 0, firstStart,
                    children[0], children[1], children[2], children[3],
                    children[4], children[5], children[6], rest);
        }
        int errorCost = summarizeErrorCost(symbol, children, childCount);
        recordSubtreeSize(node, size, dynPrec, errorCost);
        if (childCount > 0) {
            int lastEnd = arena.get(children[childCount - 1]).padding()
                    + arena.sizeOf(children[childCount - 1]);
            arena.setSize(node, Math.max(0, lastEnd - firstStart));
        }
        return node;
    }

    /**
     * A node's error cost per the C {@code ts_subtree__summarize_children}
     * rules: child costs accumulate (an ERROR_REPEAT child refunds its own
     * extent penalty, which the parent re-charges), ERROR / ERROR_REPEAT nodes
     * additionally pay {@code ERROR_COST_PER_SKIPPED_TREE} per skipped visible
     * subtree plus the extent penalty over their span.
     */
    private int summarizeErrorCost(int symbol, int[] children, int childCount) {
        boolean errorNode = symbol == language.builtinErrorSymbol()
                || symbol == language.builtinErrorRepeatSymbol();
        if (!errorNode && childCount == 0) {
            return 0;
        }
        int errorCost = 0;
        for (int i = 0; i < childCount; i++) {
            int child = children[i];
            Subtree cs = arena.get(child);
            if (cs.symbol() == language.builtinErrorRepeatSymbol()) {
                int extent = errorExtentCost(cs.padding(), subtreeSize[child]);
                errorCost += subtreeErrorCost[child] - extent;
            } else {
                errorCost += subtreeErrorCost[child];
            }
            if (errorNode && cs.extra() == 0
                    && !(cs.symbol() == language.builtinErrorSymbol() && cs.childCount() == 0)) {
                if (isSymbolVisible(cs.symbol())) {
                    errorCost += ERROR_COST_PER_SKIPPED_TREE;
                } else if (cs.childCount() > 0) {
                    errorCost += ERROR_COST_PER_SKIPPED_TREE * visibleChildCount(child);
                }
            }
        }
        if (errorNode) {
            int firstStart = childCount > 0 ? arena.get(children[0]).padding() : 0;
            int lastEnd = childCount > 0
                    ? arena.get(children[childCount - 1]).padding() + arena.sizeOf(children[childCount - 1])
                    : firstStart;
            errorCost += errorExtentCost(firstStart, Math.max(0, lastEnd - firstStart));
        }
        return errorCost;
    }

    /**
     * C {@code ts_subtree__error_extent_cost}: the recovery penalty plus
     * per-character and per-line terms over the error node's span.
     */
    private int errorExtentCost(int padding, int size) {
        int rows = 0;
        int end = Math.min(padding + size, source.length);
        for (int i = Math.max(0, padding); i < end; i++) {
            if (source[i] == '\n') {
                rows++;
            }
        }
        return ERROR_COST_PER_RECOVERY + ERROR_COST_PER_SKIPPED_CHAR * size
                + ERROR_COST_PER_SKIPPED_LINE * rows;
    }

    private boolean isSymbolVisible(int symbol) {
        return symbol >= 0 && symbol < language.symbolCount() + language.aliasCount()
                && language.symbolVisible(symbol);
    }

    /**
     * Visible children of a composite per the C summarize rules: visible
     * children count 1, hidden composites contribute their own visible child
     * count. (Alias-driven counts are not tracked; error paths are cold.)
     */
    private int visibleChildCount(int id) {
        Subtree node = arena.get(id);
        int count = 0;
        for (int i = 0; i < node.childCount(); i++) {
            int child = node.child(i);
            Subtree cs = arena.get(child);
            if (cs.extra() != 0) {
                continue;
            }
            if (isSymbolVisible(cs.symbol())) {
                count++;
            } else if (cs.childCount() > 0) {
                count += visibleChildCount(child);
            }
        }
        return count;
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
        recordSubtreeSize(endId, 0, 0, 0);
        push(version, endId, gss[versions[version].head].state);
        int endPosition = endToken.endOffset();

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
            int size = endPosition - gss[versions[slice.version].head].position;
            int dynPrec = subtreeDynPrec[trees.get(rootIndex)];
            int[] children = new int[all.size()];
            for (int i = 0; i < all.size(); i++) {
                children[i] = all.get(i);
            }
            int rootId;
            if (children.length <= Subtree.MAX_CHILDREN) {
                rootId = arena.allocate(root.state(), root.symbol(), 0, 0, children);
            } else {
                int rest = buildChain(arena, language.chainContainerSymbol(), children, 3, children.length);
                rootId = arena.allocate(root.state(), root.symbol(), 0, 0,
                        children[0], children[1], children[2], rest);
            }
            int errorCost = summarizeErrorCost(root.symbol(), children, children.length);
            recordSubtreeSize(rootId, size, dynPrec, errorCost);
            arena.setSize(rootId, size);
            selectTree(rootId);
            acceptCount++;
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
        int leftErr = subtreeErrorCost[finishedRoot];
        int rightErr = subtreeErrorCost[candidate];
        if (rightErr < leftErr) {
            finishedRoot = candidate;
            return;
        }
        if (leftErr < rightErr) {
            return;
        }
        int leftPrec = subtreeDynPrec[finishedRoot];
        int rightPrec = subtreeDynPrec[candidate];
        if (rightPrec > leftPrec) {
            finishedRoot = candidate;
            return;
        }
        if (leftPrec > rightPrec) {
            return;
        }
        if (leftErr > 0) {
            finishedRoot = candidate;
            return;
        }
        if (compareTrees(candidate, finishedRoot) < 0) {
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

    /** Sentinel recorded in a leaf's state slot when its lex state must never match. */
    private static final int NO_LEX_STATE = -1;

    /**
     * Offers the reuse cursor's candidate at {@code position} as this advance's
     * lookahead: copies the old leaf into the parse arena at the mapped position
     * and returns its id, so every table action (reduce first, then shift)
     * dispatches exactly as it would for a freshly lexed token — the C
     * {@code ts_parser__reuse_node} contract. Gates mirror C's
     * {@code ts_parser__can_reuse_first_leaf}: the state must have no external
     * lex mode (our leaves carry no external-scanner state anchor), the leaf
     * must not be the keyword-capture token (keyword resolution is
     * parse-state dependent), and the table cell must have actions that are
     * safe for reuse either because the leaf was produced under the same lex
     * state (same DFA + same bytes + same start ⇒ deterministic same token)
     * or because the blob's generator-computed {@code reusable} bit marks the
     * cell lexically unambiguous across lex states.
     */
    private int reuseLeafForPosition(int state, int position) {
        if (reuse == null || language.externalLexState(state) != 0) {
            return Subtree.NO_ID;
        }
        ReuseCursor.Candidate candidate = reuse.candidateAt(position);
        if (candidate == null
                || candidate.symbol() == language.keywordCaptureToken()) {
            return Subtree.NO_ID;
        }
        int cell = language.tableCell(state, candidate.symbol());
        if (cell == 0) {
            return Subtree.NO_ID;
        }
        Language.ActionGroup group = language.actionGroup(cell);
        if (group == null || group.actions().length == 0) {
            return Subtree.NO_ID;
        }
        boolean lexStateEqual =
                candidate.lexState() != NO_LEX_STATE && candidate.lexState() == language.lexState(state);
        if (!lexStateEqual && !group.reusable()) {
            return Subtree.NO_ID;
        }
        int id = arena.allocate(candidate.lexState(), candidate.symbol(), candidate.extra() ? 1 : 0, position);
        arena.setSize(id, candidate.size());
        recordSubtreeSize(id, candidate.size(), 0, 0);
        return id;
    }

    /**
     * Produces this advance's lookahead token: either a regular token, or —
     * when no lex mode can match — the C runtime's builtin-error leaf spanning
     * the skipped bytes. The leaf is allocated here (C allocates it inside
     * {@code ts_parser__lex}); a regular token's leaf is allocated per shift,
     * as before.
     */
    private Lexer.Token getToken(int version, int parseState, int position) {
        if (cachedParseState == parseState && cachedPosition == position) {
            return cachedToken;
        }
        boolean ignoreEmptyExternal =
                parseState == Language.ERROR_STATE || !hasAdvancedSinceError(version);
        Lexer.LexOutcome outcome = Lexer.nextForParse(language, source, position, parseState,
                ignoreEmptyExternal);
        Lexer.Token token;
        if (outcome.isError()) {
            int errorStart = outcome.errorStart();
            int errorEnd = outcome.errorEnd();
            int id = arena.allocateErrorLeaf(language.builtinErrorSymbol(), errorStart,
                    outcome.errorChar());
            arena.setSize(id, errorEnd - errorStart);
            recordSubtreeSize(id, errorEnd - errorStart, 0, 0);
            token = new Lexer.Token(language.builtinErrorSymbol(), errorStart, errorEnd);
            cachedErrorChar = outcome.errorChar();
        } else {
            token = outcome.token();
            cachedErrorChar = 0;
        }
        if (stats != null) {
            stats.recordLex();
        }
        cachedParseState = parseState;
        cachedPosition = position;
        cachedToken = token;
        return token;
    }

    // ------------------------------------------------------------------
    // Error recovery (C ts_parser__handle_error / __recover family)
    // ------------------------------------------------------------------

    private ErrorStatus versionStatus(int version) {
        int cost = stackErrorCost(version);
        boolean paused = isPaused(version);
        if (paused) {
            cost += ERROR_COST_PER_SKIPPED_TREE;
        }
        GSSNode head = gss[versions[version].head];
        return new ErrorStatus(cost, nodeCountSinceError(version), head.dynPrec,
                paused || head.state == Language.ERROR_STATE);
    }

    private ErrorComparison compareVersions(ErrorStatus a, ErrorStatus b) {
        if (!a.inError() && b.inError()) {
            return a.cost() < b.cost() ? ErrorComparison.TAKE_LEFT : ErrorComparison.PREFER_LEFT;
        }
        if (a.inError() && !b.inError()) {
            return b.cost() < a.cost() ? ErrorComparison.TAKE_RIGHT : ErrorComparison.PREFER_RIGHT;
        }
        if (a.cost() < b.cost()) {
            return (b.cost() - a.cost()) * (1 + a.nodeCount()) > MAX_COST_DIFFERENCE
                    ? ErrorComparison.TAKE_LEFT : ErrorComparison.PREFER_LEFT;
        }
        if (b.cost() < a.cost()) {
            return (a.cost() - b.cost()) * (1 + b.nodeCount()) > MAX_COST_DIFFERENCE
                    ? ErrorComparison.TAKE_RIGHT : ErrorComparison.PREFER_RIGHT;
        }
        if (a.dynamicPrecedence() > b.dynamicPrecedence()) {
            return ErrorComparison.PREFER_LEFT;
        }
        if (b.dynamicPrecedence() > a.dynamicPrecedence()) {
            return ErrorComparison.PREFER_RIGHT;
        }
        return ErrorComparison.NONE;
    }

    private boolean betterVersionExists(int version, boolean inError, int cost) {
        if (finishedRoot != Subtree.NO_ID && subtreeErrorCost[finishedRoot] <= cost) {
            return true;
        }
        int position = headPosition(version);
        ErrorStatus status = new ErrorStatus(cost, nodeCountSinceError(version),
                gss[versions[version].head].dynPrec, inError);
        for (int i = 0; i < versionCount; i++) {
            if (i == version || !isActive(i) || headPosition(i) < position) {
                continue;
            }
            ErrorStatus statusI = versionStatus(i);
            switch (compareVersions(status, statusI)) {
                case TAKE_RIGHT -> {
                    return true;
                }
                case PREFER_RIGHT -> {
                    if (canMerge(i, version)) {
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    /**
     * Allocates the paused lookahead as an arena leaf: a builtin-error leaf for
     * lexer-skip outcomes, otherwise the token's symbol marked extra when state
     * 1's last action is an extra shift (C marks the lookahead extra before
     * strategy 2 wraps it).
     */
    private int materializeLookahead(int version, PausedToken tok) {
        int position = headPosition(version);
        int id;
        if (tok.symbol() == language.builtinErrorSymbol()) {
            id = arena.allocateErrorLeaf(language.builtinErrorSymbol(), tok.start(),
                    tok.errorChar());
        } else {
            boolean extra = isExtraShiftAtState1(tok.symbol());
            id = arena.allocate(NO_LEX_STATE, tok.symbol(), extra ? 1 : 0, tok.start());
        }
        recordSubtreeSize(id, tok.end() - position, 0, 0);
        arena.setSize(id, tok.end() - tok.start());
        return id;
    }

    private boolean isExtraShiftAtState1(int symbol) {
        if (isBuiltinErrorSymbol(symbol) || symbol >= language.symbolCount()) {
            return false;
        }
        int cell = language.tableCell(Language.INITIAL_STATE, symbol);
        if (cell == 0) {
            return false;
        }
        Language.ActionGroup group = language.actionGroup(cell);
        if (group == null || group.actions().length == 0) {
            return false;
        }
        Language.Action last = group.actions()[group.actions().length - 1];
        return last.type() == Language.Action.SHIFT && last.extra();
    }

    /**
     * The C {@code ts_parser__handle_error}, in C's order: potential reductions,
     * missing-token insertion over every reduction-created version, the NULL
     * discontinuity push into the error state for all of them, merging, the
     * on-demand stack summary, and the unconditional {@code ts_parser__recover}.
     */
    private void handleError(int version, PausedToken paused) {
        int previousVersionCount = versionCount;
        int lookaheadId = materializeLookahead(version, paused);
        int lookaheadLeafSymbol = arena.get(lookaheadId).symbol();

        doAllPotentialReductions(version, 0);
        int versionCountAfterReductions = versionCount;
        int position = headPosition(version);

        boolean didInsertMissingToken = false;
        for (int v = version; v < versionCountAfterReductions; ) {
            if (!didInsertMissingToken) {
                int state = gss[versions[v].head].state;
                for (int missingSymbol = 1; missingSymbol < language.tokenCount(); missingSymbol++) {
                    int stateAfterMissing = language.nextState(state, missingSymbol);
                    if (stateAfterMissing == 0 || stateAfterMissing == state) {
                        continue;
                    }
                    if (!language.hasReduceAction(stateAfterMissing, lookaheadLeafSymbol)) {
                        continue;
                    }
                    addVersion(versions[v].head, STATUS_ACTIVE);
                    int versionWithMissing = versionCount - 1;
                    versions[versionWithMissing].nodeCountAtLastError = versions[v].nodeCountAtLastError;
                    int missingId = arena.allocateMissing(missingSymbol, position);
                    recordSubtreeSize(missingId, 0, 0, MISSING_LEAF_ERROR_COST);
                    push(versionWithMissing, missingId, stateAfterMissing);
                    boolean canShift = doAllPotentialReductions(versionWithMissing, lookaheadLeafSymbol);
                    if (canShift) {
                        didInsertMissingToken = true;
                        break;
                    }
                }
            }
            pushNullIntoErrorState(v);
            v = (v == version) ? previousVersionCount : v + 1;
        }

        for (int i = previousVersionCount; i < versionCountAfterReductions; i++) {
            if (!merge(version, previousVersionCount)) {
                throw new IllegalStateException("recovery: post-discontinuity merge failed");
            }
        }

        recordSummary(version);
        recoverFromError(version, lookaheadId, paused);
    }

    /**
     * C {@code ts_parser__do_all_potential_reductions}: applies every reduce
     * action the state offers (over all terminals when no lookahead symbol is
     * given), reporting whether the lookahead could be shifted afterwards.
     */
    private boolean doAllPotentialReductions(int version, int lookaheadSymbol) {
        int initialVersionCount = versionCount;
        boolean canShiftLookaheadSymbol = false;
        int v = version;
        for (int i = 0; ; i++) {
            if (v >= versionCount) {
                break;
            }
            boolean merged = false;
            for (int j = initialVersionCount; j < v; j++) {
                if (merge(j, v)) {
                    merged = true;
                    break;
                }
            }
            if (merged) {
                continue;
            }
            int versionCountAtTop = versionCount;
            int state = gss[versions[v].head].state;
            boolean hasShiftAction = false;
            List<ReduceAction> reduceActions = new ArrayList<>();
            if (lookaheadSymbol != 0) {
                hasShiftAction = collectCandidateRecoveryActions(state, lookaheadSymbol, reduceActions);
            } else {
                for (int symbol = 1; symbol < language.tokenCount(); symbol++) {
                    if (collectCandidateRecoveryActions(state, symbol, reduceActions)) {
                        hasShiftAction = true;
                    }
                }
                reduceActions.sort((a, b) -> Integer.compare(b.symbol(), a.symbol()));
            }
            int reductionVersion = NO_VERSION;
            for (ReduceAction action : reduceActions) {
                reductionVersion = reduce(v, action.symbol(), action.count(),
                        action.dynamicPrecedence(), action.productionId());
            }
            if (hasShiftAction) {
                canShiftLookaheadSymbol = true;
            } else if (reductionVersion != NO_VERSION && i < MAX_VERSION_COUNT) {
                renumberVersion(reductionVersion, v);
                continue;
            } else if (lookaheadSymbol != 0) {
                removeVersion(v);
            }
            if (v == version) {
                v = versionCountAtTop;
            } else {
                v++;
            }
        }
        return canShiftLookaheadSymbol;
    }

    private boolean collectCandidateRecoveryActions(int state, int symbol, List<ReduceAction> out) {
        if (symbol >= language.tokenCount() || isBuiltinErrorSymbol(symbol)) {
            return false;
        }
        int cell = language.tableCell(state, symbol);
        if (cell == 0) {
            return false;
        }
        Language.ActionGroup group = language.actionGroup(cell);
        if (group == null) {
            return false;
        }
        boolean hasShift = false;
        for (Language.Action action : group.actions()) {
            switch (action.type()) {
                case Language.Action.SHIFT -> {
                    if (!action.extra() && !action.repetition()) {
                        hasShift = true;
                    }
                }
                case Language.Action.RECOVER -> hasShift = true;
                case Language.Action.REDUCE -> {
                    if (action.childCount() > 0) {
                        ReduceAction candidate = new ReduceAction(action.symbol(), action.childCount(),
                                action.dynamicPrecedence(), action.productionId());
                        boolean dup = false;
                        for (ReduceAction existing : out) {
                            if (existing.symbol() == candidate.symbol()
                                    && existing.count() == candidate.count()) {
                                dup = true;
                                break;
                            }
                        }
                        if (!dup) {
                            out.add(candidate);
                        }
                    }
                }
                default -> {
                }
            }
        }
        return hasShift;
    }

    /**
     * The stack summary C records at recovery entry: a breadth-first walk of
     * the version's GSS paths, one deduplicated entry per (depth, state), at
     * most {@link #MAX_SUMMARY_DEPTH} subtrees deep.
     */
    private void recordSummary(int version) {
        List<SummaryEntry> summary = new ArrayList<>();
        List<Iter> work = new ArrayList<>();
        work.add(new Iter(versions[version].head, new ArrayList<>(), 0, true));
        while (!work.isEmpty()) {
            List<Iter> batch = new ArrayList<>(work);
            work.clear();
            for (Iter it : batch) {
                int depth = it.nonExtraCount;
                if (depth > MAX_SUMMARY_DEPTH) {
                    continue;
                }
                int state = gss[it.node].state;
                boolean dup = false;
                for (int i = summary.size() - 1; i >= 0; i--) {
                    SummaryEntry entry = summary.get(i);
                    if (entry.depth() < depth) {
                        break;
                    }
                    if (entry.depth() == depth && entry.state() == state) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    summary.add(new SummaryEntry(gss[it.node].position, depth, state));
                }
                GSSNode node = gss[it.node];
                for (int j = 1; j <= node.linkCount; j++) {
                    int link = (j == node.linkCount) ? 0 : j;
                    Iter next = (link == 0) ? it : new Iter(it.node, new ArrayList<>(), it.nonExtraCount, it.pending);
                    int sub = node.linkSubtrees[link];
                    if (sub == NO_LINK || arena.get(sub).extra() == 0) {
                        next.nonExtraCount++;
                    }
                    next.node = node.linkNodes[link];
                    work.add(next);
                }
            }
        }
        versions[version].summary = summary;
    }

    /**
     * The C {@code ts_parser__recover}: strategy 1 walks the summary
     * head-outward and recovers to the first earlier state that admits the
     * lookahead; strategy 2 skips the lookahead inside an ERROR_REPEAT — and
     * runs even after a successful strategy 1.
     */
    private void recoverFromError(int version, int lookaheadId, PausedToken paused) {
        boolean didRecover = false;
        int previousVersionCount = versionCount;
        int position = headPosition(version);
        List<SummaryEntry> summary = versions[version].summary;
        int nodeCountSinceError = nodeCountSinceError(version);
        int currentErrorCost = stackErrorCost(version);
        int lookaheadSymbol = arena.get(lookaheadId).symbol();
        boolean lookaheadIsError = lookaheadSymbol == language.builtinErrorSymbol();

        if (summary != null && !lookaheadIsError) {
            for (SummaryEntry entry : summary) {
                if (entry.state() == Language.ERROR_STATE) {
                    continue;
                }
                if (entry.position() == position) {
                    continue;
                }
                int depth = entry.depth();
                if (nodeCountSinceError > 0) {
                    depth++;
                }
                boolean wouldMerge = false;
                for (int j = 0; j < previousVersionCount; j++) {
                    if (gss[versions[j].head].state == entry.state() && headPosition(j) == position) {
                        wouldMerge = true;
                        break;
                    }
                }
                if (wouldMerge) {
                    continue;
                }
                int newCost = currentErrorCost
                        + entry.depth() * ERROR_COST_PER_SKIPPED_TREE
                        + (position - entry.position()) * ERROR_COST_PER_SKIPPED_CHAR
                        + (rowCount(position) - rowCount(entry.position())) * ERROR_COST_PER_SKIPPED_LINE;
                if (betterVersionExists(version, false, newCost)) {
                    break;
                }
                if (language.hasActions(entry.state(), lookaheadSymbol)) {
                    if (recoverToState(version, depth, entry.state())) {
                        didRecover = true;
                        break;
                    }
                }
            }
        }

        for (int i = previousVersionCount; i < versionCount; i++) {
            if (!isActive(i)) {
                removeVersion(i);
                i--;
            }
        }

        if (lookaheadSymbol == Lexer.END_SYMBOL) {
            int wrapper = buildErrorComposite(language.builtinErrorSymbol(), new ArrayList<>(), false,
                    position);
            push(version, wrapper, Language.INITIAL_STATE);
            accept(version, new Lexer.Token(Lexer.END_SYMBOL, paused.start(), paused.end()));
            return;
        }

        if (didRecover && versionCount > MAX_VERSION_COUNT) {
            halt(version);
            return;
        }

        int newCost = currentErrorCost + ERROR_COST_PER_SKIPPED_TREE
                + arena.sizeOf(lookaheadId) * ERROR_COST_PER_SKIPPED_CHAR
                + rowCountOverSpan(lookaheadId) * ERROR_COST_PER_SKIPPED_LINE;
        if (betterVersionExists(version, false, newCost)) {
            halt(version);
            return;
        }

        if (Boolean.getBoolean("ts.debug")) {
            System.err.println("SKIP v" + version + " pos=" + position + " sym=" + lookaheadSymbol
                    + " span=[" + arena.get(lookaheadId).padding() + "," + (arena.get(lookaheadId).padding() + arena.sizeOf(lookaheadId)) + ")"
                    + " didRecover=" + didRecover + " sinceErr=" + nodeCountSinceError
                    + " state=" + gss[versions[version].head].state + " arena=" + arena.size());
        }
        int errorRepeat = buildErrorComposite(language.builtinErrorRepeatSymbol(),
                List.of(lookaheadId), false, position);
        if (nodeCountSinceError > 0) {
            List<Slice> pop = popCount(version, 1);
            if (pop.isEmpty()) {
                throw new IllegalStateException("recovery: skip merge found no previous error entry");
            }
            if (pop.size() > 1) {
                for (int i = 1; i < pop.size(); i++) {
                    removeVersion(pop.get(i).version);
                }
                while (versionCount > pop.get(0).version + 1) {
                    removeVersion(pop.get(0).version + 1);
                }
            }
            Slice first = pop.get(0);
            renumberVersion(first.version, version);
            List<Integer> merged = new ArrayList<>(first.subtrees());
            merged.add(errorRepeat);
            errorRepeat = buildErrorComposite(language.builtinErrorRepeatSymbol(), merged, false,
                    position);
        }
        push(version, errorRepeat, Language.ERROR_STATE);
    }

    /**
     * The C {@code ts_parser__recover_to_state}: pops {@code depth} entries,
     * wraps the span (plus any directly-preceding ERROR subtree, whose children
     * splice in front as an invisible ERROR_REPEAT) in an extra-carrying ERROR
     * node, and re-pushes trailing extras after it.
     */
    private boolean recoverToState(int version, int depth, int goalState) {
        List<Slice> pop = popCount(version, depth);
        int previousSliceVersion = NO_VERSION;
        boolean recovered = false;
        for (Slice slice : pop) {
            if (slice.version == previousSliceVersion) {
                continue;
            }
            if (gss[versions[slice.version].head].state != goalState) {
                halt(slice.version);
                continue;
            }
            List<Integer> subtrees = new ArrayList<>(slice.subtrees());
            GSSNode cutHead = gss[versions[slice.version].head];
            for (int i = 0; i < cutHead.linkCount; i++) {
                int sub = cutHead.linkSubtrees[i];
                if (sub != NO_LINK && arena.get(sub).symbol() == language.builtinErrorSymbol()) {
                    versions[slice.version].head = cutHead.linkNodes[i];
                    if (arena.get(sub).childCount() > 0) {
                        Subtree prevError = arena.get(sub);
                        List<Integer> nested = new ArrayList<>(prevError.childCount());
                        for (int c = 0; c < prevError.childCount(); c++) {
                            nested.add(prevError.child(c));
                        }
                        subtrees.add(0, buildErrorComposite(
                                language.builtinErrorRepeatSymbol(), nested, false,
                                headPosition(slice.version)));
                    }
                    break;
                }
            }
            int end = subtrees.size();
            while (end > 0 && arena.get(subtrees.get(end - 1)).extra() != 0) {
                end--;
            }
            List<Integer> wrapped = new ArrayList<>(subtrees.subList(0, end));
            List<Integer> trailing = new ArrayList<>(subtrees.subList(end, subtrees.size()));
            if (!wrapped.isEmpty()) {
                int error = buildErrorComposite(language.builtinErrorSymbol(), wrapped, true,
                        headPosition(slice.version));
                push(slice.version, error, goalState);
            }
            for (int t : trailing) {
                push(slice.version, t, goalState);
            }
            previousSliceVersion = slice.version;
            recovered = true;
        }
        return recovered;
    }

    /**
     * Builds an ERROR / ERROR_REPEAT composite: no production id, span and
     * error cost per the C {@code ts_subtree__summarize_children} rules.
     *
     * @param bottomPosition the stack position the wrapper is pushed from; the
     *        recorded size spans from there to the last child's end so the
     *        leading padding gap counts toward the next stack position (C
     *        total_size semantics — omitting it makes recovery skips re-consume
     *        the same token forever).
     */
    private int buildErrorComposite(int symbol, List<Integer> children, boolean extra,
                                    int bottomPosition) {
        int childCount = children.size();
        int[] arr = new int[childCount];
        for (int i = 0; i < childCount; i++) {
            arr[i] = children.get(i);
        }
        int firstStart = childCount > 0 ? arena.get(arr[0]).padding() : bottomPosition;
        int node = arena.allocate(0, symbol, extra ? 1 : 0, firstStart, arr);
        int dynPrec = 0;
        for (int child : arr) {
            dynPrec += subtreeDynPrec[child];
        }
        int lastEnd = childCount > 0
                ? arena.get(arr[childCount - 1]).padding() + arena.sizeOf(arr[childCount - 1])
                : firstStart;
        int size = Math.max(0, lastEnd - bottomPosition);
        recordSubtreeSize(node, size, dynPrec, summarizeErrorCost(symbol, arr, childCount));
        arena.setSize(node, Math.max(0, lastEnd - firstStart));
        return node;
    }

    private int rowCount(int position) {
        int rows = 0;
        int end = Math.min(position, source.length);
        for (int i = 0; i < end; i++) {
            if (source[i] == '\n') {
                rows++;
            }
        }
        return rows;
    }

    private int rowCountOverSpan(int lookaheadId) {
        Subtree token = arena.get(lookaheadId);
        return Math.max(0, rowCount(token.padding() + arena.sizeOf(lookaheadId)) - rowCount(token.padding()));
    }

    private record ReduceAction(int symbol, int count, int dynamicPrecedence, int productionId) {
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
        GSSNode node = gss[newNode];
        node.errorCost = head.errorCost + subtreeErrorCost[subtreeId];
        node.nodeCount = head.nodeCount + subtreeNodeCount(subtreeId);
        node.dynPrec = head.dynPrec + subtreeDynPrec[subtreeId];
        node.linkNodes[0] = versions[version].head;
        node.linkSubtrees[0] = subtreeId;
        node.linkCount = 1;
        versions[version].head = newNode;
    }

    /**
     * Pushes the empty discontinuity link into the error state (C's NULL_SUBTREE
     * push in {@code ts_parser__handle_error}): position and accumulated values
     * are unchanged, but the link marks the version's error cost with the
     * recovery penalty and resets the since-error node baseline.
     */
    private void pushNullIntoErrorState(int version) {
        GSSNode head = gss[versions[version].head];
        int newNode = newGSSNode(Language.ERROR_STATE, head.position);
        GSSNode node = gss[newNode];
        node.errorCost = head.errorCost;
        node.nodeCount = head.nodeCount;
        node.dynPrec = head.dynPrec;
        node.errorDiscontinuity = true;
        node.linkNodes[0] = versions[version].head;
        node.linkSubtrees[0] = NO_LINK;
        node.linkCount = 1;
        versions[version].head = newNode;
        versions[version].nodeCountAtLastError = node.nodeCount;
    }

    /**
     * C {@code stack__subtree_node_count}: visible descendants, plus the node
     * itself when visible, plus intermediate ERROR_REPEAT wrappers — the
     * progress measure behind {@code node_count_since_error}.
     */
    private int subtreeNodeCount(int id) {
        Subtree node = arena.get(id);
        int count = subtreeVisibleDescendantCount(id);
        if (isSymbolVisible(node.symbol())) {
            count++;
        }
        if (node.symbol() == language.builtinErrorRepeatSymbol()) {
            count++;
        }
        return count;
    }

    private int subtreeVisibleDescendantCount(int id) {
        Subtree node = arena.get(id);
        int count = 0;
        for (int i = 0; i < node.childCount(); i++) {
            int child = node.child(i);
            Subtree cs = arena.get(child);
            if (cs.extra() != 0) {
                continue;
            }
            if (isSymbolVisible(cs.symbol())) {
                count++;
            } else if (cs.childCount() > 0) {
                count += subtreeVisibleDescendantCount(child);
            }
        }
        return count;
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

    private boolean isHalted(int version) {
        return versions[version].status == STATUS_HALTED;
    }

    private boolean isPaused(int version) {
        return versions[version].status == STATUS_PAUSED;
    }

    private void halt(int version) {
        versions[version].status = STATUS_HALTED;
    }

    /**
     * Marks the version as paused with its unprocessed lookahead retained (C
     * {@code ts_stack_pause}); condense resumes exactly one paused version into
     * error recovery.
     */
    private void pause(int version, PausedToken token) {
        versions[version].status = STATUS_PAUSED;
        versions[version].pausedToken = token;
        versions[version].nodeCountAtLastError = gss[versions[version].head].nodeCount;
    }

    private PausedToken resume(int version) {
        versions[version].status = STATUS_ACTIVE;
        PausedToken token = versions[version].pausedToken;
        versions[version].pausedToken = null;
        return token;
    }

    private void swapVersions(int i, int j) {
        Version tmp = versions[i];
        versions[i] = versions[j];
        versions[j] = tmp;
    }

    private int headPosition(int version) {
        return gss[versions[version].head].position;
    }

    /**
     * The C {@code ts_stack_error_cost}: accumulated subtree error costs plus
     * the recovery penalty while the version sits at an error discontinuity or
     * is paused.
     */
    private int stackErrorCost(int version) {
        GSSNode head = gss[versions[version].head];
        int cost = head.errorCost;
        if (isPaused(version) || head.errorDiscontinuity) {
            cost += ERROR_COST_PER_RECOVERY;
        }
        return cost;
    }

    private int nodeCountSinceError(int version) {
        Version v = versions[version];
        GSSNode head = gss[v.head];
        if (head.nodeCount < v.nodeCountAtLastError) {
            v.nodeCountAtLastError = head.nodeCount;
        }
        return head.nodeCount - v.nodeCountAtLastError;
    }

    /**
     * C {@code ts_stack_has_advanced_since_error}: true when the version's
     * spine contains a subtree with bytes past the last error mark.
     */
    private boolean hasAdvancedSinceError(int version) {
        Version v = versions[version];
        GSSNode node = gss[v.head];
        if (node.errorCost == 0) {
            return true;
        }
        while (node.linkCount > 0) {
            int sub = node.linkSubtrees[0];
            if (sub != NO_LINK) {
                Subtree tree = arena.get(sub);
                if (tree.padding() + arena.sizeOf(sub) > 0) {
                    return true;
                }
                if (node.nodeCount > v.nodeCountAtLastError && subtreeErrorCost[sub] == 0) {
                    node = gss[node.linkNodes[0]];
                    continue;
                }
            }
            break;
        }
        return false;
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

    /**
     * The C {@code ts_parser__condense_stack}: prune halted versions, order the
     * survivors by {@link ErrorStatus} comparison (removing, merging, or
     * swapping clearly-worse versions), truncate to {@link #MAX_VERSION_COUNT},
     * and — when the most promising version is paused — resume exactly one
     * paused version into error recovery (C {@code has_unpaused_version}).
     *
     * @return the minimum error cost among non-error versions (or
     *         {@link Integer#MAX_VALUE}), the main loop's termination gate.
     */
    private int condense() {
        boolean madeChanges = false;
        int minErrorCost = Integer.MAX_VALUE;
        for (int i = 0; i < versionCount; i++) {
            if (isHalted(i)) {
                removeVersion(i);
                i--;
                continue;
            }
            ErrorStatus statusI = versionStatus(i);
            if (!statusI.inError() && statusI.cost() < minErrorCost) {
                minErrorCost = statusI.cost();
            }
            for (int j = 0; j < i; j++) {
                ErrorStatus statusJ = versionStatus(j);
                switch (compareVersions(statusJ, statusI)) {
                    case TAKE_LEFT -> {
                        madeChanges = true;
                        removeVersion(i);
                        i--;
                        j = i;
                    }
                    case PREFER_LEFT, NONE -> {
                        if (merge(j, i)) {
                            madeChanges = true;
                            i--;
                            j = i;
                        }
                    }
                    case PREFER_RIGHT -> {
                        madeChanges = true;
                        if (merge(j, i)) {
                            i--;
                            j = i;
                        } else {
                            swapVersions(i, j);
                        }
                    }
                    case TAKE_RIGHT -> {
                        madeChanges = true;
                        removeVersion(j);
                        i--;
                        j--;
                    }
                }
            }
        }
        while (versionCount > MAX_VERSION_COUNT) {
            removeVersion(MAX_VERSION_COUNT);
            madeChanges = true;
        }
        if (versionCount > 0) {
            boolean hasUnpausedVersion = false;
            int n = versionCount;
            for (int i = 0; i < n; i++) {
                if (i >= versionCount) {
                    break;
                }
                if (isPaused(i)) {
                    if (!hasUnpausedVersion && acceptCount < MAX_VERSION_COUNT) {
                        minErrorCost = stackErrorCost(i);
                        PausedToken lookahead = resume(i);
                        handleError(i, lookahead);
                        hasUnpausedVersion = true;
                    } else {
                        removeVersion(i);
                        madeChanges = true;
                        i--;
                        n--;
                    }
                } else {
                    hasUnpausedVersion = true;
                }
            }
        }
        return minErrorCost;
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
        int errorCost;
        int nodeCount;
        int dynPrec;
        boolean errorDiscontinuity;
    }

    /**
     * The lookahead a paused version retained across condense rounds: the raw
     * token facts, materialized into an arena leaf only when recovery actually
     * consumes it.
     */
    private record PausedToken(int symbol, int start, int end, boolean keyword, int errorChar) {

        static PausedToken of(Lexer.Token token, int errorChar) {
            return new PausedToken(token.symbol(), token.startOffset(), token.endOffset(),
                    token.keyword(), errorChar);
        }

        boolean isEof() {
            return symbol == Lexer.END_SYMBOL;
        }
    }

    private record SummaryEntry(int position, int depth, int state) {
    }

    /**
     * C {@code ErrorStatus}: the comparison key for stack versions during
     * condense and recovery-cost gating.
     */
    private record ErrorStatus(int cost, int nodeCount, int dynamicPrecedence, boolean inError) {
    }

    private enum ErrorComparison {
        TAKE_LEFT, PREFER_LEFT, NONE, PREFER_RIGHT, TAKE_RIGHT
    }

    private static final class Version {
        int head;
        int status;
        PausedToken pausedToken;
        int nodeCountAtLastError;
        List<SummaryEntry> summary;

        Version(int head, int status) {
            this.head = head;
            this.status = status;
        }
    }

    // ------------------------------------------------------------------
    // Side tables
    // ------------------------------------------------------------------

    private void recordSubtreeSize(int id, int size, int dynPrec, int errorCost) {
        if (id >= subtreeSize.length) {
            int cap = Math.max(subtreeSize.length * 2, id + 1);
            subtreeSize = java.util.Arrays.copyOf(subtreeSize, cap);
            subtreeDynPrec = java.util.Arrays.copyOf(subtreeDynPrec, cap);
            subtreeErrorCost = java.util.Arrays.copyOf(subtreeErrorCost, cap);
        }
        subtreeSize[id] = size;
        subtreeDynPrec[id] = dynPrec;
        subtreeErrorCost[id] = errorCost;
    }

    private TreeSitterException parseError(Lexer.Token token, int state, String reason) {
        return new TreeSitterException("parse error at byte offset " + token.startOffset()
                + " (state " + state + "): " + reason);
    }
}