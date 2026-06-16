package io.nop.ai.agent.contribution;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Shipped no-op default for {@link IContributionRegistry}.
 *
 * <p>{@link #register(Contribution)} explicitly returns {@code false} (not a
 * silent success — plan 217 Minimum Rules #24). All queries return empty. The
 * engine uses this default out-of-the-box, so integrators see zero behaviour
 * regression unless they explicitly wire a functional registry (e.g.
 * {@link InMemoryContributionRegistry}) via
 * {@code DefaultAgentEngine.setContributionRegistry(...)}.
 */
public final class NoOpContributionRegistry implements IContributionRegistry {

    public static final NoOpContributionRegistry INSTANCE = new NoOpContributionRegistry();

    private NoOpContributionRegistry() {
    }

    public static NoOpContributionRegistry noOp() {
        return INSTANCE;
    }

    @Override
    public boolean register(Contribution contribution) {
        return false;
    }

    @Override
    public void unregisterSource(String source) {
        // explicit no-op — nothing to unregister
    }

    @Override
    public List<Contribution> getContributions(ContributionType type) {
        return Collections.emptyList();
    }

    @Override
    public List<Contribution> getContributions(ContributionType type, String source) {
        return Collections.emptyList();
    }

    @Override
    public Set<String> getSources() {
        return Collections.emptySet();
    }
}
