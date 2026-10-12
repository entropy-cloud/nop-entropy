package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.ISecurityLevelResolver;
import io.nop.ai.agent.security.LevelHints;
import io.nop.ai.agent.security.SecurityLevel;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 tests: verifies {@link DefaultAgentEngine} security-level-resolver
 * wiring, the {@link DefaultSecurityLevelResolver} default, and set/get
 * identity.
 *
 * <p>These tests do NOT run a full agent execution — they verify the resolver is
 * correctly wired into the engine and can be retrieved by dispatch-path
 * consumers.
 */
public class TestDefaultAgentEngineSecurityLevelResolverWiring {

    @Test
    void setSecurityLevelResolverAndGetSecurityLevelResolverReturnSameInstance() {
        DefaultAgentEngine engine = newEngineStub();
        ISecurityLevelResolver custom = new ISecurityLevelResolver() {
            @Override
            public LevelHints produce(String toolName, Map<String, Object> arguments, File workDir,
                                      AgentExecutionContext ctx) {
                return LevelHints.defaults();
            }

            @Override
            public SecurityLevel resolve(String actionKind, LevelHints hints) {
                return SecurityLevel.ELEVATED;
            }
        };

        engine.config().setSecurityLevelResolver(custom);

        assertSame(custom, engine.config().getSecurityLevelResolver(),
                "resolver retrieved from engine must be the same instance set via setSecurityLevelResolver");
    }

    @Test
    void setSecurityLevelResolverNullFallsBackToDefaultSecurityLevelResolver() {
        DefaultAgentEngine engine = newEngineStub();
        engine.config().setSecurityLevelResolver(null);
        ISecurityLevelResolver retrieved = engine.config().getSecurityLevelResolver();
        assertTrue(retrieved instanceof DefaultSecurityLevelResolver,
                "setSecurityLevelResolver(null) must fall back to DefaultSecurityLevelResolver, got: " + retrieved.getClass());
    }

    @Test
    void defaultResolverIsDefaultSecurityLevelResolverWhenNeverSet() {
        DefaultAgentEngine engine = newEngineStub();
        ISecurityLevelResolver resolver = engine.config().getSecurityLevelResolver();
        assertTrue(resolver instanceof DefaultSecurityLevelResolver,
                "engine constructed without setSecurityLevelResolver must default to DefaultSecurityLevelResolver, got: "
                        + (resolver == null ? "null" : resolver.getClass()));
    }

    @Test
    void defaultResolverUsesTrustedByDefaultVariant() {
        DefaultAgentEngine engine = newEngineStub();
        ISecurityLevelResolver resolver = engine.config().getSecurityLevelResolver();
        // trusted source + no risk signals → STANDARD
        assertEquals(SecurityLevel.STANDARD,
                resolver.resolve("fs.read", new LevelHints(true, false, false, false, false)),
                "trusted fs.read must resolve to STANDARD");
        // trusted source + highImpact → ELEVATED (not RESTRICTED)
        assertEquals(SecurityLevel.ELEVATED,
                resolver.resolve("shell.exec", new LevelHints(true, false, false, false, true)),
                "trusted shell.exec (highImpact) must resolve to ELEVATED, not RESTRICTED");
        // untrusted + highImpact → RESTRICTED
        assertEquals(SecurityLevel.RESTRICTED,
                resolver.resolve("shell.exec", new LevelHints(false, false, false, false, true)),
                "untrusted shell.exec (highImpact) must resolve to RESTRICTED");
    }

    @Test
    void existingEngineConstructionPathsAreUnchanged() {
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(null, null).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();
        ISecurityLevelResolver resolver = engine.config().getSecurityLevelResolver();
        assertTrue(resolver instanceof DefaultSecurityLevelResolver);
    }

    private DefaultAgentEngine newEngineStub() {
        return new DefaultAgentEngine.Builder(null, null).sessionStore(new InMemorySessionStore()).permissionProvider(new AllowAllPermissionProvider()).toolAccessChecker(new DefaultToolAccessChecker()).pathAccessChecker(new DefaultPathAccessChecker()).contentGuardrail(NoOpContentGuardrail.noOp()).modelRouter(PassThroughModelRouter.passThrough()).contextCompactor(new MicroCompressionCompactor()).build();
    }
}
