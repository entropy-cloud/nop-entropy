package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 325 golden test：{@link ChatMessage} 六型消息的 Jackson 序列化 round-trip
 * （对象 → JSON → 对象 等价）。
 * <p>
 * 覆盖 user/assistant/system/tool_call/tool_output/reasoning 六种 type 标识，并验证
 * {@link ChatToolCallMessage} 的 callId 关联字段、{@link ChatReasoningMessage} 的 summary/detail、
 * 以及 {@link ChatToolResponseMessage} 的 {@code "tool_output"} discriminator（plan 325 从 {@code "tool"} 改名）。
 */
public class TestChatMessageSerialization {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void userMessageRoundTrip() throws Exception {
        ChatUserMessage msg = new ChatUserMessage("hello");
        msg.setMessageId("u1");

        String json = mapper.writeValueAsString(msg);
        assertTrue(json.contains("\"role\":\"user\""), json);

        ChatUserMessage back = (ChatUserMessage) mapper.readValue(json, ChatMessage.class);
        assertEquals("user", back.getRole());
        assertEquals("hello", back.getContent());
        assertEquals("u1", back.getMessageId());
    }

    @Test
    void assistantMessageRoundTrip() throws Exception {
        ChatAssistantMessage msg = new ChatAssistantMessage("hi");
        msg.setMessageId("a1");
        msg.setThink("thinking");

        String json = mapper.writeValueAsString(msg);
        assertTrue(json.contains("\"role\":\"assistant\""), json);

        ChatAssistantMessage back = (ChatAssistantMessage) mapper.readValue(json, ChatMessage.class);
        assertEquals("assistant", back.getRole());
        assertEquals("hi", back.getContent());
        assertEquals("thinking", back.getThink());
        assertEquals("a1", back.getMessageId());
    }

    @Test
    void systemMessageRoundTrip() throws Exception {
        ChatSystemMessage msg = new ChatSystemMessage("be helpful");
        msg.setName("sys");

        String json = mapper.writeValueAsString(msg);
        assertTrue(json.contains("\"role\":\"system\""), json);

        ChatSystemMessage back = (ChatSystemMessage) mapper.readValue(json, ChatMessage.class);
        assertEquals("system", back.getRole());
        assertEquals("be helpful", back.getContent());
        assertEquals("sys", back.getName());
    }

    @Test
    void toolCallMessageRoundTrip() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("city", "Beijing");
        args.put("days", 3);

        ChatToolCallMessage msg = new ChatToolCallMessage("call_1", "get_weather", args);
        msg.setMessageId("tc1");

        String json = mapper.writeValueAsString(msg);
        assertTrue(json.contains("\"role\":\"tool_call\""), json);
        assertTrue(json.contains("\"callId\":\"call_1\""), json);
        assertTrue(json.contains("\"name\":\"get_weather\""), json);

        ChatToolCallMessage back = (ChatToolCallMessage) mapper.readValue(json, ChatMessage.class);
        assertEquals("tool_call", back.getRole());
        assertEquals("call_1", back.getCallId());
        assertEquals("get_weather", back.getName());
        assertEquals(args, back.getArguments());
        assertEquals("tc1", back.getMessageId());
    }

    @Test
    void toolCallMessageFromChatToolCall() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("x", 1);
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_x");
        toolCall.setName("fn");
        toolCall.setArguments(args);

        ChatToolCallMessage msg = ChatToolCallMessage.fromChatToolCall(toolCall);
        assertEquals("call_x", msg.getCallId());
        assertEquals("fn", msg.getName());
        assertEquals(args, msg.getArguments());
    }

    @Test
    void toolOutputMessageRoundTrip() throws Exception {
        ChatToolResponseMessage msg = new ChatToolResponseMessage("call_1", "get_weather", "sunny");
        msg.setResultType("text");

        String json = mapper.writeValueAsString(msg);
        assertTrue(json.contains("\"role\":\"tool_output\""), json);
        assertTrue(json.contains("\"callId\":\"call_1\""), json);
        assertTrue(json.contains("\"name\":\"get_weather\""), json);
        assertTrue(json.contains("\"content\":\"sunny\""), json);

        ChatToolResponseMessage back = (ChatToolResponseMessage) mapper.readValue(json, ChatMessage.class);
        assertEquals("tool_output", back.getRole());
        assertEquals("call_1", back.getCallId());
        assertEquals("get_weather", back.getName());
        assertEquals("sunny", back.getContent());
        assertEquals("text", back.getResultType());
    }

    @Test
    void toolOutputFromToolCallBindsCallId() {
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_z");
        toolCall.setName("fn");

        ChatToolResponseMessage msg = ChatToolResponseMessage.fromToolCall(toolCall, "ok");
        assertEquals("call_z", msg.getCallId());
        assertEquals("call_z", msg.getToolCallId());
        assertEquals("ok", msg.getContent());
    }

    @Test
    void reasoningMessageRoundTrip() throws Exception {
        ChatReasoningMessage msg = new ChatReasoningMessage("summary text", "detail text");
        msg.setMessageId("r1");

        String json = mapper.writeValueAsString(msg);
        assertTrue(json.contains("\"role\":\"reasoning\""), json);
        assertTrue(json.contains("\"summary\":\"summary text\""), json);
        assertTrue(json.contains("\"detail\":\"detail text\""), json);

        ChatReasoningMessage back = (ChatReasoningMessage) mapper.readValue(json, ChatMessage.class);
        assertEquals("reasoning", back.getRole());
        assertEquals("summary text", back.getSummary());
        assertEquals("detail text", back.getDetail());
        assertEquals("summary text", back.getContent());
        assertEquals("r1", back.getMessageId());
    }

    @Test
    void reasoningMessageMinimalRoundTrip() throws Exception {
        ChatReasoningMessage msg = new ChatReasoningMessage("only summary");

        String json = mapper.writeValueAsString(msg);
        ChatReasoningMessage back = (ChatReasoningMessage) mapper.readValue(json, ChatMessage.class);
        assertEquals("reasoning", back.getRole());
        assertEquals("only summary", back.getSummary());
        assertNull(back.getDetail());
    }

    @Test
    void toolCallMessageEmptyArgumentsRoundTrip() throws Exception {
        ChatToolCallMessage msg = new ChatToolCallMessage("call_2", "noop", null);

        String json = mapper.writeValueAsString(msg);
        ChatToolCallMessage back = (ChatToolCallMessage) mapper.readValue(json, ChatMessage.class);
        assertEquals("tool_call", back.getRole());
        assertEquals("call_2", back.getCallId());
        assertEquals("noop", back.getName());
        assertNull(back.getArguments());
    }

    @Test
    void polymorphicListRoundTrip() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("q", "test");

        List<ChatMessage> msgs = List.of(
                new ChatSystemMessage("sys"),
                new ChatUserMessage("hi"),
                new ChatAssistantMessage("hello"),
                new ChatToolCallMessage("call_1", "search", args),
                new ChatToolResponseMessage("call_1", "search", "result"),
                new ChatReasoningMessage("thinking"));

        String json = mapper.writeValueAsString(msgs);

        List<ChatMessage> back = mapper.readValue(json,
                mapper.getTypeFactory().constructCollectionType(List.class, ChatMessage.class));

        assertEquals(6, back.size());
        assertTrue(back.get(0) instanceof ChatSystemMessage);
        assertTrue(back.get(1) instanceof ChatUserMessage);
        assertTrue(back.get(2) instanceof ChatAssistantMessage);
        assertTrue(back.get(3) instanceof ChatToolCallMessage);
        assertTrue(back.get(4) instanceof ChatToolResponseMessage);
        assertTrue(back.get(5) instanceof ChatReasoningMessage);

        assertEquals("tool_call", back.get(3).getRole());
        assertEquals("tool_output", back.get(4).getRole());
        assertEquals("reasoning", back.get(5).getRole());

        ChatToolCallMessage tcm = (ChatToolCallMessage) back.get(3);
        assertEquals("call_1", tcm.getCallId());
        assertEquals(args, tcm.getArguments());
    }

    @Test
    void customTypeNoLongerRegistered() {
        String json = "{\"role\":\"custom\",\"content\":\"x\"}";
        assertThrows(Exception.class, () -> mapper.readValue(json, ChatMessage.class),
                "ChatCustomMessage 已从 @JsonSubTypes 移除，\"custom\" discriminator 应不可反序列化");
    }
}
