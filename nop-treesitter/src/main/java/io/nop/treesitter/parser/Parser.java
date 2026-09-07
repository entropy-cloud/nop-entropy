package io.nop.treesitter.parser;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.lexer.Lexer;
import io.nop.treesitter.subtree.Subtree;
import io.nop.treesitter.subtree.SubtreeArena;

import java.util.Arrays;

/**
 * LR(1) parser driving the language's parse table with a single linear stack.
 *
 * <p>Semantics mirror the tree-sitter C runtime: the stack holds completed
 * subtrees (ids into a {@link SubtreeArena}) plus one state per entry; the
 * initial state is {@link Language#INITIAL_STATE}. For each lookahead token the
 * table cell for the current state yields an action group; REDUCE actions
 * execute in order (re-evaluating the same token at the new state), SHIFT
 * consumes the token, SHIFT_EXTRA pushes the token with the state unchanged
 * and re-lexes, and ACCEPT_INPUT finishes the parse. Repeated rules (aux
 * symbols) reduce to nested hidden nodes exactly like the C runtime's "repeat"
 * handling.</p>
 *
 * <p>Extra tokens (comments) sit on the stack between ordinary entries; a
 * reduce pops {@code child_count} <em>non-extra</em> entries, sweeping any
 * extras in between into the new node's children (and re-pushing extras that
 * end up on top), which is how comments become children of the enclosing
 * object / array / document. A node whose children would exceed the arena's
 * inline capacity of {@link Subtree#MAX_CHILDREN} is split into a chain of
 * invisible container nodes (reserved symbol id {@code symbolCount()}) so the
 * visible tree shape is unchanged.</p>
 *
 * <p>No GLR, no error recovery: an unexpected token raises
 * {@link TreeSitterException}, and a table action the parser does not
 * implement raises {@link UnsupportedOperationException} naming the action —
 * nothing is skipped silently.</p>
 */
public final class Parser {

    private static final int MAX_INLINE_CHILDREN = Subtree.MAX_CHILDREN;

    private Parser() {
    }

    /**
     * Parses {@code source} with the given language into {@code arena} and
     * returns the id of the root subtree.
     */
    public static int parse(Language language, SubtreeArena arena, byte[] source) {
        Stack stack = new Stack();
        int state = Language.INITIAL_STATE;
        int position = 0;
        for (;;) {
            Lexer.Token token = Lexer.next(language, source, position, language.lexState(state));
            int symbol = token.symbol();
            for (;;) {
                int cell = language.tableCell(state, symbol);
                if (cell == 0) {
                    throw parseError(language, token, state, "no table action for token '"
                            + language.symbolName(symbol) + "'");
                }
                Language.ActionGroup group = language.actionGroup(cell);
                if (group == null) {
                    throw new TreeSitterException("table cell " + cell + " for state " + state
                            + ", symbol '" + language.symbolName(symbol) + "' names an undeclared action group");
                }
                boolean shifted = false;
                for (Language.Action action : group.actions()) {
                    switch (action.type()) {
                        case Language.Action.SHIFT -> {
                            if (action.repetition()) {
                                continue;
                            }
                            if (action.extra()) {
                                int id = arena.allocate(0, symbol, 1, token.startOffset());
                                stack.push(id, state);
                                shifted = true;
                            } else {
                                int id = arena.allocate(0, symbol, 0, token.startOffset());
                                stack.push(id, action.state());
                                state = action.state();
                                shifted = true;
                            }
                        }
                        case Language.Action.REDUCE -> {
                            reduce(language, arena, stack, action);
                            state = stack.topState();
                        }
                        case Language.Action.ACCEPT -> {
                            return accept(language, arena, stack, state, token);
                        }
                        case Language.Action.RECOVER -> throw new UnsupportedOperationException(
                                "parse action type 'recover' is not implemented (error recovery is roadmap item 11)");
                        default -> throw new UnsupportedOperationException(
                                "unimplemented parse action type: " + action.type());
                    }
                    if (shifted) {
                        break;
                    }
                }
                if (shifted) {
                    position = token.endOffset();
                    break;
                }
            }
        }
    }

    private static void reduce(Language language, SubtreeArena arena, Stack stack,
                               Language.Action action) {
        int count = action.childCount();
        int[] collected = new int[count + 16];
        int n = 0;
        int nonExtra = 0;
        while (nonExtra < count) {
            if (stack.isEmpty()) {
                throw new TreeSitterException("parse error: reduce " + language.symbolName(action.symbol())
                        + " with " + count + " children, but the stack ran out of entries");
            }
            int id = stack.pop();
            collected[n++] = id;
            if (arena.get(id).extra() == 0) {
                nonExtra++;
            }
        }

        // Extras at the top of the popped range are trailing extras: they are
        // re-pushed after the parent node instead of becoming its children.
        int trailing = 0;
        while (trailing < n && arena.get(collected[trailing]).extra() != 0) {
            trailing++;
        }

        int childCount = n - trailing;
        int[] children = new int[childCount];
        for (int i = 0; i < childCount; i++) {
            children[i] = collected[n - 1 - i];
        }
        int node = buildNode(language, arena, action.symbol(), children, childCount);

        int belowState = stack.topState();
        int nextState = language.tableCell(belowState, action.symbol());
        if (nextState == 0) {
            throw new TreeSitterException("parse error: no goto for " + language.symbolName(action.symbol())
                    + " from state " + belowState);
        }
        stack.push(node, nextState);
        for (int j = trailing - 1; j >= 0; j--) {
            stack.push(collected[j], nextState);
        }
    }

    private static int accept(Language language, SubtreeArena arena, Stack stack, int state,
                              Lexer.Token endToken) {
        int endId = arena.allocate(0, Lexer.END_SYMBOL, 1, endToken.startOffset());
        stack.push(endId, state);
        int[] entries = stack.popAll();

        int rootIndex = -1;
        for (int i = entries.length - 1; i >= 0; i--) {
            if (arena.get(entries[i]).extra() == 0) {
                rootIndex = i;
                break;
            }
        }
        if (rootIndex < 0) {
            throw new TreeSitterException("parse error: accept reached without a completed root node");
        }
        int docId = entries[rootIndex];
        Subtree doc = arena.get(docId);
        int[] children = new int[entries.length - 1 + doc.childCount()];
        int p = 0;
        for (int i = 0; i < rootIndex; i++) {
            children[p++] = entries[i];
        }
        for (int i = 0; i < doc.childCount(); i++) {
            children[p++] = doc.child(i);
        }
        for (int i = rootIndex + 1; i < entries.length; i++) {
            children[p++] = entries[i];
        }
        int rootId = arena.allocate(0, doc.symbol(), 0, 0, children);
        arena.free(docId);
        return rootId;
    }

    private static int buildNode(Language language, SubtreeArena arena, int symbol,
                                 int[] children, int childCount) {
        if (childCount <= MAX_INLINE_CHILDREN) {
            return arena.allocate(0, symbol, 0, 0,
                    Arrays.copyOf(children, childCount));
        }
        int rest = buildChain(arena, language.symbolCount(), children, 3, childCount);
        return arena.allocate(0, symbol, 0, 0,
                children[0], children[1], children[2], rest);
    }

    private static int buildChain(SubtreeArena arena, int containerSymbol,
                                  int[] children, int from, int to) {
        int n = to - from;
        if (n <= 3) {
            return arena.allocate(0, containerSymbol, 0, 0,
                    Arrays.copyOfRange(children, from, to));
        }
        int rest = buildChain(arena, containerSymbol, children, from + 3, to);
        return arena.allocate(0, containerSymbol, 0, 0,
                children[from], children[from + 1], children[from + 2], rest);
    }

    private static TreeSitterException parseError(Language language, Lexer.Token token, int state, String reason) {
        return new TreeSitterException("parse error at byte offset " + token.startOffset()
                + " (state " + state + "): " + reason);
    }

    /**
     * Linear parse stack: parallel id/state arrays plus a sentinel base entry
     * holding the initial state.
     */
    private static final class Stack {

        private int[] ids = new int[8];
        private int[] states = new int[8];
        private int top;

        Stack() {
            states[0] = Language.INITIAL_STATE;
            ids[0] = Subtree.NO_ID;
            top = 0;
        }

        void push(int id, int state) {
            if (top + 1 >= ids.length) {
                ids = Arrays.copyOf(ids, ids.length * 2);
                states = Arrays.copyOf(states, states.length * 2);
            }
            top++;
            ids[top] = id;
            states[top] = state;
        }

        int pop() {
            if (top <= 0) {
                return Subtree.NO_ID;
            }
            return ids[top--];
        }

        int topState() {
            return states[top];
        }

        boolean isEmpty() {
            return top == 0;
        }

        /**
         * Returns all pushed subtree ids in stack order (bottom to top) and
         * resets the stack to its base entry.
         */
        int[] popAll() {
            int[] result = Arrays.copyOfRange(ids, 1, top + 1);
            top = 0;
            return result;
        }
    }
}