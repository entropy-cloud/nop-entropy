package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.guardrail.GuardrailDirection;

import java.util.Objects;

/**
 * A single guardrail test case (design {@code guardrail-contract.md} §增量 1,
 * Decision B). Immutable value object.
 *
 * <p>Base cases (declared in the YAML corpus) have {@code transform == null}.
 * {@link AttackTransform} variants are derived copies with a transformed
 * {@code payload} and a non-null {@code transform} marker; their
 * {@code expectedBehavior} is inherited unchanged (a transform does not change
 * the attack nature).
 */
public final class AttackCase {

    private final String id;
    private final String category;
    private final String threatClass;
    private final String payload;
    private final GuardrailDirection direction;
    private final ExpectedBehavior expectedBehavior;
    private final String description;
    private final String transform;

    public AttackCase(String id, String category, String threatClass, String payload,
                      GuardrailDirection direction, ExpectedBehavior expectedBehavior,
                      String description, String transform) {
        this.id = id;
        this.category = category;
        this.threatClass = threatClass;
        this.payload = payload;
        this.direction = direction;
        this.expectedBehavior = expectedBehavior;
        this.description = description;
        this.transform = transform;
    }

    public AttackCase(String id, String category, String threatClass, String payload,
                      ExpectedBehavior expectedBehavior) {
        this(id, category, threatClass, payload, GuardrailDirection.INPUT, expectedBehavior, null, null);
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getThreatClass() {
        return threatClass;
    }

    public String getPayload() {
        return payload;
    }

    public GuardrailDirection getDirection() {
        return direction;
    }

    public ExpectedBehavior getExpectedBehavior() {
        return expectedBehavior;
    }

    public String getDescription() {
        return description;
    }

    public String getTransform() {
        return transform;
    }

    public boolean isTransformed() {
        return transform != null;
    }

    /**
     * Return a copy of this case with a transformed payload and the given
     * transform marker. {@code expectedBehavior}, {@code category},
     * {@code threatClass} and {@code direction} are inherited unchanged. The
     * variant id is {@code <baseId>:<transformName>} so derived cases stay
     * uniquely identifiable.
     */
    public AttackCase withTransformedPayload(String newPayload, String transformName) {
        return new AttackCase(id + ":" + transformName, category, threatClass, newPayload,
                direction, expectedBehavior, description, transformName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttackCase that = (AttackCase) o;
        return Objects.equals(id, that.id)
                && Objects.equals(category, that.category)
                && Objects.equals(threatClass, that.threatClass)
                && Objects.equals(payload, that.payload)
                && direction == that.direction
                && expectedBehavior == that.expectedBehavior
                && Objects.equals(description, that.description)
                && Objects.equals(transform, that.transform);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, category, threatClass, payload, direction, expectedBehavior,
                description, transform);
    }

    @Override
    public String toString() {
        return "AttackCase{id='" + id + "', category='" + category + "', threatClass='" + threatClass
                + "', direction=" + direction + ", expected=" + expectedBehavior
                + (transform != null ? ", transform='" + transform + "'" : "") + "}";
    }
}
