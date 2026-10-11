package io.nop.bytecode.cli;

/**
 * Rule IDs emitted by the bytecode discovery channel, namespaced per analysis family
 * ({@code nullflow/}, {@code resources/}) — the channel reports the path-sensitive face only (substrate ADR / gap-ledger G1); the
 * pattern face (throw-null / equals-null / ...) belongs to the source lane, and any overlap
 * on trivial paths (e.g. unguarded {@code x.equals(y)} vs the source lane's equals-null rule)
 * is adjudicated in the Wave 4 parallel-comparison item, not by silently dropping findings.
 */
public final class RuleIds {
    public static final String MAY_NULL_DEREF = "nullflow/may-null-deref";
    public static final String UNCLOSED_RESOURCE = "resources/unclosed-resource";

    private RuleIds() {
    }
}
