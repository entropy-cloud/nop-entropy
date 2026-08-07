package io.nop.ai.api.chat.stream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nop.ai.api.chat.messages.ChatUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 328 Phase 1 golden test：{@link ChatStreamChunk} item 增量模型的 Jackson 序列化 round-trip。
 * <p>
 * 覆盖 ADDED/DELTA/DONE 三段式，以及多 tool_call（不同 callId/itemIndex）chunk 独立。
 */
public class TestChatStreamChunkSerialization {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void textDeltaRoundTrip() throws Exception {
        ChatStreamChunk chunk = new ChatStreamChunk();
        chunk.setId("chatcmpl-1");
        chunk.setItemType(StreamItemType.text);
        chunk.setItemIndex(0);
        chunk.setPhase(StreamItemPhase.DELTA);
        chunk.setDelta("Hello");

        String json = mapper.writeValueAsString(chunk);
        assertTrue(json.contains("\"itemType\":\"text\""), json);
        assertTrue(json.contains("\"phase\":\"DELTA\""), json);
        assertTrue(json.contains("\"delta\":\"Hello\""), json);

        ChatStreamChunk back = mapper.readValue(json, ChatStreamChunk.class);
        assertEquals("chatcmpl-1", back.getId());
        assertEquals(StreamItemType.text, back.getItemType());
        assertEquals(0, back.getItemIndex());
        assertEquals(StreamItemPhase.DELTA, back.getPhase());
        assertEquals("Hello", back.getDelta());
        assertTrue(back.isTextItem());
        assertTrue(back.isDelta());
        assertNull(back.getCallId());
    }

    @Test
    void reasoningDeltaRoundTrip() throws Exception {
        ChatStreamChunk chunk = new ChatStreamChunk();
        chunk.setItemType(StreamItemType.reasoning);
        chunk.setItemIndex(0);
        chunk.setPhase(StreamItemPhase.DELTA);
        chunk.setDelta("Let me think");

        String json = mapper.writeValueAsString(chunk);
        assertTrue(json.contains("\"itemType\":\"reasoning\""), json);

        ChatStreamChunk back = mapper.readValue(json, ChatStreamChunk.class);
        assertEquals(StreamItemType.reasoning, back.getItemType());
        assertEquals("Let me think", back.getDelta());
        assertTrue(back.isReasoningItem());
    }

    @Test
    void toolCallAddedPhaseRoundTrip() throws Exception {
        ChatStreamChunk chunk = new ChatStreamChunk();
        chunk.setItemType(StreamItemType.tool_call);
        chunk.setItemIndex(0);
        chunk.setCallId("call_abc");
        chunk.setPhase(StreamItemPhase.ADDED);
        chunk.setDelta("get_weather");

        String json = mapper.writeValueAsString(chunk);
        assertTrue(json.contains("\"itemType\":\"tool_call\""), json);
        assertTrue(json.contains("\"callId\":\"call_abc\""), json);
        assertTrue(json.contains("\"phase\":\"ADDED\""), json);

        ChatStreamChunk back = mapper.readValue(json, ChatStreamChunk.class);
        assertEquals(StreamItemType.tool_call, back.getItemType());
        assertEquals(0, back.getItemIndex());
        assertEquals("call_abc", back.getCallId());
        assertEquals(StreamItemPhase.ADDED, back.getPhase());
        assertEquals("get_weather", back.getDelta());
        assertTrue(back.isToolCallItem());
        assertTrue(back.isAdded());
    }

    @Test
    void toolCallDeltaPhaseRoundTrip() throws Exception {
        ChatStreamChunk chunk = new ChatStreamChunk();
        chunk.setItemType(StreamItemType.tool_call);
        chunk.setItemIndex(0);
        chunk.setPhase(StreamItemPhase.DELTA);
        chunk.setDelta("{\"loc");

        String json = mapper.writeValueAsString(chunk);
        ChatStreamChunk back = mapper.readValue(json, ChatStreamChunk.class);
        assertEquals(StreamItemType.tool_call, back.getItemType());
        assertEquals(StreamItemPhase.DELTA, back.getPhase());
        assertEquals("{\"loc", back.getDelta());
        assertNull(back.getCallId());
    }

    @Test
    void doneChunkWithFinishReasonAndUsageRoundTrip() throws Exception {
        ChatStreamChunk chunk = new ChatStreamChunk();
        chunk.setItemType(StreamItemType.text);
        chunk.setItemIndex(0);
        chunk.setPhase(StreamItemPhase.DONE);
        chunk.setFinishReason("stop");
        chunk.setUsage(new ChatUsage(10, 5));

        String json = mapper.writeValueAsString(chunk);
        assertTrue(json.contains("\"phase\":\"DONE\""), json);
        assertTrue(json.contains("\"finishReason\":\"stop\""), json);

        ChatStreamChunk back = mapper.readValue(json, ChatStreamChunk.class);
        assertEquals(StreamItemPhase.DONE, back.getPhase());
        assertEquals("stop", back.getFinishReason());
        assertTrue(back.isLastChunk());
        assertTrue(back.isDone());
        assertNotNull(back.getUsage());
        assertEquals(10, back.getUsage().getPromptTokens().intValue());
        assertEquals(5, back.getUsage().getCompletionTokens().intValue());
    }

    @Test
    void multipleToolCallsRemainIndependent() throws Exception {
        // 两个并行 tool_call，靠 itemIndex + callId 区分，各自独立 round-trip
        ChatStreamChunk first = new ChatStreamChunk();
        first.setItemType(StreamItemType.tool_call);
        first.setItemIndex(0);
        first.setCallId("call_1");
        first.setPhase(StreamItemPhase.ADDED);
        first.setDelta("get_weather");

        ChatStreamChunk second = new ChatStreamChunk();
        second.setItemType(StreamItemType.tool_call);
        second.setItemIndex(1);
        second.setCallId("call_2");
        second.setPhase(StreamItemPhase.ADDED);
        second.setDelta("get_time");

        String json1 = mapper.writeValueAsString(first);
        String json2 = mapper.writeValueAsString(second);

        ChatStreamChunk back1 = mapper.readValue(json1, ChatStreamChunk.class);
        ChatStreamChunk back2 = mapper.readValue(json2, ChatStreamChunk.class);

        assertEquals(0, back1.getItemIndex());
        assertEquals("call_1", back1.getCallId());
        assertEquals("get_weather", back1.getDelta());

        assertEquals(1, back2.getItemIndex());
        assertEquals("call_2", back2.getCallId());
        assertEquals("get_time", back2.getDelta());
    }
}
