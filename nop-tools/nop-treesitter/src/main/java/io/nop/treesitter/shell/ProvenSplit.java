package io.nop.treesitter.shell;

/**
 * The two-way outcome of {@link ShellAnalysis#splitProven}: either a
 * {@link ProvenCommandSequence} or an {@link UnprovenScript}.
 *
 * <p>Sealed with exactly these two cases so that a consumer's {@code switch} is
 * exhaustive: adding a case later becomes a compile error at every consumption
 * site, which is the point. A caller cannot reach an "empty sequence" by ignoring
 * a branch.</p>
 */
public sealed interface ProvenSplit permits ProvenCommandSequence, UnprovenScript {

    /** Whether this split is a proof. */
    default boolean isProven() {
        return this instanceof ProvenCommandSequence;
    }
}
