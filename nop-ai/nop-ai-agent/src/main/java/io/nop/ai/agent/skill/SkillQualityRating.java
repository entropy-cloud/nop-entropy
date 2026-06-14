package io.nop.ai.agent.skill;

/**
 * Quality-rating taxonomy for skill curation (design
 * {@code skill-system-design.md} §5.5). Each {@code SkillAssessment} carries
 * one of these ratings describing how well a skill definition is specified.
 *
 * <p>The taxonomy is deliberately small — three levels cover the curation
 * decision space without over-fragmenting:
 * <ul>
 *   <li><b>{@link #WELL_DEFINED}</b> — the skill's goal, matching signatures,
 *       dependencies, and resource scope are clear, complete, and
 *       non-redundant with other skills in the registry.</li>
 *   <li><b>{@link #NEEDS_IMPROVEMENT}</b> — the skill is usable but has gaps:
 *       vague goal, missing dependencies, ambiguous intent signatures, or
 *       under-specified resource scope.</li>
 *   <li><b>{@link #REDUNDANT}</b> — the skill substantially overlaps with one
 *       or more other skills in the registry; consolidation or removal is
 *       recommended.</li>
 * </ul>
 */
public enum SkillQualityRating {
    WELL_DEFINED,
    NEEDS_IMPROVEMENT,
    REDUNDANT
}
