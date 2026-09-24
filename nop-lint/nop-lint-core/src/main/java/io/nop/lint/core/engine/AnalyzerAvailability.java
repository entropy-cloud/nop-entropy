package io.nop.lint.core.engine;

/**
 * Availability probes for the analyzer-backed capabilities a run can serve
 * (roadmap item 31, plan Decision 6). <strong>Since roadmap items 32–34 the
 * engine no longer gates any capability through probes</strong>: all four
 * deep-only capabilities (L3/L4/SCOPE/METRICS) are served through their own
 * resolvers in {@link DeepResolvers} and gate via provider availability.
 * This interface is retained as the extension point for future analyzer
 * kinds that do not fit the position-keyed resolver shape.
 *
 * <p>Contract (unchanged for future consumers): probes are cheap and
 * side-effect free — they must never start a backend (same discipline as
 * {@code TypeResolver.isAvailable()}, design 11 §3 lazy wiring).</p>
 */
public interface AnalyzerAvailability {

    /**
     * True when this run can serve queries of {@code capability}. Called
     * only for capabilities the profile's ceiling declares; must be cheap
     * and must not initialize the analyzer backend.
     */
    boolean isLive(LintCapability capability);
}
