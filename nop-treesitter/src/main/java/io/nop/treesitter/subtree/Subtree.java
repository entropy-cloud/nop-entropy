package io.nop.treesitter.subtree;

/**
 * Immutable value snapshot of a single parse-tree node.
 *
 * <p>Holds only {@code int} fields so it can be cheaply materialised from the
 * arena's parallel {@code int[]} columns on {@link SubtreeArena#get(int)}; the
 * hot parser path addresses nodes by id and never allocates a per-node heap
 * object.</p>
 *
 * <p>Children are stored in the dense slots {@code child0}..{@code child3}; a
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
        int extra,
        int padding) {

    /**
     * Maximum number of children stored inline in a subtree value.
     */
    public static final int MAX_CHILDREN = 4;

    /**
     * Sentinel for an absent child / absent parent link. The arena never assigns
     * this value as a live node id.
     */
    public static final int NO_ID = -1;

    public Subtree {
        if (!isDense(child0, child1, child2, child3)) {
            throw new IllegalArgumentException(
                    "child slots must be dense: a NO_ID slot may not be followed by a child id");
        }
    }

    private static boolean isDense(int c0, int c1, int c2, int c3) {
        if (c0 == NO_ID) {
            return c1 == NO_ID && c2 == NO_ID && c3 == NO_ID;
        }
        if (c1 == NO_ID) {
            return c2 == NO_ID && c3 == NO_ID;
        }
        return c2 != NO_ID || c3 == NO_ID;
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
        return child3 == NO_ID ? 3 : MAX_CHILDREN;
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
            default -> child3;
        };
    }
}
