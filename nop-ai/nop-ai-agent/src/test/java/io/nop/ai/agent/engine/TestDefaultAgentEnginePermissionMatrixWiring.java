package io.nop.ai.agent.engine;

import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.IPermissionMatrix;
import io.nop.ai.agent.security.MatrixDecision;
import io.nop.ai.agent.security.PassThroughPermissionMatrix;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.SecurityLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 tests: verifies {@link DefaultAgentEngine} permission-matrix wiring,
 * the PassThrough default backward-compat, and set/get identity.
 *
 * <p>These tests do NOT run a full agent execution — they verify the matrix is
 * correctly wired into the engine and can be retrieved by future dispatch-path
 * consumers / the L2-13 successor. The pass-through default makes the wiring
 * transparent to runtime behaviour (no spurious denials).
 */
public class TestDefaultAgentEnginePermissionMatrixWiring {

    // ========================================================================
    // Wiring: setPermissionMatrix / getPermissionMatrix round-trip
    // ========================================================================

    @Test
    void setPermissionMatrixAndGetPermissionMatrixReturnSameInstance() {
        DefaultAgentEngine engine = newEngineStub();
        IPermissionMatrix custom = (channel, principal, level) -> MatrixDecision.allow();

        engine.setPermissionMatrix(custom);

        assertSame(custom, engine.getPermissionMatrix(),
                "matrix retrieved from engine must be the same instance set via setPermissionMatrix");
    }

    @Test
    void setPermissionMatrixNullFallsBackToPassThrough() {
        DefaultAgentEngine engine = newEngineStub();
        engine.setPermissionMatrix(null);
        IPermissionMatrix retrieved = engine.getPermissionMatrix();
        assertTrue(retrieved instanceof PassThroughPermissionMatrix,
                "setPermissionMatrix(null) must fall back to PassThrough, got: " + retrieved.getClass());
        assertSame(PassThroughPermissionMatrix.passThrough(), retrieved,
                "null fallback must be the PassThrough singleton");
    }

    @Test
    void defaultMatrixIsPassThroughWhenNeverSet() {
        DefaultAgentEngine engine = newEngineStub();
        IPermissionMatrix matrix = engine.getPermissionMatrix();
        assertTrue(matrix instanceof PassThroughPermissionMatrix,
                "engine constructed without setPermissionMatrix must default to PassThrough, got: "
                        + (matrix == null ? "null" : matrix.getClass()));
        assertSame(PassThroughPermissionMatrix.passThrough(), matrix,
                "default PassThrough matrix must be the singleton instance");
    }

    // ========================================================================
    // Backward-compat: default PassThrough allows everything (no spurious deny)
    // ========================================================================

    @Test
    void defaultPassThroughMatrixAllowsAllInputsInEngineContext() {
        DefaultAgentEngine engine = newEngineStub();
        IPermissionMatrix matrix = engine.getPermissionMatrix();
        for (ChannelKind channel : ChannelKind.values()) {
            for (SecurityLevel level : SecurityLevel.values()) {
                MatrixDecision d = matrix.check(channel, Principal.user(), level);
                assertTrue(d.isAllowed(),
                        "default PassThrough must allow channel=" + channel + ", level=" + level
                                + " — no spurious denials from the default wiring");
            }
        }
    }

    @Test
    void existingEngineConstructionPathsAreUnchanged() {
        // Engine constructed via the simplest constructor (chatService, toolManager)
        // must still work and default to PassThrough matrix — no new required args.
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        IPermissionMatrix matrix = engine.getPermissionMatrix();
        assertTrue(matrix instanceof PassThroughPermissionMatrix);
    }

    // ========================================================================
    // Helper
    // ========================================================================

    /**
     * Construct a DefaultAgentEngine without running CoreInitialization — the
     * matrix wiring tests don't need agent model loading. Passing null
     * chatService/toolManager is fine because we never invoke execute().
     */
    private DefaultAgentEngine newEngineStub() {
        return new DefaultAgentEngine(null, null);
    }
}
