package io.nop.ai.api.secure;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a {@code Default*} component ships secure-by-default
 * configuration (deny-by-default posture, non-permissive fallbacks, non-zero
 * timeouts, bounded IO abstractions, ...).
 *
 * <p>This is a <b>declaration-only marker</b> consumed by the invariant gate
 * {@code TestInvariantGate1SecureDefault} (INV-1, see
 * {@code ai-dev/audits/nop-ai-invariants/invariant-catalog.md} §2 INV-1). The
 * gate verifies <b>declaration presence and category attributes only</b>; it
 * does <b>not</b> verify behaviour semantics. Behaviour-level verification is
 * the responsibility of the invariant-loop audit (I2) wiring spot-checks and
 * the committed behaviour tests of the individual components (e.g.
 * {@code TestSecureByDefault}, {@code TestLayer23SecureDefaults}).
 *
 * <p>Contract: every {@code Default*} class in {@code src/main} (per the I0
 * target set table §3.1) must either carry this annotation or be registered in
 * the known-gaps list {@code gate-gaps.yaml} (family {@code gate-1-default-secure})
 * until the gap is fixed (I4) — a class with neither is a gate failure.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SecureDefault {
}
