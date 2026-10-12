package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.IPermissionMatrix;
import io.nop.ai.agent.security.MatrixDecision;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.PrincipalRole;
import io.nop.ai.agent.security.SecurityLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 tests: verifies {@link DefaultAgentEngine} permission-matrix wiring,
 * the {@link DefaultPermissionMatrix} default, and set/get identity.
 *
 * <p>These tests do NOT run a full agent execution — they verify the matrix is
 * correctly wired into the engine and can be retrieved by dispatch-path
 * consumers.
 */
public class TestDefaultAgentEnginePermissionMatrixWiring {

    @Test
    void setPermissionMatrixAndGetPermissionMatrixReturnSameInstance() {
        DefaultAgentEngine engine = newEngineStub();
        IPermissionMatrix custom = (channel, principal, level) -> MatrixDecision.allow();

        engine.config().setPermissionMatrix(custom);

        assertSame(custom, engine.config().getPermissionMatrix(),
                "matrix retrieved from engine must be the same instance set via setPermissionMatrix");
    }

    @Test
    void setPermissionMatrixNullFallsBackToDefaultPermissionMatrix() {
        DefaultAgentEngine engine = newEngineStub();
        engine.config().setPermissionMatrix(null);
        IPermissionMatrix retrieved = engine.config().getPermissionMatrix();
        assertTrue(retrieved instanceof DefaultPermissionMatrix,
                "setPermissionMatrix(null) must fall back to DefaultPermissionMatrix, got: " + retrieved.getClass());
    }

    @Test
    void defaultMatrixIsDefaultPermissionMatrixWhenNeverSet() {
        DefaultAgentEngine engine = newEngineStub();
        IPermissionMatrix matrix = engine.config().getPermissionMatrix();
        assertTrue(matrix instanceof DefaultPermissionMatrix,
                "engine constructed without setPermissionMatrix must default to DefaultPermissionMatrix, got: "
                        + (matrix == null ? "null" : matrix.getClass()));
    }

    @Test
    void defaultPermissionMatrixEnforcesChannelLevelRestrictions() {
        DefaultAgentEngine engine = newEngineStub();
        IPermissionMatrix matrix = engine.config().getPermissionMatrix();
        // STANDARD is allowed on all channels
        for (ChannelKind channel : ChannelKind.values()) {
            MatrixDecision d = matrix.check(channel, Principal.user(), SecurityLevel.STANDARD);
            assertTrue(d.isAllowed(),
                    "DefaultPermissionMatrix must allow STANDARD on channel=" + channel);
        }
        // RESTRICTED denied for USER except WEBUI
        assertTrue(matrix.check(ChannelKind.GROUP, Principal.user(), SecurityLevel.RESTRICTED).isDenied(),
                "GROUP + RESTRICTED must be denied for USER");
        assertTrue(matrix.check(null, Principal.user(), SecurityLevel.RESTRICTED).isDenied(),
                "null channel + RESTRICTED must be denied for USER");
        // OPERATOR bypasses RESTRICTED
        Principal operator = new Principal(PrincipalRole.OPERATOR, null, null);
        assertTrue(matrix.check(null, operator, SecurityLevel.RESTRICTED).isAllowed(),
                "null channel + RESTRICTED must be allowed for OPERATOR");
    }

    @Test
    void existingEngineConstructionPathsAreUnchanged() {
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(null, null).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();
        IPermissionMatrix matrix = engine.config().getPermissionMatrix();
        assertTrue(matrix instanceof DefaultPermissionMatrix);
    }

    private DefaultAgentEngine newEngineStub() {
        return new DefaultAgentEngine.Builder(null, null).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();
    }
}
