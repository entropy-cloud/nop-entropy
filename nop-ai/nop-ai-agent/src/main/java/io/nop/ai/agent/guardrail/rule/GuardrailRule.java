package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.guardrail.GuardrailDirection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Declarative guardrail rule with structural relationships (design
 * {@code guardrail-contract.md} §增量 2, Decision A). Immutable value object.
 *
 * <p>A rule has a deterministic regex {@code pattern} match condition, a result
 * {@link RuleAction}, and two structural relationship lists:
 * <ul>
 * <li>{@code dependsOn} — when this rule is in the active set, the listed rules
 * are pulled in (transitive closure, expanding the evaluation surface).</li>
 * <li>{@code excludes} — when this rule is in the active set, the listed rules
 * are removed (structural narrowing; excludes wins over dependsOn).</li>
 * </ul>
 *
 * <p>Coexists with the existing {@code PromptInjectionGuardrail}; the existing
 * hardcoded rules are NOT migrated (see Non-Goals).
 */
public final class GuardrailRule {

    private final String id;
    private final GuardrailDirection direction;
    private final String pattern;
    private final RuleAction action;
    private final String modifyReplacement;
    private final List<String> dependsOn;
    private final List<String> excludes;
    private final String threatClass;
    private final String description;

    public GuardrailRule(String id, GuardrailDirection direction, String pattern, RuleAction action,
                         String modifyReplacement, List<String> dependsOn, List<String> excludes,
                         String threatClass, String description) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("GuardrailRule: id must not be null or empty");
        }
        if (pattern == null) {
            throw new IllegalArgumentException("GuardrailRule: pattern must not be null (id=" + id + ")");
        }
        if (action == null) {
            throw new IllegalArgumentException("GuardrailRule: action must not be null (id=" + id + ")");
        }
        if (action == RuleAction.MODIFY && (modifyReplacement == null)) {
            throw new IllegalArgumentException(
                    "GuardrailRule: MODIFY action requires non-null modifyReplacement (id=" + id + ")");
        }
        this.id = id;
        this.direction = direction;
        this.pattern = pattern;
        this.action = action;
        this.modifyReplacement = modifyReplacement;
        this.dependsOn = toImmutableNullable(dependsOn);
        this.excludes = toImmutableNullable(excludes);
        this.threatClass = threatClass;
        this.description = description;
    }

    private static List<String> toImmutableNullable(List<String> src) {
        if (src == null) {
            return null;
        }
        if (src.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(src));
    }

    public String getId() {
        return id;
    }

    /**
     * Direction this rule applies to. {@code null} means the rule applies to
     * both {@link GuardrailDirection#INPUT} and {@link GuardrailDirection#OUTPUT}.
     */
    public GuardrailDirection getDirection() {
        return direction;
    }

    /**
     * Whether this rule applies to the given direction ({@code null} direction
     * rule applies to both).
     */
    public boolean appliesTo(GuardrailDirection dir) {
        return direction == null || direction == dir;
    }

    public String getPattern() {
        return pattern;
    }

    public RuleAction getAction() {
        return action;
    }

    public String getModifyReplacement() {
        return modifyReplacement;
    }

    /**
     * Immutable list of rule ids this rule depends on (never null after ctor;
     * empty if none declared). Pulling them in is transitive and expands the
     * evaluation surface (Decision B-1/B-2).
     */
    public List<String> getDependsOn() {
        return dependsOn == null ? Collections.emptyList() : dependsOn;
    }

    /**
     * Immutable list of rule ids this rule excludes (never null after ctor;
     * empty if none declared). Excludes is non-transitive (Decision B-4) and
     * wins over dependsOn (Decision B-6).
     */
    public List<String> getExcludes() {
        return excludes == null ? Collections.emptyList() : excludes;
    }

    public String getThreatClass() {
        return threatClass;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasRelationships() {
        return !getDependsOn().isEmpty() || !getExcludes().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuardrailRule that = (GuardrailRule) o;
        return Objects.equals(id, that.id)
                && direction == that.direction
                && Objects.equals(pattern, that.pattern)
                && action == that.action
                && Objects.equals(modifyReplacement, that.modifyReplacement)
                && Objects.equals(getDependsOn(), that.getDependsOn())
                && Objects.equals(getExcludes(), that.getExcludes())
                && Objects.equals(threatClass, that.threatClass)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, direction, pattern, action, modifyReplacement,
                getDependsOn(), getExcludes(), threatClass, description);
    }

    @Override
    public String toString() {
        return "GuardrailRule{id='" + id + "', action=" + action
                + (threatClass != null ? ", threatClass='" + threatClass + "'" : "")
                + (direction != null ? ", direction=" + direction : "")
                + (!getDependsOn().isEmpty() ? ", dependsOn=" + getDependsOn() : "")
                + (!getExcludes().isEmpty() ? ", excludes=" + getExcludes() : "")
                + "}";
    }
}
