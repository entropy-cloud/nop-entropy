package io.nop.treesitter.subtree;

import io.nop.treesitter.TreeSitterException;

import java.util.Arrays;

/**
 * Compact storage for parse-tree nodes, addressed by integer ids.
 *
 * <p>Nodes live in parallel {@code int[]} columns (one per {@link Subtree} field)
 * instead of per-node heap objects, keeping the hot parser path allocation-free.
 * Ids are stable positions in the columns; freed ids are pushed onto a free list
 * and reused by the next {@link #allocate}. Backing arrays grow geometrically and
 * preserve all previously stored contents on growth.</p>
 *
 * <p>Id {@code 0} is a valid node id; {@link Subtree#NO_ID} ({@code -1}) is never
 * returned. Accessing an invalid, never-allocated, or already-freed id throws
 * {@link TreeSitterException} rather than returning stale data.</p>
 */
public final class SubtreeArena {

    private static final int INITIAL_CAPACITY = 4;
    private static final int GROWTH_FACTOR = 2;

    private int[] state;
    private int[] symbol;
    private int[] child0;
    private int[] child1;
    private int[] child2;
    private int[] child3;
    private int[] child4;
    private int[] child5;
    private int[] child6;
    private int[] child7;
    private int[] extra;
    private int[] padding;
    private int[] nodeSize;
    private boolean[] missing;
    private int[] lookaheadChar;
    private boolean[] live;
    private int[] parent;
    private int[] freeList;
    private Subtree[] cache;
    private int size;
    private int freeCount;

    public SubtreeArena() {
        this(INITIAL_CAPACITY);
    }

    /**
     * Pre-reserves capacity for {@code expectedNodes} so large parses skip the
     * geometric growth chain entirely (every doubling copies all columns and
     * abandons the previous arrays). Small parses keep the tiny initial
     * footprint; the estimate should come from a per-language nodes-per-byte
     * observation, capped so a mis-estimate cannot spike the heap.
     */
    public SubtreeArena(int expectedNodes) {
        int cap = Math.max(INITIAL_CAPACITY, expectedNodes);
        state = new int[cap];
        symbol = new int[cap];
        child0 = filledColumn(cap);
        child1 = filledColumn(cap);
        child2 = filledColumn(cap);
        child3 = filledColumn(cap);
        child4 = filledColumn(cap);
        child5 = filledColumn(cap);
        child6 = filledColumn(cap);
        child7 = filledColumn(cap);
        extra = new int[cap];
        padding = new int[cap];
        nodeSize = new int[cap];
        missing = new boolean[cap];
        lookaheadChar = new int[cap];
        live = new boolean[cap];
        parent = filledColumn(cap);
        freeList = new int[Math.min(cap, 1024)];
        cache = new Subtree[cap];
    }

    private static int[] filledColumn(int cap) {
        int[] column = new int[cap];
        Arrays.fill(column, Subtree.NO_ID);
        return column;
    }

    /**
     * Allocates a node with the given field values and returns its id.
     *
     * <p>Every child id must currently be live in this arena; a freed or never
     * allocated child raises {@link TreeSitterException}. At most
     * {@link Subtree#MAX_CHILDREN} children are accepted. The first free-list slot
     * is reused when one exists; otherwise the arena grows geometrically.</p>
     */
    public int allocate(int stateValue, int symbolValue, int extraValue, int paddingValue,
                        int... children) {
        return allocateChildren(stateValue, symbolValue, extraValue, paddingValue, children,
                children.length);
    }

    /**
     * Same as {@link #allocate(int, int, int, int, int...)} for a prefix of a
     * reusable scratch array — reads only {@code children[0..count)}.
     */
    public int allocateChildren(int stateValue, int symbolValue, int extraValue, int paddingValue,
                                int[] children, int count) {
        if (count > Subtree.MAX_CHILDREN) {
            throw new IllegalArgumentException(
                    "a subtree may have at most " + Subtree.MAX_CHILDREN + " children, got "
                            + count);
        }
        for (int i = 0; i < count; i++) {
            checkLive(children[i], "allocate");
        }

        int id;
        if (freeCount > 0) {
            id = freeList[--freeCount];
        } else {
            if (size == state.length) {
                grow();
            }
            id = size++;
        }

        state[id] = stateValue;
        symbol[id] = symbolValue;
        child0[id] = count > 0 ? children[0] : Subtree.NO_ID;
        child1[id] = count > 1 ? children[1] : Subtree.NO_ID;
        child2[id] = count > 2 ? children[2] : Subtree.NO_ID;
        child3[id] = count > 3 ? children[3] : Subtree.NO_ID;
        child4[id] = count > 4 ? children[4] : Subtree.NO_ID;
        child5[id] = count > 5 ? children[5] : Subtree.NO_ID;
        child6[id] = count > 6 ? children[6] : Subtree.NO_ID;
        child7[id] = count > 7 ? children[7] : Subtree.NO_ID;
        extra[id] = extraValue;
        padding[id] = paddingValue;
        nodeSize[id] = 0;
        missing[id] = false;
        lookaheadChar[id] = 0;
        live[id] = true;
        cache[id] = new Subtree(stateValue, symbolValue,
                child0[id], child1[id], child2[id], child3[id],
                child4[id], child5[id], child6[id], child7[id],
                extraValue, paddingValue);
        for (int i = 0; i < count; i++) {
            parent[children[i]] = id;
        }
        return id;
    }

    /**
     * Allocates a zero-width missing-token leaf (C
     * {@code ts_subtree_new_missing_leaf}): the symbol the parser expected, at
     * the current position. The missing mark travels with the node through
     * snapshots and renders as {@code (MISSING symbol)}.
     */
    public int allocateMissing(int symbol, int padding) {
        int id = allocate(0, symbol, 0, padding);
        missing[id] = true;
        return id;
    }

    /**
     * Allocates the lexer's error leaf (C {@code ts_subtree_new_error}): the
     * builtin error symbol spanning the skipped bytes, carrying the first
     * unrecognized character for {@code (UNEXPECTED 'c')} rendering.
     */
    public int allocateErrorLeaf(int symbol, int padding, int firstLookaheadChar) {
        int id = allocate(0, symbol, 0, padding);
        lookaheadChar[id] = firstLookaheadChar;
        return id;
    }

    /**
     * True when the node is a zero-width missing-token leaf.
     */
    public boolean isMissing(int id) {
        checkLive(id, "isMissing");
        return missing[id];
    }

    /**
     * The first unrecognized character of a lexer error leaf, or 0.
     */
    public int lookaheadCharOf(int id) {
        checkLive(id, "lookaheadCharOf");
        return lookaheadChar[id];
    }

    /**
     * Sets the byte length of a node (C {@code ts_subtree_size}); 0 by default
     * until the parser records it. The node's byte range is
     * {@code [padding, padding + size)}.
     */
    public void setSize(int id, int byteSize) {
        checkLive(id, "setSize");
        nodeSize[id] = byteSize;
    }

    /**
     * Byte length of a live node (C {@code ts_subtree_size}).
     */
    public int sizeOf(int id) {
        checkLive(id, "sizeOf");
        return nodeSize[id];
    }

    /**
     * Releases a node id for reuse. Subsequent {@link #get} on it throws;
     * the next {@link #allocate} reuses the slot without growing the arena.
     */
    public void free(int id) {
        checkLive(id, "free");
        live[id] = false;
        if (freeCount == freeList.length) {
            freeList = Arrays.copyOf(freeList, Math.max(INITIAL_CAPACITY, freeList.length * GROWTH_FACTOR));
        }
        freeList[freeCount++] = id;
    }

    /**
     * Returns the value snapshot for a live node id.
     */
    public Subtree get(int id) {
        checkLive(id, "get");
        return cache[id];
    }

    /**
     * Returns the id of the node that owns {@code id} as a child, or
     * {@link Subtree#NO_ID} if the node is a root.
     */
    public int parentOf(int id) {
        checkLive(id, "parentOf");
        return parent[id];
    }

    /**
     * True when {@code id} refers to a currently live node.
     */
    public boolean isLive(int id) {
        return id >= 0 && id < size && live[id];
    }

    /**
     * Number of distinct ids ever handed out (including ones currently freed).
     */
    public int size() {
        return size;
    }

    /**
     * Current backing-column capacity; grows geometrically on demand.
     */
    public int capacity() {
        return state.length;
    }

    private void grow() {
        int newCapacity = Math.max(INITIAL_CAPACITY, state.length * GROWTH_FACTOR);
        state = Arrays.copyOf(state, newCapacity);
        symbol = Arrays.copyOf(symbol, newCapacity);
        child0 = growChildColumn(child0, newCapacity);
        child1 = growChildColumn(child1, newCapacity);
        child2 = growChildColumn(child2, newCapacity);
        child3 = growChildColumn(child3, newCapacity);
        child4 = growChildColumn(child4, newCapacity);
        child5 = growChildColumn(child5, newCapacity);
        child6 = growChildColumn(child6, newCapacity);
        child7 = growChildColumn(child7, newCapacity);
        extra = Arrays.copyOf(extra, newCapacity);
        padding = Arrays.copyOf(padding, newCapacity);
        nodeSize = Arrays.copyOf(nodeSize, newCapacity);
        missing = Arrays.copyOf(missing, newCapacity);
        lookaheadChar = Arrays.copyOf(lookaheadChar, newCapacity);
        live = Arrays.copyOf(live, newCapacity);
        parent = growChildColumn(parent, newCapacity);
        cache = Arrays.copyOf(cache, newCapacity);
    }

    private static int[] growChildColumn(int[] column, int newCapacity) {
        int[] grown = Arrays.copyOf(column, newCapacity);
        Arrays.fill(grown, column.length, newCapacity, Subtree.NO_ID);
        return grown;
    }

    private void checkLive(int id, String op) {
        if (id < 0 || id >= size || !live[id]) {
            throw new TreeSitterException("cannot " + op + " subtree id " + id + ": not a live node");
        }
    }
}