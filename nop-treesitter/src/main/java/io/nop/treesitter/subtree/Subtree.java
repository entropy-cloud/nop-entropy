package io.nop.treesitter.subtree;

/**
 * Immutable value snapshot of a single parse-tree node.
 *
 * <p>Holds only {@code int} fields so it can be cheaply materialised from the
 * arena's parallel {@code int[]} columns on {@link SubtreeArena#get(int)}; the
 * hot parser path addresses nodes by id and never allocates a per-node heap
 * object.</p>
 *
 * <p>Children are stored in the dense slots {@code child0}..{@code child7}; a
 * slot equal to {@link #NO_ID} marks "no child at this position" and children
 * must be packed from {@code child0} onward (no gaps). This constructor enforces
 * that density invariant so an arena node can never carry a ragged child list.</p>
 */
public record Subtree(
        int state,
        int symbol,
        int child0,
        int child1,
        int child2,
        int child3,
        int child4,
        int child5,
        int child6,
        int child7,
        int extra,
        int padding) {

    /**
     * Maximum number of children stored inline in a subtree value.
     */
    public static final int MAX_CHILDREN = 8;

    /**
     * Sentinel for an absent child / absent parent link. The arena never assigns
     * this value as a live node id.
     */
    public static final int NO_ID = -1;

    public Subtree {
        if (!isDense(child0, child1, child2, child3, child4, child5, child6, child7)) {
            throw new IllegalArgumentException(
                    "child slots must be dense: a NO_ID slot may not be followed by a child id");
        }
    }

    private static boolean isDense(int c0, int c1, int c2, int c3, int c4, int c5, int c6, int c7) {
        if (c0 == NO_ID) {
            return c1 == NO_ID && c2 == NO_ID && c3 == NO_ID && c4 == NO_ID
                    && c5 == NO_ID && c6 == NO_ID && c7 == NO_ID;
        }
        if (c1 == NO_ID) {
            return c2 == NO_ID && c3 == NO_ID && c4 == NO_ID && c5 == NO_ID
                    && c6 == NO_ID && c7 == NO_ID;
        }
        if (c2 == NO_ID) {
            return c3 == NO_ID && c4 == NO_ID && c5 == NO_ID && c6 == NO_ID && c7 == NO_ID;
        }
        if (c3 == NO_ID) {
            return c4 == NO_ID && c5 == NO_ID && c6 == NO_ID && c7 == NO_ID;
        }
        if (c4 == NO_ID) {
            return c5 == NO_ID && c6 == NO_ID && c7 == NO_ID;
        }
        if (c5 == NO_ID) {
            return c6 == NO_ID && c7 == NO_ID;
        }
        return c6 != NO_ID || c7 == NO_ID;
    }

    /**
     * Number of children present, derived from the dense child slots.
     */
    public int childCount() {
        if (child0 == NO_ID) {
            return 0;
        }
        if (child1 == NO_ID) {
            return 1;
        }
        if (child2 == NO_ID) {
            return 2;
        }
        if (child3 == NO_ID) {
            return 3;
        }
        if (child4 == NO_ID) {
            return 4;
        }
        if (child5 == NO_ID) {
            return 5;
        }
        if (child6 == NO_ID) {
            return 6;
        }
        return child7 == NO_ID ? 7 : MAX_CHILDREN;
    }

    /**
     * Returns the id of the child at {@code index}, or throws if out of range.
     */
    public int child(int index) {
        int count = childCount();
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException(
                    "child index " + index + " out of range [0," + count + ")");
        }
        return switch (index) {
            case 0 -> child0;
            case 1 -> child1;
            case 2 -> child2;
            case 3 -> child3;
            case 4 -> child4;
            case 5 -> child5;
            case 6 -> child6;
            default -> child7;
        };
    }
}