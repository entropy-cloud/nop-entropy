package io.nop.ai.agent.skill;

/**
 * Top-level behavioural pattern of a skill, used for coarse first-pass
 * filtering (design {@code skill-system-design.md} §4.1, §5.2 Phase 2).
 *
 * <p>Stored on {@link SkillModel} from phase 1 but not consulted by the
 * declaration-based matcher; it is reserved for phase-2 {@code topPattern}
 * coarse filtering once a request-side mechanism exists.
 */
public enum SkillTopPattern {
    PREPARE,
    ACT,
    VERIFY,
    MANAGE,
    RETRIEVE,
    TRANSFORM
}
