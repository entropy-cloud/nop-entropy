package io.nop.ai.agent.skill;

import java.util.Collection;
import java.util.Collections;

/**
 * Pass-through {@link ISkillProvider} that discovers zero skills. Using it (or
 * registering no provider) leaves engine behaviour unchanged — the resolver
 * resolves an empty assembly, no skill instructions or tools are injected.
 *
 * <p>Consistent with {@code NoOpTalent.noOp()} /
 * {@code NoOpContentGuardrail.noOp()} (plan 160 sibling pattern).
 */
public final class NoOpSkillProvider implements ISkillProvider {

    private static final NoOpSkillProvider INSTANCE = new NoOpSkillProvider();

    private static final Collection<SkillModel> EMPTY = Collections.emptyList();

    private NoOpSkillProvider() {
    }

    public static ISkillProvider noOp() {
        return INSTANCE;
    }

    @Override
    public Collection<SkillModel> getSkills() {
        return EMPTY;
    }
}
