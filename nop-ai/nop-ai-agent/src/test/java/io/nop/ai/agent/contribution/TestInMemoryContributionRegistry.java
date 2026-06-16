package io.nop.ai.agent.contribution;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestInMemoryContributionRegistry {

    private static Contribution prompt(String id, String source, int priority, String payload) {
        return new Contribution(ContributionType.PROMPT, id, source, priority, payload);
    }

    private static Contribution hook(String id, String source, int priority, Object payload) {
        return new Contribution(ContributionType.HOOK, id, source, priority, payload);
    }

    @Test
    void registerAndQueryByType() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        assertTrue(reg.register(prompt("p1", "plugin-a", 0, "alpha")));
        assertTrue(reg.register(prompt("p2", "plugin-a", 0, "beta")));
        assertTrue(reg.register(hook("h1", "plugin-a", 0, new Object())));

        List<Contribution> prompts = reg.getContributions(ContributionType.PROMPT);
        assertEquals(2, prompts.size());
        List<Contribution> hooks = reg.getContributions(ContributionType.HOOK);
        assertEquals(1, hooks.size());
        assertTrue(reg.getContributions(ContributionType.TOOL).isEmpty());
    }

    @Test
    void queryByTypeAndSource() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "plugin-a", 0, "a"));
        reg.register(prompt("p2", "plugin-b", 0, "b"));
        reg.register(prompt("p3", "plugin-b", 0, "c"));

        assertEquals(1, reg.getContributions(ContributionType.PROMPT, "plugin-a").size());
        assertEquals(2, reg.getContributions(ContributionType.PROMPT, "plugin-b").size());
        assertTrue(reg.getContributions(ContributionType.PROMPT, "plugin-c").isEmpty());
        assertTrue(reg.getContributions(ContributionType.HOOK, "plugin-a").isEmpty());
    }

    @Test
    void priorityAscendingStableOrder() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p-low", "s", 10, "low"));
        reg.register(prompt("p-mid", "s", 5, "mid"));
        reg.register(prompt("p-mid2", "s", 5, "mid2"));
        reg.register(prompt("p-high", "s", 1, "high"));

        List<Contribution> result = reg.getContributions(ContributionType.PROMPT);
        assertEquals(4, result.size());
        assertEquals("p-high", result.get(0).getId());
        // same-priority (5) keeps registration order: p-mid before p-mid2
        assertEquals("p-mid", result.get(1).getId());
        assertEquals("p-mid2", result.get(2).getId());
        assertEquals("p-low", result.get(3).getId());
    }

    @Test
    void sameSourceReRegisterReplaces() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "s", 0, "old"));
        reg.register(prompt("p1", "s", 0, "new"));

        List<Contribution> result = reg.getContributions(ContributionType.PROMPT);
        assertEquals(1, result.size(), "same-source re-register replaces, not adds");
        assertEquals("new", result.get(0).getPayload());
    }

    @Test
    void sameSourceReRegisterChangesPriority() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "s", 10, "low-prio"));
        reg.register(prompt("p2", "s", 0, "high-prio"));
        // re-register p1 with priority 0 — should now sort before p2's stable position? No:
        // both are priority 0 now; p1 was registered first (initial registration), p2 second.
        // Stable sort keeps registration order: p1 before p2.
        reg.register(prompt("p1", "s", 0, "low-prio-updated"));

        List<Contribution> result = reg.getContributions(ContributionType.PROMPT);
        assertEquals(2, result.size());
        assertEquals("p1", result.get(0).getId());
        assertEquals("p2", result.get(1).getId());
        assertEquals("low-prio-updated", result.get(0).getPayload());
    }

    @Test
    void crossSourceCollisionThrows() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "plugin-a", 0, "from-a"));

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> reg.register(prompt("p1", "plugin-b", 0, "from-b")));
        // The error must carry type/id/source context — not a silent overwrite.
        assertTrue(ex.getMessage().contains("p1"));
        assertTrue(ex.getMessage().contains("plugin-a"));
        assertTrue(ex.getMessage().contains("plugin-b"));
        assertTrue(ex.getMessage().contains("PROMPT"));

        // The failed cross-source register did NOT overwrite the existing entry.
        List<Contribution> result = reg.getContributions(ContributionType.PROMPT);
        assertEquals(1, result.size());
        assertEquals("from-a", result.get(0).getPayload());
    }

    @Test
    void crossSourceCollisionAcrossDifferentTypesAllowed() {
        // Same id under DIFFERENT types is allowed (uniqueness is per (type,id)).
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("dup-id", "plugin-a", 0, "prompt"));
        reg.register(hook("dup-id", "plugin-b", 0, new Object()));

        assertEquals(1, reg.getContributions(ContributionType.PROMPT).size());
        assertEquals(1, reg.getContributions(ContributionType.HOOK).size());
    }

    @Test
    void unregisterSourceRemovesAllFromSource() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "plugin-a", 0, "a1"));
        reg.register(prompt("p2", "plugin-a", 0, "a2"));
        reg.register(hook("h1", "plugin-a", 0, new Object()));
        reg.register(prompt("p3", "plugin-b", 0, "b1"));

        reg.unregisterSource("plugin-a");

        assertTrue(reg.getContributions(ContributionType.PROMPT, "plugin-a").isEmpty());
        assertTrue(reg.getContributions(ContributionType.HOOK, "plugin-a").isEmpty());
        // plugin-b untouched
        assertEquals(1, reg.getContributions(ContributionType.PROMPT, "plugin-b").size());
        assertEquals(1, reg.getContributions(ContributionType.PROMPT).size());
    }

    @Test
    void unregisterUnknownSourceIsNoOp() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "plugin-a", 0, "a1"));
        reg.unregisterSource("nonexistent");
        assertEquals(1, reg.getContributions(ContributionType.PROMPT).size());
    }

    @Test
    void unregisterNullSourceIsNoOp() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "plugin-a", 0, "a1"));
        reg.unregisterSource(null);
        assertEquals(1, reg.getContributions(ContributionType.PROMPT).size());
    }

    @Test
    void getSourcesReturnsAllRegistered() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "plugin-a", 0, "a"));
        reg.register(prompt("p2", "plugin-b", 0, "b"));
        reg.register(hook("h1", "plugin-c", 0, new Object()));

        Set<String> sources = reg.getSources();
        assertEquals(Set.of("plugin-a", "plugin-b", "plugin-c"), sources);
    }

    @Test
    void getSourcesReflectsUnregister() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "plugin-a", 0, "a"));
        reg.register(prompt("p2", "plugin-b", 0, "b"));
        reg.unregisterSource("plugin-a");

        assertEquals(Set.of("plugin-b"), reg.getSources());
    }

    @Test
    void emptyRegistryReturnsEmptyQueries() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        assertTrue(reg.getContributions(ContributionType.PROMPT).isEmpty());
        assertTrue(reg.getContributions(ContributionType.PROMPT, "x").isEmpty());
        assertTrue(reg.getSources().isEmpty());
    }

    @Test
    void getSourcesReturnsUnmodifiableSnapshot() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "plugin-a", 0, "a"));
        Set<String> sources = reg.getSources();
        assertThrows(UnsupportedOperationException.class, () -> sources.add("evil"));
    }

    @Test
    void registerNullThrows() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        assertThrows(IllegalArgumentException.class, () -> reg.register(null));
    }

    @Test
    void reRegisterAfterUnregisterAllowed() {
        // After unregistering source-a, a fresh register of the same (type,id)
        // from source-a (or any source) must succeed.
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "plugin-a", 0, "v1"));
        reg.unregisterSource("plugin-a");
        assertTrue(reg.register(prompt("p1", "plugin-a", 0, "v2")));
        assertEquals("v2", reg.getContributions(ContributionType.PROMPT).get(0).getPayload());
    }

    @Test
    void nullTypeQueryReturnsEmpty() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "plugin-a", 0, "a"));
        assertTrue(reg.getContributions(null).isEmpty());
        assertTrue(reg.getContributions(null, "plugin-a").isEmpty());
    }

    @Test
    void nullSourceQueryReturnsEmpty() {
        InMemoryContributionRegistry reg = new InMemoryContributionRegistry();
        reg.register(prompt("p1", "plugin-a", 0, "a"));
        assertTrue(reg.getContributions(ContributionType.PROMPT, null).isEmpty());
    }

    @Test
    void falseReturnValueIsExplicitNotSilentSuccess_NoOpContract() {
        // Distinct from InMemory (which returns true). NoOp returns false —
        // explicit no-op, not a silent success (Minimum Rules #24). This test
        // lives in this file so reviewers see both contracts side by side.
        assertFalse(NoOpContributionRegistry.INSTANCE.register(prompt("p1", "s", 0, "x")));
        assertTrue(NoOpContributionRegistry.INSTANCE.getContributions(ContributionType.PROMPT).isEmpty());
        assertTrue(NoOpContributionRegistry.INSTANCE.getSources().isEmpty());
    }
}
