package io.nop.ai.agent.message;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.api.core.annotations.data.DataBean;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 tests for {@link AgentMessageEnvelopeJson} — serialization
 * round-trip with non-trivial payloads.
 */
public class TestAgentMessageEnvelopeJson {

    @Test
    void roundTripStringPayload() {
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "sender-A", "agent.B.inbox", "corr-1", AgentMessageKind.REQUEST, "hello-payload", 99L);

        String json = AgentMessageEnvelopeJson.toJson(env);
        AgentMessageEnvelope restored = AgentMessageEnvelopeJson.fromJson(json);

        assertEquals(env, restored);
        assertTrue(restored.getPayload() instanceof String);
        assertEquals("hello-payload", restored.getPayload());
    }

    @Test
    void roundTripNullPayload() {
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "sender-A", "agent.B.inbox", null, AgentMessageKind.ASYNC, null, 100L);

        String json = AgentMessageEnvelopeJson.toJson(env);
        AgentMessageEnvelope restored = AgentMessageEnvelopeJson.fromJson(json);

        assertEquals(env, restored);
        assertNull(restored.getPayload());
        assertNull(restored.getCorrelationId());
    }

    @Test
    void roundTripIntegerPayload() {
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "t", "c", AgentMessageKind.RESPONSE, 42, 1L);

        String json = AgentMessageEnvelopeJson.toJson(env);
        AgentMessageEnvelope restored = AgentMessageEnvelopeJson.fromJson(json);

        assertEquals(env, restored);
        assertTrue(restored.getPayload() instanceof Integer);
        assertEquals(42, restored.getPayload());
    }

    @Test
    void roundTripLongPayload() {
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "t", "c", AgentMessageKind.RESPONSE, 123456789012L, 1L);

        String json = AgentMessageEnvelopeJson.toJson(env);
        AgentMessageEnvelope restored = AgentMessageEnvelopeJson.fromJson(json);

        assertEquals(env, restored);
        assertTrue(restored.getPayload() instanceof Long);
        assertEquals(123456789012L, restored.getPayload());
    }

    @Test
    void roundTripBooleanPayload() {
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "t", "c", AgentMessageKind.ASYNC, Boolean.TRUE, 1L);

        String json = AgentMessageEnvelopeJson.toJson(env);
        AgentMessageEnvelope restored = AgentMessageEnvelopeJson.fromJson(json);

        assertEquals(env, restored);
        assertTrue(restored.getPayload() instanceof Boolean);
        assertEquals(Boolean.TRUE, restored.getPayload());
    }

    @Test
    void roundTripMapPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key1", "value1");
        payload.put("key2", 42);
        payload.put("key3", true);

        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "corr-map", AgentMessageKind.REQUEST, payload, 5L);

        String json = AgentMessageEnvelopeJson.toJson(env);
        AgentMessageEnvelope restored = AgentMessageEnvelopeJson.fromJson(json);

        assertEquals(env.getSenderId(), restored.getSenderId());
        assertEquals(env.getTargetTopic(), restored.getTargetTopic());
        assertEquals(env.getCorrelationId(), restored.getCorrelationId());
        assertEquals(env.getKind(), restored.getKind());
        assertEquals(env.getTimestamp(), restored.getTimestamp());
        assertNotNull(restored.getPayload());
        assertTrue(restored.getPayload() instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> restoredPayload = (Map<String, Object>) restored.getPayload();
        assertEquals("value1", restoredPayload.get("key1"));
        assertEquals(42, restoredPayload.get("key2"));
        assertEquals(true, restoredPayload.get("key3"));
    }

    @Test
    void roundTripListPayload() {
        List<String> payload = List.of("a", "b", "c");

        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "corr-list", AgentMessageKind.ASYNC, payload, 7L);

        String json = AgentMessageEnvelopeJson.toJson(env);
        AgentMessageEnvelope restored = AgentMessageEnvelopeJson.fromJson(json);

        assertNotNull(restored.getPayload());
        assertTrue(restored.getPayload() instanceof List);
        assertEquals(payload, restored.getPayload());
    }

    @Test
    void roundTripCustomBeanPayload() {
        SamplePayload payload = new SamplePayload();
        payload.setName("test-name");
        payload.setCount(99);
        payload.setActive(true);

        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "A", "agent.B.inbox", "corr-bean", AgentMessageKind.REQUEST, payload, 8L);

        String json = AgentMessageEnvelopeJson.toJson(env);
        AgentMessageEnvelope restored = AgentMessageEnvelopeJson.fromJson(json);

        assertNotNull(restored.getPayload());
        assertTrue(restored.getPayload() instanceof SamplePayload);
        SamplePayload restoredPayload = (SamplePayload) restored.getPayload();
        assertEquals("test-name", restoredPayload.getName());
        assertEquals(99, restoredPayload.getCount());
        assertTrue(restoredPayload.isActive());
    }

    @Test
    void toJsonRejectsNullEnvelope() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> AgentMessageEnvelopeJson.toJson(null));
        assertTrue(ex.getMessage().contains("envelope must not be null"));
    }

    @Test
    void fromJsonRejectsNullOrBlankJson() {
        assertThrows(NopAiAgentException.class, () -> AgentMessageEnvelopeJson.fromJson(null));
        assertThrows(NopAiAgentException.class, () -> AgentMessageEnvelopeJson.fromJson(""));
        assertThrows(NopAiAgentException.class, () -> AgentMessageEnvelopeJson.fromJson("   "));
    }

    @Test
    void fromJsonRejectsInvalidKind() {
        String json = "{\"senderId\":\"A\",\"targetTopic\":\"t\",\"kind\":\"BOGUS\",\"timestamp\":1,\"payloadClassName\":null,\"payload\":null}";
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> AgentMessageEnvelopeJson.fromJson(json));
        assertTrue(ex.getMessage().contains("unknown AgentMessageKind"));
    }

    @DataBean
    public static class SamplePayload {
        private String name;
        private int count;
        private boolean active;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
