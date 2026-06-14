package io.nop.ai.agent.engine;

import io.nop.ai.agent.security.ISecurityLevelResolver;
import io.nop.ai.agent.security.LevelHints;
import io.nop.ai.agent.security.NoOpSecurityLevelResolver;
import io.nop.ai.agent.security.SecurityLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 tests: verifies {@link DefaultAgentEngine} security-level-resolver
 * wiring, the NoOp default backward-compat, and set/get identity.
 *
 * <p>These tests do NOT run a full agent execution — they verify the resolver is
 * correctly wired into the engine and can be retrieved by future dispatch-path
 * consumers. The NoOp default makes the wiring transparent to runtime behaviour
 * (all operations resolve to STANDARD, equivalent to no classification).
 */
public class TestDefaultAgentEngineSecurityLevelResolverWiring {

    // ========================================================================
    // Wiring: setSecurityLevelResolver / getSecurityLevelResolver round-trip
    // ========================================================================

    @Test
    void setSecurityLevelResolverAndGetSecurityLevelResolverReturnSameInstance() {
        DefaultAgentEngine engine = newEngineStub();
        ISecurityLevelResolver custom = (actionKind, hints) -> SecurityLevel.ELEVATED;

        engine.setSecurityLevelResolver(custom);

        assertSame(custom, engine.getSecurityLevelResolver(),
                "resolver retrieved from engine must be the same instance set via setSecurityLevelResolver");
    }

    @Test
    void setSecurityLevelResolverNullFallsBackToNoOp() {
        DefaultAgentEngine engine = newEngineStub();
        engine.setSecurityLevelResolver(null);
        ISecurityLevelResolver retrieved = engine.getSecurityLevelResolver();
        assertTrue(retrieved instanceof NoOpSecurityLevelResolver,
                "setSecurityLevelResolver(null) must fall back to NoOp, got: " + retrieved.getClass());
        assertSame(NoOpSecurityLevelResolver.noOp(), retrieved,
                "null fallback must be the NoOp singleton");
    }

    @Test
    void defaultResolverIsNoOpWhenNeverSet() {
        DefaultAgentEngine engine = newEngineStub();
        ISecurityLevelResolver resolver = engine.getSecurityLevelResolver();
        assertTrue(resolver instanceof NoOpSecurityLevelResolver,
                "engine constructed without setSecurityLevelResolver must default to NoOp, got: "
                        + (resolver == null ? "null" : resolver.getClass()));
        assertSame(NoOpSecurityLevelResolver.noOp(), resolver,
                "default NoOp resolver must be the singleton instance");
    }

    // ========================================================================
    // Backward-compat: default NoOp resolves everything to STANDARD
    // ========================================================================

    @Test
    void defaultNoOpResolverReturnsStandardForAllInputsInEngineContext() {
        DefaultAgentEngine engine = newEngineStub();
        ISecurityLevelResolver resolver = engine.getSecurityLevelResolver();
        String[] actionKinds = {"fs.read", "fs.write", "shell.exec", "network.fetch", "unknown", null};
        for (String actionKind : actionKinds) {
            LevelHints hints = new LevelHints(true, true, true, true, true);
            assertEquals(SecurityLevel.STANDARD, resolver.resolve(actionKind, hints),
                    "default NoOp must resolve actionKind=" + actionKind
                            + " to STANDARD — no spurious classification from the default wiring");
        }
    }

    @Test
    void existingEngineConstructionPathsAreUnchanged() {
        // Engine constructed via the simplest constructor (chatService, toolManager)
        // must still work and default to NoOp resolver — no new required args.
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        ISecurityLevelResolver resolver = engine.getSecurityLevelResolver();
        assertTrue(resolver instanceof NoOpSecurityLevelResolver);
    }

    // ========================================================================
    // Helper
    // ========================================================================

    /**
     * Construct a DefaultAgentEngine without running CoreInitialization — the
     * resolver wiring tests don't need agent model loading. Passing null
     * chatService/toolManager is fine because we never invoke execute().
     */
    private DefaultAgentEngine newEngineStub() {
        return new DefaultAgentEngine(null, null);
    }
}
