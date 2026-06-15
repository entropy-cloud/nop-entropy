package io.nop.ai.agent.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.nop.ai.agent.engine.NopAiAgentException;

public class TestAgentMessageEnvelope {

    @Test
    void constructorStoresAllFields() {
        long ts = 1_700_000_000_000L;
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "sender-A", "agent.B.inbox", "corr-1", AgentMessageKind.REQUEST, "hello", ts);

        assertEquals("sender-A", env.getSenderId());
        assertEquals("agent.B.inbox", env.getTargetTopic());
        assertEquals("corr-1", env.getCorrelationId());
        assertEquals(AgentMessageKind.REQUEST, env.getKind());
        assertEquals("hello", env.getPayload());
        assertEquals(ts, env.getTimestamp());
    }

    @Test
    void defaultTimestampIsCurrentTime() {
        long before = System.currentTimeMillis();
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "s", "t", "c", AgentMessageKind.ASYNC, null);
        long after = System.currentTimeMillis();
        assertTrue(env.getTimestamp() >= before && env.getTimestamp() <= after,
                "default timestamp should fall within [before, after] window");
    }

    @Test
    void nullPayloadAllowed() {
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "s", "t", "c", AgentMessageKind.RESPONSE, null);
        assertNotNull(env);
        assertEquals(null, env.getPayload());
    }

    @Test
    void nullKindRejected() {
        assertThrows(NopAiAgentException.class, () ->
                new AgentMessageEnvelope("s", "t", "c", null, "p"));
    }

    @Test
    void equalityBasedOnAllFields() {
        long ts = 1234L;
        AgentMessageEnvelope a = new AgentMessageEnvelope("s", "t", "c", AgentMessageKind.REQUEST, "p", ts);
        AgentMessageEnvelope b = new AgentMessageEnvelope("s", "t", "c", AgentMessageKind.REQUEST, "p", ts);
        AgentMessageEnvelope diffPayload = new AgentMessageEnvelope("s", "t", "c", AgentMessageKind.REQUEST, "q", ts);
        AgentMessageEnvelope diffTs = new AgentMessageEnvelope("s", "t", "c", AgentMessageKind.REQUEST, "p", ts + 1);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diffPayload);
        assertNotEquals(a, diffTs);
        assertNotEquals(a, null);
        assertNotEquals(a, "not-an-envelope");
    }

    @Test
    void toStringContainsKeyFields() {
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "sender-A", "agent.B.inbox", "corr-1", AgentMessageKind.REQUEST, "hello", 99L);
        String s = env.toString();
        assertTrue(s.contains("sender-A"));
        assertTrue(s.contains("agent.B.inbox"));
        assertTrue(s.contains("corr-1"));
        assertTrue(s.contains("REQUEST"));
        assertTrue(s.contains("hello"));
    }
}
