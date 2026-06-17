package io.nop.ai.agent.quota;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only {@link IResourceGuard} that delegates to a real guard and records
 * every {@code checkConcurrent} call, so focused tests can assert wiring
 * (Minimum Rules #23 — the enforcement point actually invoked the guard at
 * runtime, not just that the guard type exists).
 *
 * <p>Records the dimension/scopeKey/projectedCount/overrideLimit of each call
 * plus the resulting {@link QuotaDecision}.
 */
public class CountingResourceGuard implements IResourceGuard {

    private final IResourceGuard delegate;
    private final List<QuotaDimension> dimensions = new ArrayList<>();
    private final List<QuotaDecision> decisions = new ArrayList<>();
    private int callCount = 0;

    public CountingResourceGuard(IResourceGuard delegate) {
        this.delegate = delegate;
    }

    @Override
    public QuotaDecision checkConcurrent(QuotaDimension dimension, String scopeKey,
                                         int projectedCount, int overrideLimit) {
        callCount++;
        dimensions.add(dimension);
        QuotaDecision decision = delegate.checkConcurrent(dimension, scopeKey,
                projectedCount, overrideLimit);
        decisions.add(decision);
        return decision;
    }

    public int getCallCount() {
        return callCount;
    }

    public List<QuotaDimension> getDimensions() {
        return dimensions;
    }

    public List<QuotaDecision> getDecisions() {
        return decisions;
    }

    public boolean wasCalled() {
        return callCount > 0;
    }
}
