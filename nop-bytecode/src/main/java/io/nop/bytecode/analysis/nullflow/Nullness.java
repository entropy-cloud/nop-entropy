package io.nop.bytecode.analysis.nullflow;

/**
 * Nullness lattice for the null-flow analysis.
 *
 * <p>{@link #TOP} is a slot placeholder for the second slot of category-2 values — it is a
 * stack-width marker, not a semantic nullness degree.
 */
public enum Nullness {
    NONNULL, MAYNULL, NULL, TOP;

    /** Lattice merge; null = "no information yet" (first touch). */
    public static Nullness merge(Nullness a, Nullness b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a == TOP) return b == null ? TOP : b;
        if (b == TOP) return a;
        if (a == b) return a;
        return MAYNULL;
    }
}
