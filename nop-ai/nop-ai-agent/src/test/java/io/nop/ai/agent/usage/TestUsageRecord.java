package io.nop.ai.agent.usage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 201 (L2-17) Phase 1: verify {@link UsageRecord} field read/write,
 * including the nullable {@code modelId} / {@code responseDurationMs} fields
 * that are intentionally left null at the agent runtime layer (resolved by
 * the L2-18 recorder at persistence time).
 */
public class TestUsageRecord {

    @Test
    void allFieldsRoundTrip() {
        UsageRecord record = new UsageRecord();
        record.setSessionId("sess-1");
        record.setAgentName("calc-agent");
        record.setRequestId("req-42");
        record.setAiProvider("test-provider");
        record.setAiModel("test-model");
        record.setPromptTokens(120);
        record.setCompletionTokens(35);
        record.setResponseDurationMs(750L);
        record.setResponseTimestamp(1_700_000_000_000L);

        assertEquals("sess-1", record.getSessionId());
        assertEquals("calc-agent", record.getAgentName());
        assertEquals("req-42", record.getRequestId());
        assertEquals("test-provider", record.getAiProvider());
        assertEquals("test-model", record.getAiModel());
        assertEquals(120, record.getPromptTokens());
        assertEquals(35, record.getCompletionTokens());
        assertEquals(750L, record.getResponseDurationMs());
        assertEquals(1_700_000_000_000L, record.getResponseTimestamp());
    }

    @Test
    void nullableFieldsDefaultToNull() {
        UsageRecord record = new UsageRecord();
        // modelId is null at agent runtime (NopAiModel PK resolved by L2-18)
        assertNull(record.getModelId(), "modelId must default to null");
        // responseDurationMs is null at agent runtime (LLM timing is L2-18)
        assertNull(record.getResponseDurationMs(),
                "responseDurationMs must default to null");
        record.setModelId("mdl-9");
        assertNotNull(record.getModelId());
        assertEquals("mdl-9", record.getModelId());
    }

    @Test
    void tokenFieldsDefaultToZero() {
        UsageRecord record = new UsageRecord();
        assertEquals(0, record.getPromptTokens());
        assertEquals(0, record.getCompletionTokens());
        assertEquals(0L, record.getResponseTimestamp());
    }
}
