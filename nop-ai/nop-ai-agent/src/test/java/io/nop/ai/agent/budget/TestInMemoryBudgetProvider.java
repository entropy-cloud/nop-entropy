package io.nop.ai.agent.budget;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 206 (L2-22) Phase 2 focused tests covering {@link InMemoryBudgetProvider}
 * limit + cost accumulation behaviour (design
 * {@code nop-ai-agent-usage-and-billing.md} §3.6).
 *
 * <p>Scenarios:
 * <ol>
 *   <li>Under limit → exceeded=false</li>
 *   <li>Over limit → exceeded=true</li>
 *   <li>Accumulation across multiple addCost calls</li>
 *   <li>null limit (unlimited) → never exceeded</li>
 *   <li>no cost tracked yet → estimatedTotalCost null, exceeded false</li>
 * </ol>
 */
public class TestInMemoryBudgetProvider {

    private AgentExecutionContext newCtx(long tokens) {
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "sess-mem");
        ctx.setTokensUsed(tokens);
        return ctx;
    }

    @Test
    void underLimitIsNotExceeded() {
        InMemoryBudgetProvider provider = new InMemoryBudgetProvider(new BigDecimal("1.00"));
        provider.addCost(new BigDecimal("0.40"));

        BudgetSnapshot snapshot = provider.getBudget(newCtx(500L));

        assertFalse(snapshot.isExceeded(),
                "cost(0.40) < limit(1.00) must not be exceeded");
        assertEquals(0, new BigDecimal("0.40").compareTo(snapshot.getEstimatedTotalCost()));
        assertEquals(500L, snapshot.getTotalTokensUsed());
    }

    @Test
    void overLimitIsExceeded() {
        InMemoryBudgetProvider provider = new InMemoryBudgetProvider(new BigDecimal("1.00"));
        provider.addCost(new BigDecimal("1.20"));

        BudgetSnapshot snapshot = provider.getBudget(newCtx(100L));

        assertTrue(snapshot.isExceeded(),
                "cost(1.20) > limit(1.00) must be exceeded");
    }

    @Test
    void costAccumulatesAcrossMultipleAddCalls() {
        InMemoryBudgetProvider provider = new InMemoryBudgetProvider(new BigDecimal("1.00"));

        provider.addCost(new BigDecimal("0.30"));
        assertFalse(provider.getBudget(newCtx(0L)).isExceeded());

        provider.addCost(new BigDecimal("0.30"));
        assertFalse(provider.getBudget(newCtx(0L)).isExceeded(),
                "0.60 < 1.00 still not exceeded");

        provider.addCost(new BigDecimal("0.50"));
        assertTrue(provider.getBudget(newCtx(0L)).isExceeded(),
                "1.10 > 1.00 now exceeded after third add");
    }

    @Test
    void nullLimitIsNeverExceeded() {
        InMemoryBudgetProvider provider = new InMemoryBudgetProvider(null);
        provider.addCost(new BigDecimal("999.00"));

        BudgetSnapshot snapshot = provider.getBudget(newCtx(0L));
        assertFalse(snapshot.isExceeded(),
                "null limit (unlimited) must never be exceeded regardless of cost");
        assertNull(snapshot.getBudgetLimit());
    }

    @Test
    void noCostTrackedYetReportsNullCost() {
        InMemoryBudgetProvider provider = new InMemoryBudgetProvider(new BigDecimal("1.00"));

        BudgetSnapshot snapshot = provider.getBudget(newCtx(0L));
        assertNull(snapshot.getEstimatedTotalCost(),
                "Before any addCost, estimatedTotalCost must be null (cost not yet tracked)");
        assertFalse(snapshot.isExceeded(),
                "null cost must not be exceeded even with a limit");
    }

    @Test
    void setCostOverridesAccumulatedTotal() {
        InMemoryBudgetProvider provider = new InMemoryBudgetProvider(new BigDecimal("1.00"));
        provider.addCost(new BigDecimal("0.50"));
        provider.setCost(new BigDecimal("0.95"));

        BudgetSnapshot snapshot = provider.getBudget(newCtx(0L));
        assertEquals(0, new BigDecimal("0.95").compareTo(snapshot.getEstimatedTotalCost()));
        assertFalse(snapshot.isExceeded(), "0.95 < 1.00 not exceeded");

        provider.setCost(new BigDecimal("1.00"));
        assertTrue(provider.getBudget(newCtx(0L)).isExceeded(),
                "exactly at limit (1.00 >= 1.00) is exceeded");
    }

    @Test
    void addCostNullIsNoOp() {
        InMemoryBudgetProvider provider = new InMemoryBudgetProvider(new BigDecimal("1.00"));
        provider.addCost(null);
        assertNull(provider.getEstimatedTotalCost(),
                "addCost(null) must not start tracking cost");
    }
}
