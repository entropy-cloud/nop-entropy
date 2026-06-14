package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 tests for {@link PassThroughPermissionMatrix} and the
 * {@link IPermissionMatrix} contract.
 *
 * <p>Covers three concerns:
 * <ol>
 *   <li>Pass-through default: all channel × level × role combinations return
 *       allow.</li>
 *   <li>Anti-Hollow check: a custom restrictive matrix implementing the design
 *       §5.3 channel × level table proves the contract can produce real
 *       allow/deny decisions (not a hollow shell that always returns true).</li>
 *   <li>Singleton identity: {@code passThrough()} returns the same instance.</li>
 * </ol>
 */
public class TestPassThroughPermissionMatrix {

    // ========================================================================
    // Pass-through default: all combinations allow
    // ========================================================================

    @Test
    void passThroughAllowsAllChannelLevelAndRoleCombinations() {
        IPermissionMatrix matrix = PassThroughPermissionMatrix.passThrough();
        for (ChannelKind channel : ChannelKind.values()) {
            for (SecurityLevel level : SecurityLevel.values()) {
                for (PrincipalRole role : PrincipalRole.values()) {
                    Principal principal = new Principal(role, "ch-" + channel, "tenant");
                    MatrixDecision decision = matrix.check(channel, principal, level);
                    assertTrue(decision.isAllowed(),
                            "pass-through must allow: channel=" + channel + ", level=" + level + ", role=" + role);
                }
            }
        }
    }

    @Test
    void passThroughAllowsNullChannelAndNullPrincipal() {
        IPermissionMatrix matrix = PassThroughPermissionMatrix.passThrough();
        MatrixDecision d1 = matrix.check(null, null, SecurityLevel.STANDARD);
        MatrixDecision d2 = matrix.check(null, null, SecurityLevel.RESTRICTED);
        assertTrue(d1.isAllowed());
        assertTrue(d2.isAllowed());
    }

    @Test
    void passThroughReturnsSingletonInstance() {
        IPermissionMatrix a = PassThroughPermissionMatrix.passThrough();
        IPermissionMatrix b = PassThroughPermissionMatrix.passThrough();
        assertSame(a, b, "passThrough() must return the same singleton instance");
    }

    @Test
    void passThroughAllowDecisionHasNoReason() {
        IPermissionMatrix matrix = PassThroughPermissionMatrix.passThrough();
        MatrixDecision decision = matrix.check(ChannelKind.API, Principal.user(), SecurityLevel.ELEVATED);
        assertTrue(decision.isAllowed());
        assertEquals(null, decision.getReason());
    }

    // ========================================================================
    // Anti-Hollow: custom restrictive matrix proves the contract is functional
    // ========================================================================

    /**
     * A restrictive matrix implementing the design §5.3 channel × level table.
     * This is a TEST-ONLY implementation (not shipped product code) that proves
     * the {@link IPermissionMatrix} contract can produce real allow/deny
     * decisions.
     *
     * <table>
     *   <tr><th>Channel</th><th>Allowed levels</th></tr>
     *   <tr><td>WEBUI</td><td>STANDARD + ELEVATED + RESTRICTED</td></tr>
     *   <tr><td>API</td><td>STANDARD + ELEVATED</td></tr>
     *   <tr><td>DM</td><td>STANDARD + ELEVATED</td></tr>
     *   <tr><td>GROUP</td><td>STANDARD</td></tr>
     *   <tr><td>unknown / null</td><td>STANDARD (fail-closed)</td></tr>
     * </table>
     *
     * <p>An {@link PrincipalRole#OPERATOR} may bypass
     * {@link SecurityLevel#RESTRICTED}.
     */
    static final class DesignSpecRestrictiveMatrix implements IPermissionMatrix {
        @Override
        public MatrixDecision check(ChannelKind channel, Principal principal, SecurityLevel level) {
            // Operator bypass for RESTRICTED
            if (level == SecurityLevel.RESTRICTED
                    && principal != null
                    && principal.getRole() == PrincipalRole.OPERATOR) {
                return MatrixDecision.allow();
            }

            // STANDARD is always allowed on any channel
            if (level == SecurityLevel.STANDARD) {
                return MatrixDecision.allow();
            }

            // Non-STANDARD levels: channel determines the cap
            SecurityLevel cap = channelCap(channel);
            if (level.ordinal() <= cap.ordinal()) {
                return MatrixDecision.allow();
            }
            return MatrixDecision.deny(channel, level,
                    "channel " + channel + " does not allow security level " + level
                            + " (cap=" + cap + ")");
        }

        private static SecurityLevel channelCap(ChannelKind channel) {
            if (channel == null) {
                return SecurityLevel.STANDARD; // fail-closed
            }
            switch (channel) {
                case WEBUI:
                    return SecurityLevel.RESTRICTED;
                case API:
                case DM:
                    return SecurityLevel.ELEVATED;
                case GROUP:
                    return SecurityLevel.STANDARD;
                default:
                    return SecurityLevel.STANDARD; // fail-closed
            }
        }
    }

    @Test
    void restrictiveMatrixAllowsWebuiAllLevels() {
        IPermissionMatrix matrix = new DesignSpecRestrictiveMatrix();
        for (SecurityLevel level : SecurityLevel.values()) {
            MatrixDecision d = matrix.check(ChannelKind.WEBUI, Principal.user(), level);
            assertTrue(d.isAllowed(),
                    "WEBUI must allow " + level + " for a user");
        }
    }

    @Test
    void restrictiveMatrixDeniesApiRestrictedForUser() {
        IPermissionMatrix matrix = new DesignSpecRestrictiveMatrix();
        MatrixDecision d = matrix.check(ChannelKind.API, Principal.user(), SecurityLevel.RESTRICTED);
        assertTrue(d.isDenied(), "API + RESTRICTED + user must be denied");
        assertNotNull(d.getReason(), "denial must carry an auditable reason");
        assertEquals(ChannelKind.API, d.getChannel(), "denial must carry the channel context");
        assertEquals(SecurityLevel.RESTRICTED, d.getLevel(), "denial must carry the level context");
    }

    @Test
    void restrictiveMatrixAllowsApiStandardAndElevated() {
        IPermissionMatrix matrix = new DesignSpecRestrictiveMatrix();
        assertTrue(matrix.check(ChannelKind.API, Principal.user(), SecurityLevel.STANDARD).isAllowed());
        assertTrue(matrix.check(ChannelKind.API, Principal.user(), SecurityLevel.ELEVATED).isAllowed());
    }

    @Test
    void restrictiveMatrixDeniesGroupElevatedAndRestricted() {
        IPermissionMatrix matrix = new DesignSpecRestrictiveMatrix();
        assertTrue(matrix.check(ChannelKind.GROUP, Principal.user(), SecurityLevel.STANDARD).isAllowed());
        assertTrue(matrix.check(ChannelKind.GROUP, Principal.user(), SecurityLevel.ELEVATED).isDenied());
        assertTrue(matrix.check(ChannelKind.GROUP, Principal.user(), SecurityLevel.RESTRICTED).isDenied());
    }

    @Test
    void restrictiveMatrixOperatorBypassesRestricted() {
        IPermissionMatrix matrix = new DesignSpecRestrictiveMatrix();
        // GROUP normally caps at STANDARD, but operator bypasses RESTRICTED
        MatrixDecision d = matrix.check(ChannelKind.GROUP, Principal.operator(), SecurityLevel.RESTRICTED);
        assertTrue(d.isAllowed(), "operator must bypass RESTRICTED even on GROUP channel");
        // API + RESTRICTED + operator → bypass
        assertTrue(matrix.check(ChannelKind.API, Principal.operator(), SecurityLevel.RESTRICTED).isAllowed());
        // DM + RESTRICTED + operator → bypass
        assertTrue(matrix.check(ChannelKind.DM, Principal.operator(), SecurityLevel.RESTRICTED).isAllowed());
    }

    @Test
    void restrictiveMatrixOperatorDoesNotBypassElevatedOnGroup() {
        IPermissionMatrix matrix = new DesignSpecRestrictiveMatrix();
        // Operator bypass is for RESTRICTED only; ELEVATED on GROUP is still denied
        MatrixDecision d = matrix.check(ChannelKind.GROUP, Principal.operator(), SecurityLevel.ELEVATED);
        assertTrue(d.isDenied(), "operator bypass applies to RESTRICTED only, not ELEVATED on GROUP");
    }

    @Test
    void restrictiveMatrixFailClosedForNullChannel() {
        IPermissionMatrix matrix = new DesignSpecRestrictiveMatrix();
        // null channel → fail-closed → only STANDARD allowed
        assertTrue(matrix.check(null, Principal.user(), SecurityLevel.STANDARD).isAllowed());
        assertTrue(matrix.check(null, Principal.user(), SecurityLevel.ELEVATED).isDenied());
        assertTrue(matrix.check(null, Principal.user(), SecurityLevel.RESTRICTED).isDenied());
    }

    @Test
    void restrictiveMatrixDmBehavesLikeApi() {
        IPermissionMatrix matrix = new DesignSpecRestrictiveMatrix();
        for (SecurityLevel level : SecurityLevel.values()) {
            MatrixDecision dm = matrix.check(ChannelKind.DM, Principal.user(), level);
            MatrixDecision api = matrix.check(ChannelKind.API, Principal.user(), level);
            assertEquals(dm.isAllowed(), api.isAllowed(),
                    "DM and API must have the same policy for " + level);
        }
    }

    @Test
    void matrixDecisionFactoriesProduceCorrectAllowedFlag() {
        assertTrue(MatrixDecision.allow().isAllowed());
        assertTrue(MatrixDecision.deny("test").isDenied());
        assertTrue(MatrixDecision.deny(ChannelKind.API, SecurityLevel.RESTRICTED, "cap").isDenied());
        assertEquals("test", MatrixDecision.deny("test").getReason());
        assertEquals(ChannelKind.API,
                MatrixDecision.deny(ChannelKind.API, SecurityLevel.RESTRICTED, "cap").getChannel());
        assertEquals(SecurityLevel.RESTRICTED,
                MatrixDecision.deny(ChannelKind.API, SecurityLevel.RESTRICTED, "cap").getLevel());
    }
}
