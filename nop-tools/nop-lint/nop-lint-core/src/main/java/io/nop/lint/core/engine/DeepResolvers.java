package io.nop.lint.core.engine;

import io.nop.lint.core.semantic.DataflowResolver;
import io.nop.lint.core.semantic.MetricsResolver;
import io.nop.lint.core.semantic.ScopeResolver;
import io.nop.lint.core.semantic.SemanticResolver;

/**
 * The run family's deep-analyzer providers (roadmap items 32/33/34): the
 * engine-constructed, reusable half of the resolver wiring. Per-lint-call
 * state (the file path) joins at {@code lint} time — this record is built
 * once per engine and shared across files (plan R1 C-2: the file path is
 * per-call, never carried here).
 *
 * <p>Null members mean the run serves no such capability — rules requiring
 * it degrade at the gate (fail-closed), never run on faked answers.</p>
 */
public record DeepResolvers(MetricsResolver metrics, ScopeResolver scope,
                            SemanticResolver semantic, DataflowResolver dataflow) {

    /**
     * A run with no deep resolvers at all: every deep-analyzer rule
     * degrades (the CLI's shape when nothing is discovered).
     */
    public static final DeepResolvers NONE = new DeepResolvers(null, null, null, null);

    /**
     * True when the given resolver is wired and its availability probe
     * passes — the gate's and the ladder's "open" judgment per capability.
     */
    public static boolean live(MetricsResolver resolver) {
        return resolver != null && resolver.isAvailable();
    }

    public static boolean live(ScopeResolver resolver) {
        return resolver != null && resolver.isAvailable();
    }

    public static boolean live(SemanticResolver resolver) {
        return resolver != null && resolver.isAvailable();
    }

    public static boolean live(DataflowResolver resolver) {
        return resolver != null && resolver.isAvailable();
    }
}
