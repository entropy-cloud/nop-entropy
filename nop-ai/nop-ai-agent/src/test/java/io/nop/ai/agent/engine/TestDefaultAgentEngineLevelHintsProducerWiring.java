package io.nop.ai.agent.engine;

import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.LevelHints;
import io.nop.ai.agent.security.SecurityLevel;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link DefaultSecurityLevelResolver} (which replaced
 * {@code ILevelHintsProducer} per plan 304 Phase 6) correctly produces
 * {@link LevelHints} and composes with {@code resolve()}.
 */
public class TestDefaultAgentEngineLevelHintsProducerWiring {

    @Test
    void defaultResolverProducesSemanticallyDistinctHints() {
        DefaultSecurityLevelResolver resolver = new DefaultSecurityLevelResolver();
        LevelHints readHints = resolver.produce("fs.read", Map.of("path", "in.txt"),
                new File(System.getProperty("user.dir")), null);
        LevelHints shellHints = resolver.produce("shell.exec", Map.of(),
                new File(System.getProperty("user.dir")), null);

        assertTrue(shellHints.isHighImpact() && !readHints.isHighImpact(),
                "default resolver must distinguish highImpact across tool categories");
    }

    @Test
    void produceAndResolveComposeCorrectly() {
        DefaultSecurityLevelResolver resolver = new DefaultSecurityLevelResolver();
        LevelHints hints = resolver.produce("fs.read", Map.of("path", "in.txt"),
                new File(System.getProperty("user.dir")), null);
        SecurityLevel level = resolver.resolve("fs.read", hints);

        assertNotNull(level, "resolve must return non-null level");
        assertEquals(SecurityLevel.STANDARD, level,
                "fs.read with in-workspace path must resolve to STANDARD");
    }

    @Test
    void shellExecProducesHighImpactAndResolvesToElevated() {
        DefaultSecurityLevelResolver resolver = new DefaultSecurityLevelResolver();
        LevelHints hints = resolver.produce("shell.exec", Map.of("command", "ls"),
                new File(System.getProperty("user.dir")), null);
        SecurityLevel level = resolver.resolve("shell.exec", hints);

        assertTrue(hints.isHighImpact(), "shell.exec must produce highImpact hint");
        assertEquals(SecurityLevel.ELEVATED, level,
                "trusted shell.exec must resolve to ELEVATED");
    }

    @Test
    void existingEngineConstructionPathsAreUnchanged() {
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        assertNotNull(engine.getSecurityLevelResolver(),
                "engine constructed without args must have a default resolver");
    }

    @Test
    void nullArgumentsProduceNonNull() {
        DefaultSecurityLevelResolver resolver = new DefaultSecurityLevelResolver();
        LevelHints hints = resolver.produce(null, null, null, null);
        assertNotNull(hints, "produce must return non-null even with null args");
        // trustedSource is true because DefaultContentTrustEvaluator
        // returns true for AGENT_GENERATED even when ctx is null
        assertTrue(hints.isTrustedSource(),
                "null ctx must still produce trustedSource=true by default");
    }
}
