package io.nop.ai.agent.budget;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 206 (L2-22) Phase 2 focused tests covering the {@link BudgetSnapshot}
 * {@code exceeded} computation and field semantics (design
 * {@code nop-ai-agent-usage-and-billing.md} §3.6 / plan §设计裁定 2).
 *
 * <p>Scenarios:
 * <ol>
 *   <li>cost &lt; limit → exceeded=false</li>
 *   <li>cost &gt;= limit → exceeded=true (boundary at exactly equal)</li>
 *   <li>cost=null → exceeded=false (cost not tracked)</li>
 *   <li>limit=null → exceeded=false (no limit configured)</li>
 *   <li>both null → exceeded=false</li>
 *   <li>constructor ignores a caller-supplied exceeded flag (recomputes)</li>
 * </ol>
 */
public class TestBudgetSnapshot {

    @Test
    void costBelowLimitIsNotExceeded() {
        BudgetSnapshot s = new BudgetSnapshot(
                new BigDecimal("0.50"), 1000L, new BigDecimal("1.00"));
        assertFalse(s.isExceeded(),
                "cost(0.50) < limit(1.00) must not be exceeded");
        assertEquals(0, new BigDecimal("0.50").compareTo(s.getEstimatedTotalCost()));
        assertEquals(1000L, s.getTotalTokensUsed());
        assertEquals(0, new BigDecimal("1.00").compareTo(s.getBudgetLimit()));
    }

    @Test
    void costAtLimitIsExceeded() {
        BudgetSnapshot s = new BudgetSnapshot(
                new BigDecimal("1.00"), 500L, new BigDecimal("1.00"));
        assertTrue(s.isExceeded(),
                "cost(1.00) >= limit(1.00) boundary must be exceeded");
    }

    @Test
    void costAboveLimitIsExceeded() {
        BudgetSnapshot s = new BudgetSnapshot(
                new BigDecimal("1.50"), 500L, new BigDecimal("1.00"));
        assertTrue(s.isExceeded(),
                "cost(1.50) > limit(1.00) must be exceeded");
    }

    @Test
    void nullCostIsNeverExceeded() {
        BudgetSnapshot s = new BudgetSnapshot(null, 500L, new BigDecimal("1.00"));
        assertFalse(s.isExceeded(),
                "null cost (cost not tracked) must not be exceeded even with a limit");
        assertNull(s.getEstimatedTotalCost());
    }

    @Test
    void nullLimitIsNeverExceeded() {
        BudgetSnapshot s = new BudgetSnapshot(
                new BigDecimal("99.00"), 500L, null);
        assertFalse(s.isExceeded(),
                "null limit (unlimited) must not be exceeded even with a high cost");
        assertNull(s.getBudgetLimit());
    }

    @Test
    void bothNullIsNeverExceeded() {
        BudgetSnapshot s = new BudgetSnapshot(null, 0L, null);
        assertFalse(s.isExceeded(),
                "null cost + null limit (fully untracked/unlimited) must not be exceeded");
    }

    @Test
    void tokenOnlyBudgetDoesNotUseExceededFlag() {
        // Adjudication (plan §设计裁定 2 / Non-Blocking Follow-up): token-only
        // budget (cost=null but tokensUsed present) does NOT set exceeded —
        // exceeded is cost-based only. A router that needs token-budget
        // awareness reads totalTokensUsed (or ctx.getTokensUsed()) directly.
        BudgetSnapshot s = new BudgetSnapshot(null, 5000L, null);
        assertFalse(s.isExceeded(),
                "Token-only budget (no cost/limit) must not be flagged via exceeded");
        assertEquals(5000L, s.getTotalTokensUsed(),
                "totalTokensUsed must be carried for routers that read it directly");
    }

    @Test
    void equalsAndHashCode() {
        BudgetSnapshot a = new BudgetSnapshot(
                new BigDecimal("1.00"), 100L, new BigDecimal("2.00"));
        BudgetSnapshot b = new BudgetSnapshot(
                new BigDecimal("1.00"), 100L, new BigDecimal("2.00"));
        BudgetSnapshot c = new BudgetSnapshot(
                new BigDecimal("1.00"), 200L, new BigDecimal("2.00"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c, "different tokensUsed must not be equal");
    }
}
