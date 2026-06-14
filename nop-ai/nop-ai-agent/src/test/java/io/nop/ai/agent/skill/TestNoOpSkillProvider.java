package io.nop.ai.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNoOpSkillProvider {

    @Test
    void factoryReturnsSingleton() {
        ISkillProvider a = NoOpSkillProvider.noOp();
        ISkillProvider b = NoOpSkillProvider.noOp();
        assertSame(a, b);
    }

    @Test
    void implementsISkillProvider() {
        assertTrue(ISkillProvider.class.isAssignableFrom(NoOpSkillProvider.class));
        assertTrue(NoOpSkillProvider.noOp() instanceof ISkillProvider);
    }

    @Test
    void returnsEmptyNonNullCollection() {
        Collection<SkillModel> skills = NoOpSkillProvider.noOp().getSkills();
        assertNotNull(skills);
        assertTrue(skills.isEmpty());
    }

    @Test
    void getSkillsIsConsistentAcrossCalls() {
        ISkillProvider provider = NoOpSkillProvider.noOp();
        Collection<SkillModel> first = provider.getSkills();
        Collection<SkillModel> second = provider.getSkills();
        assertTrue(first.isEmpty());
        assertTrue(second.isEmpty());
    }
}
