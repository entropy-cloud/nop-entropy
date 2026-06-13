package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultContentTrustEvaluator {

    private final DefaultContentTrustEvaluator evaluator = new DefaultContentTrustEvaluator();

    private AgentExecutionContext newContext() {
        return AgentExecutionContext.create(new AgentModel(), "test-session");
    }

    @Test
    void testChannelInputTrusted() {
        assertTrue(evaluator.isTrustedSource(ContentOrigin.CHANNEL_INPUT, newContext()));
    }

    @Test
    void testChannelInputTrustedWithNullContext() {
        assertTrue(evaluator.isTrustedSource(ContentOrigin.CHANNEL_INPUT, null));
    }

    @Test
    void testAgentGeneratedTrusted() {
        assertTrue(evaluator.isTrustedSource(ContentOrigin.AGENT_GENERATED, newContext()));
    }

    @Test
    void testAgentGeneratedTrustedWithNullContext() {
        assertTrue(evaluator.isTrustedSource(ContentOrigin.AGENT_GENERATED, null));
    }

    @Test
    void testWebFetchUntrusted() {
        assertFalse(evaluator.isTrustedSource(ContentOrigin.WEB_FETCH, newContext()));
    }

    @Test
    void testWebFetchUntrustedWithNullContext() {
        assertFalse(evaluator.isTrustedSource(ContentOrigin.WEB_FETCH, null));
    }

    @Test
    void testFileReadUntrusted() {
        assertFalse(evaluator.isTrustedSource(ContentOrigin.FILE_READ, newContext()));
    }

    @Test
    void testFileReadUntrustedWithNullContext() {
        assertFalse(evaluator.isTrustedSource(ContentOrigin.FILE_READ, null));
    }

    @Test
    void testNullOriginReturnsFalse() {
        assertFalse(evaluator.isTrustedSource(null, newContext()));
    }

    @Test
    void testNullOriginNullContextReturnsFalse() {
        assertFalse(evaluator.isTrustedSource(null, null));
    }

    @Test
    void testAllContentOriginValuesExercised() {
        AgentExecutionContext ctx = newContext();
        for (ContentOrigin origin : ContentOrigin.values()) {
            boolean result = evaluator.isTrustedSource(origin, ctx);
            if (origin == ContentOrigin.CHANNEL_INPUT || origin == ContentOrigin.AGENT_GENERATED) {
                assertTrue(result, origin.name() + " should be trusted");
            } else {
                assertFalse(result, origin.name() + " should be untrusted");
            }
        }
    }
}
