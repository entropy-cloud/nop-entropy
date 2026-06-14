package io.nop.ai.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestISkillProvider {

    @Test
    void interfaceContractCanBeImplemented() {
        ISkillProvider provider = new ISkillProvider() {
            private final java.util.List<SkillModel> skills = java.util.List.of(buildSkill("a"));

            @Override
            public Collection<SkillModel> getSkills() {
                return skills;
            }
        };

        Collection<SkillModel> skills = provider.getSkills();
        assertNotNull(skills);
        assertEquals(1, skills.size());
        assertEquals("a", skills.iterator().next().getName());
    }

    @Test
    void implementationMayReturnEmpty() {
        ISkillProvider provider = () -> java.util.Collections.emptyList();
        assertNotNull(provider.getSkills());
        assertTrue(provider.getSkills().isEmpty());
    }

    private static SkillModel buildSkill(String name) {
        SkillModel skill = new SkillModel();
        skill.setName(name);
        return skill;
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
