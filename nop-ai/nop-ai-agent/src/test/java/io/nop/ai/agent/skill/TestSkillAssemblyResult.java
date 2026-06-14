package io.nop.ai.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSkillAssemblyResult {

    @Test
    void emptyResultHasNoData() {
        SkillAssemblyResult result = SkillAssemblyResult.empty();

        assertTrue(result.isEmpty());
        assertTrue(result.getInstructions().isEmpty());
        assertTrue(result.getToolDependencies().isEmpty());
        assertTrue(result.getResourceScope().isEmpty());
        assertTrue(result.getActivatedSkillNames().isEmpty());
    }

    @Test
    void builderCollectsFromSkills() {
        SkillModel a = new SkillModel();
        a.setName("a");
        a.setGoal("goal-a");
        a.setDependencies(Arrays.asList("read_file", "grep"));
        a.setResourceScope(Set.of(SkillResourceScope.CODEBASE));

        SkillModel b = new SkillModel();
        b.setName("b");
        b.setGoal("goal-b");
        b.setDependencies(Arrays.asList("read_file", "git_diff"));
        b.setResourceScope(Set.of(SkillResourceScope.NETWORK));

        SkillAssemblyResult result = SkillAssemblyResult.builder()
                .addSkill(a)
                .addSkill(b)
                .build();

        assertEquals(List.of("goal-a", "goal-b"), result.getInstructions());
        assertTrue(result.getToolDependencies().contains("read_file"));
        assertTrue(result.getToolDependencies().contains("grep"));
        assertTrue(result.getToolDependencies().contains("git_diff"));
        assertEquals(3, result.getToolDependencies().size());
        assertTrue(result.getResourceScope().contains(SkillResourceScope.CODEBASE));
        assertTrue(result.getResourceScope().contains(SkillResourceScope.NETWORK));
        assertEquals(Set.of("a", "b"), result.getActivatedSkillNames());
    }

    @Test
    void builderSkipsNullEmptyGoal() {
        SkillModel a = new SkillModel();
        a.setName("a");
        a.setGoal(null);
        SkillModel b = new SkillModel();
        b.setName("b");
        b.setGoal("");

        SkillAssemblyResult result = SkillAssemblyResult.builder()
                .addSkill(a)
                .addSkill(b)
                .build();

        assertTrue(result.getInstructions().isEmpty());
        assertEquals(Set.of("a", "b"), result.getActivatedSkillNames());
    }

    @Test
    void resultIsImmutable() {
        SkillAssemblyResult result = SkillAssemblyResult.builder()
                .addActivatedName("x")
                .addAllInstructions(List.of("goal-x"))
                .addAllToolDependencies(List.of("tool-x"))
                .build();

        assertNotNull(result.getActivatedSkillNames());
        assertNotNull(result.getInstructions());
        assertNotNull(result.getToolDependencies());

        assertEquals(Collections.singleton("x"), result.getActivatedSkillNames());
    }

    @Test
    void toStringIncludesActivatedNames() {
        SkillModel a = new SkillModel();
        a.setName("a");
        a.setGoal("goal-a");

        SkillAssemblyResult result = SkillAssemblyResult.builder().addSkill(a).build();

        String str = result.toString();
        assertTrue(str.contains("a"), "toString must include activated names for logging");
    }
}
