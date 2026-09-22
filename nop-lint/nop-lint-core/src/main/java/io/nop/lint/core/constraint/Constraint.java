package io.nop.lint.core.constraint;

/**
 * One compiled, executable constraint over a match (design 01 §5
 * pseudo-code contract, adjudicated filter form — plan 2026-09-22-1045-3):
 * a pure predicate with no diagnostic collector — a constraint that does not
 * hold filters the match out (counted, never silently dropped); it never
 * produces a diagnostic itself. A match is reported only when every
 * constraint of its rule holds (design 01 §3.2 Decision).
 *
 * <p>Instances are produced only by {@link Constraints#compile}; evaluation
 * must not throw for ordinary "does not hold" outcomes — only for shapes the
 * gate layer should have prevented (typeOf without L2).</p>
 */
public interface Constraint {

    /**
     * True when this constraint holds for the match described by
     * {@code ctx}.
     */
    boolean holds(ConstraintContext ctx);
}
