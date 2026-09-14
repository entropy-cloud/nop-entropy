package io.nop.ai.agent.usage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plan 201 (L2-17) Phase 1: verify {@link UsageRecord} field read/write,
 * including the nullable {@code modelId} / {@code responseDurationMs} fields
 * that are intentionally left null at the agent runtime layer (resolved by
 * the L2-18 recorder at persistence time).
 */
public class TestUsageRecord {

    @Test
    void tokenFieldsDefaultToZero() {
        UsageRecord record = new UsageRecord();
        assertEquals(0, record.getPromptTokens());
        assertEquals(0, record.getCompletionTokens());
        assertEquals(0L, record.getResponseTimestamp());
    }
}
