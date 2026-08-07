package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.StreamItemPhase;
import io.nop.ai.api.chat.stream.StreamItemType;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStreamAggregator extends JunitBaseTestCase {

    private static ChatStreamChunk textDelta(String id, String model, String delta) {
        ChatStreamChunk chunk = new ChatStreamChunk();
        if (id != null) chunk.setId(id);
        if (model != null) chunk.setModel(model);
        chunk.setItemType(StreamItemType.text);
        chunk.setItemIndex(0);
        chunk.setPhase(StreamItemPhase.DELTA);
        chunk.setDelta(delta);
        return chunk;
    }

    private static ChatStreamChunk done(String finishReason, ChatUsage usage) {
        ChatStreamChunk chunk = new ChatStreamChunk();
        chunk.setPhase(StreamItemPhase.DONE);
        chunk.setFinishReason(finishReason);
        if (usage != null) chunk.setUsage(usage);
        return chunk;
    }

    @Test
    void testAggregator_usage() {
        ChatServiceImpl.StreamAggregator aggregator = new ChatServiceImpl.StreamAggregator();

        aggregator.addChunk(textDelta("test-123", "test-model", "Hello"));
        aggregator.addChunk(textDelta(null, null, " world"));
        aggregator.addChunk(done("stop", new ChatUsage(10, 5)));

        ChatResponse response = aggregator.toResponse();

        assertEquals("test-123", response.getId());
        assertEquals("test-model", response.getModel());
        assertEquals("Hello world", response.getMessage().getContent());
        assertEquals("stop", response.getFinishReason());
        assertNotNull(response.getUsage());
        assertEquals(10, response.getUsage().getPromptTokens().intValue());
        assertEquals(5, response.getUsage().getCompletionTokens().intValue());
        assertEquals(15, response.getUsage().getTotalTokens().intValue());

        // Plan 328 双轨：messages 序列同构（text only → 仅 assistant）
        assertNotNull(response.getMessages());
        assertEquals(1, response.getMessages().size());
        assertEquals("Hello world", response.getMessages().get(0).getContent());
    }

    @Test
    void testAggregator_noUsage() {
        ChatServiceImpl.StreamAggregator aggregator = new ChatServiceImpl.StreamAggregator();

        aggregator.addChunk(textDelta(null, null, "Hello"));
        aggregator.addChunk(done("stop", null));

        ChatResponse response = aggregator.toResponse();

        assertEquals("Hello", response.getMessage().getContent());
        assertEquals("stop", response.getFinishReason());
    }

    @Test
    void testAggregator_reasoningThenTextProducesMessages() {
        // reasoning 增量 → ChatReasoningMessage，text 增量 → ChatAssistantMessage
        ChatServiceImpl.StreamAggregator aggregator = new ChatServiceImpl.StreamAggregator();

        ChatStreamChunk reasoning = new ChatStreamChunk();
        reasoning.setItemType(StreamItemType.reasoning);
        reasoning.setItemIndex(0);
        reasoning.setPhase(StreamItemPhase.DELTA);
        reasoning.setDelta("Let me think");

        aggregator.addChunk(reasoning);
        aggregator.addChunk(textDelta(null, null, "Final answer"));
        aggregator.addChunk(done("stop", null));

        ChatResponse response = aggregator.toResponse();

        // 旧 message 双轨
        assertEquals("Final answer", response.getMessage().getContent());
        assertEquals("Let me think", response.getMessage().getThink());

        // messages 序列：reasoning → assistant text
        assertNotNull(response.getMessages());
        assertEquals(2, response.getMessages().size());
        assertTrue(response.getMessages().get(0) instanceof ChatReasoningMessage,
                "reasoning item must produce ChatReasoningMessage first");
        assertEquals("Let me think", response.getMessages().get(0).getContent());
        assertEquals("Final answer", response.getMessages().get(1).getContent());
    }

    @Test
    void testAggregator_toolCallProducesChatToolCallMessage() {
        // Anti-Hollow（Plan 328 Phase 3 exit criteria）：tool_call item 增量
        // （ADDED 声明 name/callId → DELTA 拼接 arguments）收敛为 ChatToolCallMessage，
        // messages 序列含 assistant text + ChatToolCallMessage，callId/arguments 正确。
        ChatServiceImpl.StreamAggregator aggregator = new ChatServiceImpl.StreamAggregator();

        ChatStreamChunk added = new ChatStreamChunk();
        added.setItemType(StreamItemType.tool_call);
        added.setItemIndex(0);
        added.setCallId("call_42");
        added.setPhase(StreamItemPhase.ADDED);
        added.setDelta("get_weather");

        ChatStreamChunk delta1 = new ChatStreamChunk();
        delta1.setItemType(StreamItemType.tool_call);
        delta1.setItemIndex(0);
        delta1.setPhase(StreamItemPhase.DELTA);
        delta1.setDelta("{\"loc");

        ChatStreamChunk delta2 = new ChatStreamChunk();
        delta2.setItemType(StreamItemType.tool_call);
        delta2.setItemIndex(0);
        delta2.setPhase(StreamItemPhase.DELTA);
        delta2.setDelta("ation\":\"beijing\"}");

        aggregator.addChunk(textDelta("s1", null, "I will check the weather."));
        aggregator.addChunk(added);
        aggregator.addChunk(delta1);
        aggregator.addChunk(delta2);
        aggregator.addChunk(done("tool_calls", null));

        ChatResponse response = aggregator.toResponse();

        // 旧 message 双轨：toolCalls 寄居字段
        assertNotNull(response.getMessage().getToolCalls());
        assertEquals("call_42", response.getMessage().getToolCalls().get(0).getId());
        assertEquals("get_weather", response.getMessage().getToolCalls().get(0).getName());
        assertEquals("beijing", response.getMessage().getToolCalls().get(0).getArguments().get("location"),
                "arguments fragments must be assembled into a complete object");

        // messages 序列含 ChatToolCallMessage
        ChatToolCallMessage msgToolCall = response.getMessages().stream()
                .filter(m -> m instanceof ChatToolCallMessage)
                .map(m -> (ChatToolCallMessage) m)
                .findFirst().orElse(null);
        assertNotNull(msgToolCall, "messages must contain a ChatToolCallMessage for the tool_call item");
        assertEquals("call_42", msgToolCall.getCallId());
        assertEquals("get_weather", msgToolCall.getName());
        assertEquals("beijing", msgToolCall.getArguments().get("location"));

        // 顺序：assistant text → tool_call
        assertEquals(2, response.getMessages().size());
        ChatMessage first = response.getMessages().get(0);
        assertTrue(first.getClass().getSimpleName().contains("Assistant"),
                "first message should be the assistant text");
    }

    @Test
    void testAggregator_multipleToolCallsDistinguishedByItemIndex() {
        // 多 tool_call 靠 itemIndex 区分，arguments 各自完整拼接
        ChatServiceImpl.StreamAggregator aggregator = new ChatServiceImpl.StreamAggregator();

        ChatStreamChunk a0 = new ChatStreamChunk();
        a0.setItemType(StreamItemType.tool_call);
        a0.setItemIndex(0);
        a0.setCallId("call_1");
        a0.setPhase(StreamItemPhase.ADDED);
        a0.setDelta("get_weather");

        ChatStreamChunk d0 = new ChatStreamChunk();
        d0.setItemType(StreamItemType.tool_call);
        d0.setItemIndex(0);
        d0.setPhase(StreamItemPhase.DELTA);
        d0.setDelta("{\"city\":\"bj\"}");

        ChatStreamChunk a1 = new ChatStreamChunk();
        a1.setItemType(StreamItemType.tool_call);
        a1.setItemIndex(1);
        a1.setCallId("call_2");
        a1.setPhase(StreamItemPhase.ADDED);
        a1.setDelta("get_time");

        ChatStreamChunk d1 = new ChatStreamChunk();
        d1.setItemType(StreamItemType.tool_call);
        d1.setItemIndex(1);
        d1.setPhase(StreamItemPhase.DELTA);
        d1.setDelta("{\"zone\":\"utc\"}");

        aggregator.addChunk(a0);
        aggregator.addChunk(d0);
        aggregator.addChunk(a1);
        aggregator.addChunk(d1);
        aggregator.addChunk(done("tool_calls", null));

        ChatResponse response = aggregator.toResponse();

        assertEquals(2, response.getMessage().getToolCalls().size());
        assertEquals("call_1", response.getMessage().getToolCalls().get(0).getId());
        assertEquals("bj", response.getMessage().getToolCalls().get(0).getArguments().get("city"));
        assertEquals("call_2", response.getMessage().getToolCalls().get(1).getId());
        assertEquals("utc", response.getMessage().getToolCalls().get(1).getArguments().get("zone"));
    }
}
