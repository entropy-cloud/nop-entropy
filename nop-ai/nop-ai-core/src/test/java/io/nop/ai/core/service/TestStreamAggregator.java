package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestStreamAggregator extends JunitBaseTestCase {

    @Test
    void testAggregator_usage() {
        ChatServiceImpl.StreamAggregator aggregator = new ChatServiceImpl.StreamAggregator();

        ChatStreamChunk chunk1 = new ChatStreamChunk();
        chunk1.setId("test-123");
        chunk1.setModel("test-model");
        chunk1.setContent("Hello");

        ChatStreamChunk chunk2 = new ChatStreamChunk();
        chunk2.setContent(" world");

        ChatStreamChunk chunk3 = new ChatStreamChunk();
        chunk3.setFinishReason("stop");
        chunk3.setUsage(new ChatUsage(10, 5));

        aggregator.addChunk(chunk1);
        aggregator.addChunk(chunk2);
        aggregator.addChunk(chunk3);

        ChatResponse response = aggregator.toResponse();

        assertEquals("test-123", response.getId());
        assertEquals("test-model", response.getModel());
        assertEquals("Hello world", response.getMessage().getContent());
        assertEquals("stop", response.getFinishReason());
        assertNotNull(response.getUsage());
        assertEquals(10, response.getUsage().getPromptTokens().intValue());
        assertEquals(5, response.getUsage().getCompletionTokens().intValue());
        assertEquals(15, response.getUsage().getTotalTokens().intValue());
    }

    @Test
    void testAggregator_noUsage() {
        ChatServiceImpl.StreamAggregator aggregator = new ChatServiceImpl.StreamAggregator();

        ChatStreamChunk chunk1 = new ChatStreamChunk();
        chunk1.setContent("Hello");

        ChatStreamChunk chunk2 = new ChatStreamChunk();
        chunk2.setFinishReason("stop");

        aggregator.addChunk(chunk1);
        aggregator.addChunk(chunk2);

        ChatResponse response = aggregator.toResponse();

        assertEquals("Hello", response.getMessage().getContent());
        assertEquals("stop", response.getFinishReason());
    }
}
