package io.nop.ai.agent.guardrail.test;

/**
 * Strategy-layer payload transform (design {@code guardrail-contract.md}
 * §增量 1, Decision E). A transform decorates an {@link AttackCase} to produce a
 * transformed variant (e.g. base64-encoded or crescendo-escalated payload) that
 * exercises the guardrail's robustness against obfuscation. The
 * {@code expectedBehavior} is inherited unchanged: a transform does not change
 * the attack nature, so an attack that should be blocked must still be blocked
 * after transformation.
 *
 * <p>Built-in transforms: {@link Base64AttackTransform}, {@link CrescendoAttackTransform}.
 */
public interface AttackTransform {

    /**
     * Stable transform name (e.g. {@code "base64"}). Used as the variant id
     * suffix and the {@link AttackCase#getTransform()} marker.
     */
    String name();

    /**
     * Apply this transform to the source case's payload, returning a derived
     * variant. The returned case carries {@code transform == name()}.
     *
     * @param source a non-null base case (transform must be null on the source)
     */
    AttackCase apply(AttackCase source);
}
