package io.nop.ai.agent.engine;

import io.nop.ai.agent.security.DefaultLevelHintsProducer;
import io.nop.ai.agent.security.ILevelHintsProducer;
import io.nop.ai.agent.security.LevelHints;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 wiring tests: verifies {@link DefaultAgentEngine} level-hints-producer
 * wiring, the {@link DefaultLevelHintsProducer} default, set/get identity, and
 * the null-fallback.
 *
 * <p>These tests do NOT run a full agent execution — they verify the producer is
 * correctly wired into the engine and can be retrieved by the dispatch-path
 * consumer (Phase 3).
 */
public class TestDefaultAgentEngineLevelHintsProducerWiring {

    @Test
    void setLevelHintsProducerAndGetLevelHintsProducerReturnSameInstance() {
        DefaultAgentEngine engine = newEngineStub();
        ILevelHintsProducer custom = (toolName, arguments, workDir, ctx) -> LevelHints.defaults();

        engine.setLevelHintsProducer(custom);

        assertSame(custom, engine.getLevelHintsProducer(),
                "producer retrieved from engine must be the same instance set via setLevelHintsProducer");
    }

    @Test
    void setLevelHintsProducerNullFallsBackToDefault() {
        DefaultAgentEngine engine = newEngineStub();
        engine.setLevelHintsProducer(null);
        ILevelHintsProducer retrieved = engine.getLevelHintsProducer();
        assertTrue(retrieved instanceof DefaultLevelHintsProducer,
                "setLevelHintsProducer(null) must fall back to DefaultLevelHintsProducer, got: "
                        + retrieved.getClass());
    }

    @Test
    void defaultProducerIsDefaultLevelHintsProducerWhenNeverSet() {
        DefaultAgentEngine engine = newEngineStub();
        ILevelHintsProducer producer = engine.getLevelHintsProducer();
        assertTrue(producer instanceof DefaultLevelHintsProducer,
                "engine constructed without setLevelHintsProducer must default to "
                        + "DefaultLevelHintsProducer, got: " + producer.getClass());
    }

    @Test
    void defaultProducerProducesSemanticallyDistinctHintsInEngineContext() {
        // Anti-Hollow: the shipped default is functional, not an all-false stub.
        DefaultAgentEngine engine = newEngineStub();
        ILevelHintsProducer producer = engine.getLevelHintsProducer();

        LevelHints readHints = producer.produce("fs.read", Map.of("path", "in.txt"),
                new File(System.getProperty("user.dir")), null);
        LevelHints shellHints = producer.produce("shell.exec", Map.of(),
                new File(System.getProperty("user.dir")), null);

        assertTrue(shellHints.isHighImpact() && !readHints.isHighImpact(),
                "default producer must distinguish highImpact across tool categories");
    }

    @Test
    void existingEngineConstructionPathsAreUnchanged() {
        // Engine constructed via the simplest constructor (chatService, toolManager)
        // must still work and default to DefaultLevelHintsProducer — no new required args.
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        ILevelHintsProducer producer = engine.getLevelHintsProducer();
        assertInstanceOf(DefaultLevelHintsProducer.class, producer);
    }

    // ========================================================================
    // Helper
    // ========================================================================

    private DefaultAgentEngine newEngineStub() {
        return new DefaultAgentEngine(null, null);
    }
}
