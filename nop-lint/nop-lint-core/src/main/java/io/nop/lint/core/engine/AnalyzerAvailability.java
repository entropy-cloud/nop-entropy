package io.nop.lint.core.engine;

/**
 * Availability probes for the analyzer-backed capabilities a run can serve
 * (roadmap item 31, plan Decision 6): one probe per deep analyzer capability
 * (L3/L4/SCOPE/METRICS). The engine consults a probe only for capabilities
 * inside the profile's ceiling — a rule whose requirement the run cannot
 * serve degrades (counted, logged, no diagnostics), never runs on a faked
 * answer.
 *
 * <p>Contract: probes are cheap and side-effect free — they must never
 * start a backend (same discipline as {@code TypeResolver.isAvailable()},
 * design 11 §3 lazy wiring). The capability's name is the id the engine
 * records when a probe answer keeps a rule from running. Absent probes
 * (nothing wired for a capability) answer "not live" by construction.</p>
 */
public interface AnalyzerAvailability {

    /**
     * True when this run can serve queries of {@code capability}. Called
     * only for capabilities the profile's ceiling declares; must be cheap
     * and must not initialize the analyzer backend.
     */
    boolean isLive(LintCapability capability);
}
