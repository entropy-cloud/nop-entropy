package io.nop.ai.agent.quota;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the Phase 1 quota contract surface (plan 234).
 *
 * <p>Covers:
 * <ul>
 *   <li>{@link QuotaDecision} immutable factories + allow/deny invariants +
 *       null-reason rejection.</li>
 *   <li>{@link QuotaConfig} defaults + {@code defaultLimitFor} mapping +
 *       unlimited ({@code <= 0}) values.</li>
 *   <li>{@link NoOpResourceGuard} always-allow across all three dimensions
 *       (zero-regression shipped default).</li>
 *   <li>{@link DefaultResourceGuard} denial semantics: limit {@code <= 0} →
 *       allow (unlimited); {@code projectedCount <= limit} → allow;
 *       {@code projectedCount > limit} → deny with reason; override precedence
 *       over config.</li>
 * </ul>
 */
public class TestQuotaContract {

    // ----- QuotaDecision -----

    @Test
    void allowHasAllowedTrueAndNullReason() {
        QuotaDecision d = QuotaDecision.allow(QuotaDimension.TEAM_MEMBERS, "team-1", 8, 3);

        assertTrue(d.isAllowed());
        assertNull(d.getReason(), "allow decision must have null reason");
        assertEquals(QuotaDimension.TEAM_MEMBERS, d.getDimension());
        assertEquals("team-1", d.getScopeKey());
        assertEquals(8, d.getLimit());
        assertEquals(3, d.getProjectedCount());
    }

    @Test
    void denyHasAllowedFalseAndNonNullReason() {
        QuotaDecision d = QuotaDecision.deny(QuotaDimension.TEAM_MEMBERS, "team-1",
                8, 9, "exceeds");

        assertFalse(d.isAllowed());
        assertNotNull(d.getReason());
        assertEquals(QuotaDimension.TEAM_MEMBERS, d.getDimension());
        assertEquals("team-1", d.getScopeKey());
        assertEquals(8, d.getLimit());
        assertEquals(9, d.getProjectedCount());
    }

    @Test
    void denyRejectsNullReason() {
        assertThrows(NullPointerException.class,
                () -> QuotaDecision.deny(QuotaDimension.TEAM_MEMBERS, "t", 1, 2, null),
                "deny factory must reject a null reason");
    }

    // ----- QuotaConfig -----

    @Test
    void defaultConfigMatchesVisionDefaults() {
        QuotaConfig cfg = new QuotaConfig();
        assertEquals(8, cfg.getTeamMaxMembers());
        assertEquals(10, cfg.getTenantMaxConcurrentActors());
    }

    @Test
    void defaultLimitForMapsDimensions() {
        QuotaConfig cfg = new QuotaConfig(5, 7);
        assertEquals(5, cfg.defaultLimitFor(QuotaDimension.TEAM_MEMBERS));
        assertEquals(7, cfg.defaultLimitFor(QuotaDimension.CONCURRENT_ACTORS_PER_TENANT));
        // TEAM_PARALLEL_BOUND_MEMBERS has no global default → 0 (unlimited);
        // the enforcement point always supplies the per-team override.
        assertEquals(0, cfg.defaultLimitFor(QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS));
    }

    @Test
    void unlimitedConfigValuesAllowed() {
        QuotaConfig cfg = new QuotaConfig(0, -1);
        assertEquals(0, cfg.getTeamMaxMembers());
        assertEquals(-1, cfg.getTenantMaxConcurrentActors());
    }

    // ----- NoOpResourceGuard -----

    @Test
    void noOpAlwaysAllowsAllThreeDimensions() {
        NoOpResourceGuard guard = NoOpResourceGuard.noOp();
        for (QuotaDimension dim : QuotaDimension.values()) {
            QuotaDecision d = guard.checkConcurrent(dim, "scope", 999, 1);
            assertTrue(d.isAllowed(),
                    "NoOp must always allow dimension " + dim + ": " + d);
            assertNull(d.getReason(), "allow has null reason");
        }
    }

    @Test
    void noOpReturnsSingletonInstance() {
        assertSame(NoOpResourceGuard.noOp(), NoOpResourceGuard.noOp(),
                "noOp() must return the same singleton instance");
    }

    @Test
    void noOpCarriesCallerContextVerbatim() {
        NoOpResourceGuard guard = NoOpResourceGuard.noOp();
        QuotaDecision d = guard.checkConcurrent(
                QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS, "team-x", 3, 2);
        assertTrue(d.isAllowed());
        assertEquals(QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS, d.getDimension());
        assertEquals("team-x", d.getScopeKey());
        assertEquals(3, d.getProjectedCount());
        assertEquals(2, d.getLimit());
    }

    // ----- DefaultResourceGuard -----

    @Test
    void defaultGuardAllowsWhenProjectedCountWithinLimit() {
        DefaultResourceGuard guard = new DefaultResourceGuard(new QuotaConfig(3, 2));
        QuotaDecision d = guard.checkConcurrent(
                QuotaDimension.TEAM_MEMBERS, "team-1", 3, 0);
        assertTrue(d.isAllowed());
        assertEquals(3, d.getLimit());
        assertEquals(3, d.getProjectedCount());
    }

    @Test
    void defaultGuardDeniesWhenProjectedCountExceedsLimit() {
        DefaultResourceGuard guard = new DefaultResourceGuard(new QuotaConfig(3, 2));
        QuotaDecision d = guard.checkConcurrent(
                QuotaDimension.TEAM_MEMBERS, "team-1", 4, 0);
        assertFalse(d.isAllowed());
        assertEquals(3, d.getLimit());
        assertEquals(4, d.getProjectedCount());
        assertNotNull(d.getReason());
        assertTrue(d.getReason().contains("TEAM_MEMBERS"));
        assertTrue(d.getReason().contains("team-1"));
    }

    @Test
    void defaultGuardAllowsWhenConfigLimitIsZeroUnlimited() {
        DefaultResourceGuard guard = new DefaultResourceGuard(new QuotaConfig(0, 0));
        // config limit 0 (unlimited) + override 0 (no override) → unlimited allow
        QuotaDecision d = guard.checkConcurrent(
                QuotaDimension.TEAM_MEMBERS, "team-1", 99999, 0);
        assertTrue(d.isAllowed(), "limit <= 0 = unlimited must allow");
        assertTrue(d.getLimit() <= 0);
    }

    @Test
    void defaultGuardOverridePrecedenceOverConfig() {
        // config teamMaxMembers=8, but caller supplies override=2
        DefaultResourceGuard guard = new DefaultResourceGuard(new QuotaConfig(8, 10));
        QuotaDecision within = guard.checkConcurrent(
                QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS, "team-1", 2, 2);
        QuotaDecision exceed = guard.checkConcurrent(
                QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS, "team-1", 3, 2);

        assertTrue(within.isAllowed());
        assertEquals(2, within.getLimit(), "override must take precedence over config");
        assertFalse(exceed.isAllowed());
        assertEquals(2, exceed.getLimit());
        assertNotNull(exceed.getReason());
    }

    @Test
    void defaultGuardOverrideZeroFallsBackToConfig() {
        // override <= 0 → use config default
        DefaultResourceGuard guard = new DefaultResourceGuard(new QuotaConfig(3, 2));
        QuotaDecision d = guard.checkConcurrent(
                QuotaDimension.TEAM_MEMBERS, "team-1", 4, 0);
        assertFalse(d.isAllowed());
        assertEquals(3, d.getLimit(), "override <= 0 must fall back to config default");
    }

    @Test
    void defaultGuardDeniesAcrossAllThreeDimensions() {
        DefaultResourceGuard guard = new DefaultResourceGuard(new QuotaConfig(2, 2));
        // TEAM_MEMBERS (config-driven)
        assertFalse(guard.checkConcurrent(QuotaDimension.TEAM_MEMBERS, "t", 3, 0).isAllowed());
        // CONCURRENT_ACTORS_PER_TENANT (config-driven)
        assertFalse(guard.checkConcurrent(
                QuotaDimension.CONCURRENT_ACTORS_PER_TENANT, "tenant-a", 3, 0).isAllowed());
        // TEAM_PARALLEL_BOUND_MEMBERS (override-driven)
        assertFalse(guard.checkConcurrent(
                QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS, "t", 3, 2).isAllowed());
    }

    @Test
    void defaultGuardDefaultConstructorUsesVisionDefaults() {
        DefaultResourceGuard guard = new DefaultResourceGuard();
        // teamMaxMembers=8 → projectedCount 8 within, 9 denied
        assertTrue(guard.checkConcurrent(QuotaDimension.TEAM_MEMBERS, "t", 8, 0).isAllowed());
        assertFalse(guard.checkConcurrent(QuotaDimension.TEAM_MEMBERS, "t", 9, 0).isAllowed());
    }

    @Test
    void defaultGuardRejectsNullDimension() {
        DefaultResourceGuard guard = new DefaultResourceGuard();
        assertThrows(NullPointerException.class,
                () -> guard.checkConcurrent(null, "scope", 1, 0));
    }
}
