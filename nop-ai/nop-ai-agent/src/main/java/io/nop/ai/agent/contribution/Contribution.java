package io.nop.ai.agent.contribution;

import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.IAgentLifecycleHook;

import java.util.Objects;

/**
 * Immutable data object describing a single plugin contribution registered into
 * {@link IContributionRegistry} (design {@code nop-ai-agent-hook-skill-engine.md}
 * §8, plan 217).
 *
 * <p>Identity is the pair {@code (type, id)}: within a single source, the same
 * {@code (type, id)} pair replaces the previously-registered contribution;
 * the same {@code (type, id)} pair registered from a <em>different</em> source
 * fails fast with {@code NopAiAgentException} (no silent overwrite — plan 217
 * 裁定 2). The {@code source} field is the contributing plugin identifier and
 * scopes {@link IContributionRegistry#unregisterSource(String)} whole-source
 * teardown.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code type} — one of the 7 {@link ContributionType} values</li>
 *   <li>{@code id} — unique within {@code (type, source)}; combined with
 *       {@code type} forms the cross-source uniqueness key</li>
 *   <li>{@code source} — contributing plugin id (never null/empty)</li>
 *   <li>{@code priority} — ascending sort key (smaller fires first, same
 *       semantics as the Hook engine's priority ordering); default 0</li>
 *   <li>{@code payload} — typed by {@code type}: {@code HOOK} →
 *       {@link HookPayload}, {@code PROMPT} → {@code String}, other types →
 *       any typed payload the consumer knows how to interpret</li>
 * </ul>
 */
public final class Contribution {

    private final ContributionType type;
    private final String id;
    private final String source;
    private final int priority;
    private final Object payload;

    public Contribution(ContributionType type, String id, String source, int priority, Object payload) {
        if (type == null) {
            throw new IllegalArgumentException("Contribution: type must not be null");
        }
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Contribution: id must not be null or empty");
        }
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("Contribution: source must not be null or empty");
        }
        this.type = type;
        this.id = id;
        this.source = source;
        this.priority = priority;
        this.payload = payload;
    }

    /**
     * Convenience factory for an {@link ContributionType#HOOK} contribution.
     * The payload is wrapped into a {@link HookPayload}.
     */
    public static Contribution forHook(String id, String source, int priority,
                                       AgentLifecyclePoint point, IAgentLifecycleHook hook) {
        return new Contribution(ContributionType.HOOK, id, source, priority, new HookPayload(point, hook));
    }

    /**
     * Convenience factory for a {@link ContributionType#PROMPT} contribution.
     * The payload is the prompt fragment String.
     */
    public static Contribution forPrompt(String id, String source, int priority, String instruction) {
        return new Contribution(ContributionType.PROMPT, id, source, priority, instruction);
    }

    public ContributionType getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public int getPriority() {
        return priority;
    }

    public Object getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contribution)) return false;
        Contribution that = (Contribution) o;
        return type == that.type && Objects.equals(id, that.id) && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id, source);
    }

    @Override
    public String toString() {
        return "Contribution{type=" + type + ", id='" + id + "', source='" + source
                + "', priority=" + priority + '}';
    }
}
