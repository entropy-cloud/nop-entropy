package io.nop.ai.agent.skill;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSkillResolver {

    private static SkillModel skill(String name, String goal, String... tools) {
        SkillModel s = new SkillModel();
        s.setName(name);
        s.setGoal(goal);
        s.setDependencies(Arrays.asList(tools));
        return s;
    }

    private static SkillModel skillWithScope(String name, String goal, Set<SkillResourceScope> scopes, String... tools) {
        SkillModel s = skill(name, goal, tools);
        s.setResourceScope(scopes);
        return s;
    }

    private static ISkillProvider provider(SkillModel... skills) {
        List<SkillModel> list = Arrays.asList(skills);
        return () -> list;
    }

    private static Set<String> set(String... items) {
        return new LinkedHashSet<>(Arrays.asList(items));
    }

    @Test
    void availableSkillsIntersectRegistryActivatesCorrectSubset() {
        ISkillProvider provider = provider(
                skill("a", "goal-a", "tool-a"),
                skill("b", "goal-b", "tool-b"),
                skill("c", "goal-c", "tool-c"));

        SkillResolver resolver = new SkillResolver(provider);

        AgentModel agent = new AgentModel();
        agent.setName("test-agent");
        agent.setAvailableSkills(set("a", "c"));

        SkillAssemblyResult result = resolver.resolve(agent);

        assertEquals(set("a", "c"), result.getActivatedSkillNames());
        assertTrue(result.getInstructions().contains("goal-a"));
        assertTrue(result.getInstructions().contains("goal-c"));
        assertFalse(result.getInstructions().contains("goal-b"));
    }

    @Test
    void missingRequiredSkillThrowsFailFast() {
        ISkillProvider provider = provider(
                skill("a", "goal-a"));

        SkillResolver resolver = new SkillResolver(provider);

        AgentModel agent = new AgentModel();
        agent.setName("test-agent");
        agent.setRequiredSkills(set("a", "missing-skill"));

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () -> resolver.resolve(agent));
        assertTrue(ex.getMessage().contains("missing-skill"),
                "Error must name the missing skill. Got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("test-agent"),
                "Error must name the agent. Got: " + ex.getMessage());
    }

    @Test
    void requiredSkillsAllPresentAreForceActivated() {
        ISkillProvider provider = provider(
                skill("a", "goal-a"),
                skill("b", "goal-b"));

        SkillResolver resolver = new SkillResolver(provider);

        AgentModel agent = new AgentModel();
        agent.setRequiredSkills(set("a", "b"));

        SkillAssemblyResult result = resolver.resolve(agent);

        assertEquals(set("a", "b"), result.getActivatedSkillNames());
    }

    @Test
    void emptyDeclarationsProduceEmptyAssembly() {
        ISkillProvider provider = provider(
                skill("a", "goal-a"));

        SkillResolver resolver = new SkillResolver(provider);

        AgentModel agent = new AgentModel();
        agent.setAvailableSkills(Collections.emptySet());
        agent.setRequiredSkills(Collections.emptySet());

        SkillAssemblyResult result = resolver.resolve(agent);

        assertTrue(result.isEmpty());
        assertTrue(result.getInstructions().isEmpty());
        assertTrue(result.getToolDependencies().isEmpty());
        assertTrue(result.getActivatedSkillNames().isEmpty());
    }

    @Test
    void nullDeclarationsProduceEmptyAssembly() {
        ISkillProvider provider = provider(skill("a", "goal-a"));
        SkillResolver resolver = new SkillResolver(provider);

        AgentModel agent = new AgentModel();
        // null availableSkills and requiredSkills (the AgentModel default)

        SkillAssemblyResult result = resolver.resolve(agent);

        assertTrue(result.isEmpty());
    }

    @Test
    void nullAgentModelProducesEmptyAssembly() {
        ISkillProvider provider = provider(skill("a", "goal-a"));
        SkillResolver resolver = new SkillResolver(provider);

        SkillAssemblyResult result = resolver.resolve(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void skillDependenciesCollectedInAssembly() {
        ISkillProvider provider = provider(
                skill("a", "goal-a", "read_file", "grep"),
                skill("b", "goal-b", "read_file", "git_diff"));

        SkillResolver resolver = new SkillResolver(provider);

        AgentModel agent = new AgentModel();
        agent.setAvailableSkills(set("a", "b"));

        SkillAssemblyResult result = resolver.resolve(agent);

        Set<String> tools = result.getToolDependencies();
        assertTrue(tools.contains("read_file"));
        assertTrue(tools.contains("grep"));
        assertTrue(tools.contains("git_diff"));
        assertEquals(3, tools.size(), "Merged tool dependencies must be deduplicated");
    }

    @Test
    void noOpProviderProducesEmptyAssembly() {
        SkillResolver resolver = new SkillResolver(NoOpSkillProvider.noOp());

        AgentModel agent = new AgentModel();
        agent.setAvailableSkills(set("a", "b"));

        SkillAssemblyResult result = resolver.resolve(agent);

        assertTrue(result.isEmpty(), "NoOp provider must produce empty assembly");
    }

    @Test
    void requiredAndAvailableOverlapDoesNotDuplicateActivation() {
        ISkillProvider provider = provider(
                skill("shared", "goal-shared", "tool-shared"),
                skill("only-avail", "goal-avail", "tool-avail"));

        SkillResolver resolver = new SkillResolver(provider);

        AgentModel agent = new AgentModel();
        // "shared" is in both required and available — must activate once.
        agent.setRequiredSkills(set("shared"));
        agent.setAvailableSkills(set("shared", "only-avail"));

        SkillAssemblyResult result = resolver.resolve(agent);

        assertEquals(set("shared", "only-avail"), result.getActivatedSkillNames());
        // Instructions must not contain "goal-shared" twice.
        long sharedCount = result.getInstructions().stream()
                .filter("goal-shared"::equals)
                .count();
        assertEquals(1, sharedCount, "Overlapping required/available skill must activate exactly once");
        // Tool "tool-shared" must appear once.
        assertTrue(result.getToolDependencies().contains("tool-shared"));
    }

    @Test
    void missingAvailableSkillIsSilentlyIgnored() {
        ISkillProvider provider = provider(
                skill("a", "goal-a"));

        SkillResolver resolver = new SkillResolver(provider);

        AgentModel agent = new AgentModel();
        agent.setAvailableSkills(set("a", "not-registered"));

        // Must NOT throw — missing availableSkills are silently ignored.
        SkillAssemblyResult result = assertDoesNotThrow(() -> resolver.resolve(agent));

        assertEquals(set("a"), result.getActivatedSkillNames());
    }

    @Test
    void resourceScopeCollectedInAssembly() {
        ISkillProvider provider = provider(
                skillWithScope("a", "goal-a",
                        new LinkedHashSet<>(Arrays.asList(SkillResourceScope.CODEBASE, SkillResourceScope.MEMORY)),
                        "tool-a"),
                skillWithScope("b", "goal-b",
                        new LinkedHashSet<>(Arrays.asList(SkillResourceScope.NETWORK)),
                        "tool-b"));

        SkillResolver resolver = new SkillResolver(provider);

        AgentModel agent = new AgentModel();
        agent.setAvailableSkills(set("a", "b"));

        SkillAssemblyResult result = resolver.resolve(agent);

        Set<SkillResourceScope> scopes = result.getResourceScope();
        assertTrue(scopes.contains(SkillResourceScope.CODEBASE));
        assertTrue(scopes.contains(SkillResourceScope.MEMORY));
        assertTrue(scopes.contains(SkillResourceScope.NETWORK));
        assertEquals(3, scopes.size());
    }

    @Test
    void constructorRejectsNullProvider() {
        assertThrows(NopAiAgentException.class, () -> new SkillResolver(null));
    }
}
