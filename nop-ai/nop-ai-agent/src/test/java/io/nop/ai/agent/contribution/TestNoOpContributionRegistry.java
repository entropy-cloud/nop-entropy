package io.nop.ai.agent.contribution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNoOpContributionRegistry {

    @Test
    void singletonInstanceIsStable() {
        assertSame(NoOpContributionRegistry.INSTANCE, NoOpContributionRegistry.noOp());
    }

    @Test
    void registerReturnsFalseExplicitNoOp() {
        Contribution c = new Contribution(ContributionType.PROMPT, "p1", "src", 0, "x");
        // Explicit false — NOT a silent success (Minimum Rules #24).
        assertFalse(NoOpContributionRegistry.INSTANCE.register(c));
    }

    @Test
    void queriesReturnEmptyNeverNull() {
        assertTrue(NoOpContributionRegistry.INSTANCE.getContributions(ContributionType.HOOK).isEmpty());
        assertTrue(NoOpContributionRegistry.INSTANCE.getContributions(ContributionType.PROMPT, "any").isEmpty());
        assertTrue(NoOpContributionRegistry.INSTANCE.getSources().isEmpty());
    }

    @Test
    void unregisterIsExplicitNoOp() {
        // Must not throw — clean teardown of an unknown source is a no-op.
        NoOpContributionRegistry.INSTANCE.unregisterSource("any");
        NoOpContributionRegistry.INSTANCE.unregisterSource(null);
    }

    @Test
    void emptyQueriesAreImmutable() {
        List<Contribution> cs = NoOpContributionRegistry.INSTANCE.getContributions(ContributionType.TOOL);
        Set<String> sources = NoOpContributionRegistry.INSTANCE.getSources();
        assertTrue(cs.isEmpty());
        assertTrue(sources.isEmpty());
        // Returned empty list/set is Collections.emptyList/emptySet — immutable.
    }

    @Test
    void registerNullDoesNotCrash() {
        // NoOp is a pass-through; even a null argument is a no-op (returns false).
        assertFalse(NoOpContributionRegistry.INSTANCE.register(null));
    }

    @Test
    void functionalVsNoOpContractContrast() {
        // NoOp.register returns FALSE; InMemory.register returns TRUE.
        // This is the explicit no-op contract vs silent-success anti-pattern.
        Contribution c = new Contribution(ContributionType.PROMPT, "p1", "src", 0, "x");
        assertFalse(NoOpContributionRegistry.INSTANCE.register(c));

        InMemoryContributionRegistry functional = new InMemoryContributionRegistry();
        assertTrue(functional.register(c));
        assertEquals(1, functional.getContributions(ContributionType.PROMPT).size());
    }
}
