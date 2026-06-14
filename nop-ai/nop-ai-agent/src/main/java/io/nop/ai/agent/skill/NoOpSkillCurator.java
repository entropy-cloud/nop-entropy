package io.nop.ai.agent.skill;

/**
 * Pass-through {@link ISkillCurator} that returns an empty curation result.
 * Using it (or registering no curator) leaves engine behaviour unchanged — no
 * skill assessments are produced, no LLM calls are made.
 *
 * <p>Consistent with {@code NoOpSkillProvider.noOp()} /
 * {@code NoOpContentGuardrail.noOp()} (plan 160 sibling pattern).
 */
public final class NoOpSkillCurator implements ISkillCurator {

    private static final NoOpSkillCurator INSTANCE = new NoOpSkillCurator();

    private NoOpSkillCurator() {
    }

    public static ISkillCurator noOp() {
        return INSTANCE;
    }

    @Override
    public SkillCurationResult curate(java.util.Collection<SkillModel> skills) {
        return SkillCurationResult.empty();
    }
}
