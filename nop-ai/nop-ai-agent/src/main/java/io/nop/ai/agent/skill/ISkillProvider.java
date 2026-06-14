package io.nop.ai.agent.skill;

import java.util.Collection;

/**
 * Skill discovery contract (design {@code nop-ai-agent-hook-skill-engine.md} §3,
 * {@code skill-system-design.md} §5.1). An {@code ISkillProvider} returns the
 * set of all registered skills keyed by name. It performs no matching —
 * matching an agent's {@code availableSkills} / {@code requiredSkills}
 * declarations against the registry is the {@link SkillResolver}'s job.
 *
 * <p>Consistent with the extension-point convention established by
 * {@code ITalent} (plan 160): a pass-through default ({@link NoOpSkillProvider})
 * discovers zero skills, so registering it (or nothing) leaves engine behaviour
 * unchanged.
 */
public interface ISkillProvider {

    /**
     * Return all skills known to this provider. Must never return
     * {@code null}; return an empty collection when no skills are registered.
     *
     * <p>The returned collection may be cached — skills do not change at
     * runtime per design §7.3.
     */
    Collection<SkillModel> getSkills();
}
