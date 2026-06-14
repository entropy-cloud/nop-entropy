package io.nop.ai.agent.skill;

/**
 * Resource boundary declared by a skill (design {@code skill-system-design.md}
 * §4.1, §6.3). Collected in the skill assembly result for tracing/observability
 * in phase 1; enforcement against the permission system
 * ({@code ACTIVE_SCOPE = SKILL_SCOPE ∩ AGENT_PERMISSIONS}) is deferred to a
 * successor plan per the skill engine phase-1 Non-Goals.
 */
public enum SkillResourceScope {
    MEMORY,
    LOCAL_FS,
    CODEBASE,
    NETWORK,
    CREDENTIALS
}
