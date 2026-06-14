package io.nop.ai.agent.skill;

import java.util.Collection;

/**
 * Skill curation contract (design {@code skill-system-design.md} §5.5). An
 * {@code ISkillCurator} evaluates a skill registry and produces advisory
 * curation recommendations — per-skill quality assessments, coverage gaps, and
 * redundancies.
 *
 * <p>The curator is <b>advisory and non-mutating</b>: it reads skill
 * definitions and evaluates their clarity, completeness, and coverage — it
 * never modifies them. Consistent with the {@code LlmCompletionJudge} advisory
 * pattern ("裁决是'建议'不是'命令'") and the skill engine's static-loading
 * design (§7.3 — skills don't change at runtime).
 *
 * <p>The curator is decoupled from {@link ISkillProvider} — it consumes the
 * registry collection directly. Sourcing skills from {@code ISkillProvider} is
 * the engine's responsibility (the engine calls the provider, then passes the
 * resulting collection to the curator).
 *
 * <p>The curator is an <b>on-demand analytical tool</b>, not an in-loop ReAct
 * component. It is invokable on-demand for skill quality assessment; it is not
 * invoked during {@code ReActAgentExecutor.execute()}.
 *
 * <p>Consistent with the extension-point convention: a pass-through default
 * ({@link NoOpSkillCurator}) returns an empty curation result, so registering
 * it (or nothing) leaves engine behaviour unchanged.
 */
public interface ISkillCurator {

    /**
     * Curate the given skill registry, producing advisory curation
     * recommendations.
     *
     * @param skills  the skill registry to evaluate; may be null or empty (both
     *                yield an empty success result). Never modified.
     * @return the curation result (never null); carries per-skill assessments,
     *         registry-level observations, and metadata with a success/fail
     *         marker
     */
    SkillCurationResult curate(Collection<SkillModel> skills);
}
