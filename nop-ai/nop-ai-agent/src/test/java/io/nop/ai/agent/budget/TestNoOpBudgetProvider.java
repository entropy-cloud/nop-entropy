package io.nop.ai.agent.budget;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 206 (L2-22) Phase 2: verify {@link NoOpBudgetProvider} pass-through
 * semantics — singleton identity, unlimited snapshot, and a no-throw
 * {@link IBudgetProvider#getBudget}.
 */
public class TestNoOpBudgetProvider {

    @Test
    void noOpReturnsSameSingletonInstance() {
        IBudgetProvider a = NoOpBudgetProvider.noOp();
        IBudgetProvider b = NoOpBudgetProvider.noOp();
        assertSame(a, b, "noOp() must return the same singleton instance");
        assertTrue(a instanceof NoOpBudgetProvider,
                "Default must be a NoOpBudgetProvider");
    }

    @Test
    void getBudgetReturnsUnlimitedSnapshot() {
        IBudgetProvider provider = NoOpBudgetProvider.noOp();
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "sess-noop");
        ctx.setTokensUsed(1234L);

        BudgetSnapshot snapshot = provider.getBudget(ctx);

        assertTrue(snapshot != null, "getBudget must return a non-null snapshot");
        assertNull(snapshot.getEstimatedTotalCost(),
                "NoOp default must not track cost (estimatedTotalCost=null)");
        assertNull(snapshot.getBudgetLimit(),
                "NoOp default must have no limit (budgetLimit=null)");
        assertFalse(snapshot.isExceeded(),
                "NoOp default snapshot must never be exceeded");
        assertEqualsTokens(1234L, snapshot.getTotalTokensUsed(),
                "NoOp default must carry totalTokensUsed from ctx for consistency");
    }

    @Test
    void getBudgetHandlesNullContextWithoutThrowing() {
        IBudgetProvider provider = NoOpBudgetProvider.noOp();
        BudgetSnapshot snapshot = assertDoesNotThrow(() -> provider.getBudget(null),
                "NoOp must be robust to a null context (defensive)");
        assertFalse(snapshot.isExceeded(),
                "Even with null ctx the NoOp snapshot must be unlimited");
    }

    @Test
    void getBudgetNeverReturnsNull() {
        // Minimum Rules #24: getBudget must never return null.
        IBudgetProvider provider = NoOpBudgetProvider.noOp();
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "sess-nonnull");
        for (int i = 0; i < 5; i++) {
            ctx.setTokensUsed(i * 100L);
            assertTrue(provider.getBudget(ctx) != null,
                    "getBudget must never return null");
        }
    }

    private static void assertEqualsTokens(long expected, long actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + ": expected=" + expected + " actual=" + actual);
        }
    }
}
