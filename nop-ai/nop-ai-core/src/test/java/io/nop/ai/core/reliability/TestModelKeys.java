package io.nop.ai.core.reliability;

import io.nop.ai.api.chat.ChatOptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Semantic test for {@link ModelKeys#buildModelKey} (W2 reliability
 * sink-down, plan 2026-08-15-0604-2): the asserts moved verbatim from
 * {@code TestEngineExtractedCoordinators.buildModelKeyNormalizesNulls} so the
 * migrated public type keeps its own behavior coverage in nop-ai-core.
 */
class TestModelKeys {

    @Test
    void buildModelKeyNormalizesNulls() {
        ChatOptions o = new ChatOptions();
        assertEquals(":", ModelKeys.buildModelKey(o));
        o.setProvider("openai");
        o.setModel("gpt-4o");
        assertEquals("openai:gpt-4o", ModelKeys.buildModelKey(o));
    }

    @Test
    void buildModelKeyHandlesNullProviderOrModelIndependently() {
        ChatOptions o = new ChatOptions();
        o.setProvider("openai");
        assertEquals("openai:", ModelKeys.buildModelKey(o));
        o.setProvider(null);
        o.setModel("gpt-4o");
        assertEquals(":gpt-4o", ModelKeys.buildModelKey(o));
    }
}
