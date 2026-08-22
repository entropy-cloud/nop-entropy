package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.StreamItemPhase;
import io.nop.ai.api.chat.stream.StreamItemType;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGeminiDialect extends JunitBaseTestCase {

    private LlmModel newConfig() {
        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.gemini);
        return config;
    }

    @Test
    public void testBuildBodyStructure() {
        GeminiDialect dialect = new GeminiDialect();
        ChatRequest request = new ChatRequest();
        request.addMessage(new ChatUserMessage("Hello"));
        ChatOptions options = new ChatOptions();
        options.setTemperature(0.7f);
        request.setOptions(options);

        Map<String, Object> body = dialect.buildBody(request, newConfig(), null, "gemini-1.5-pro", false);

        assertNotNull(body.get("contents"));
        assertNotNull(body.get("generationConfig"));
    }

    @Test
    public void testParseResponseWithThinking() {
        GeminiDialect dialect = new GeminiDialect();
        String responseJson = "{\"candidates\":[{\"content\":{\"parts\":[" +
                "{\"text\":\"Let me think\",\"thought\":true}," +
                "{\"text\":\"Final answer\"}]},\"finishReason\":\"STOP\"}]," +
                "\"model\":\"gemini-1.5-pro\"}";

        ChatResponse response = dialect.parseResponse(responseJson, newConfig());

        assertEquals("Final answer", response.outputText());
        assertEquals("gemini-1.5-pro", response.getModel());

        // Plan 329：推理由独立 ChatReasoningMessage 承载
        assertNotNull(response.getMessages().stream()
                .filter(m -> m instanceof ChatReasoningMessage)
                .findFirst().orElse(null));

        // Plan 329：thought:true parts 产出 ChatReasoningMessage，其余 text parts 产出 assistant 文本。
        assertNotNull(response.getMessages());
        assertEquals(2, response.getMessages().size());
        assertTrue(response.getMessages().get(0) instanceof ChatReasoningMessage,
                "thought:true parts must produce ChatReasoningMessage first");
        assertEquals("Let me think", response.getMessages().get(0).getContent());
        assertTrue(response.getMessages().get(1) instanceof ChatAssistantMessage);
        assertEquals("Final answer", response.getMessages().get(1).getContent());
    }

    @Test
    public void testParseResponseNoCandidates() {
        GeminiDialect dialect = new GeminiDialect();
        String responseJson = "{\"model\":\"gemini-1.5-pro\"}";

        ChatResponse response = dialect.parseResponse(responseJson, newConfig());

        assertNull(response.outputText());
    }

    @Test
    public void testParseResponseEmptyReturnsError() {
        GeminiDialect dialect = new GeminiDialect();
        ChatResponse response = dialect.parseResponse("", newConfig());
        assertFalse(response.isSuccess());
        assertEquals("NULL_RESPONSE", response.getErrorCode());
    }

    @Test
    public void testParseResponseMalformedJsonFails() {
        GeminiDialect dialect = new GeminiDialect();
        assertThrows(NopException.class, () -> dialect.parseResponse("{bad json", newConfig()));
    }

    @Test
    public void testParseStreamChunk() {
        GeminiDialect dialect = new GeminiDialect();
        String data = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi\"}]}}]}";

        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.text, chunk.getItemType());
        assertEquals(StreamItemPhase.DELTA, chunk.getPhase());
        assertEquals("Hi", chunk.getDelta());
    }

    @Test
    public void testParseStreamChunkThought() {
        GeminiDialect dialect = new GeminiDialect();
        String data = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hmm\",\"thought\":true}]}}]}";

        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.reasoning, chunk.getItemType());
        assertEquals("hmm", chunk.getDelta());
    }

    @Test
    public void testParseStreamChunkMalformedJsonFails() {
        GeminiDialect dialect = new GeminiDialect();
        assertThrows(NopException.class, () -> dialect.parseStreamChunk("{bad json"));
    }

    @Test
    public void testParseStreamChunkFunctionCallPreservesArguments() {
        // P0 回归：Gemini 流式 functionCall 在单事件内完整下发 name + args，
        // 解析后 args 必须经 arguments 通道完整保留（修复前恒为空）。
        GeminiDialect dialect = new GeminiDialect();
        String data = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[" +
                "{\"functionCall\":{\"name\":\"get_weather\",\"args\":{\"location\":\"beijing\",\"unit\":\"celsius\"}}}]}}]}";

        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.tool_call, chunk.getItemType());
        assertEquals(StreamItemPhase.ADDED, chunk.getPhase());
        assertEquals("get_weather", chunk.getDelta(), "ADDED delta carries the function name");

        assertNotNull(chunk.getArguments(), "complete args must be carried on the arguments channel");
        Map<String, Object> args = (Map<String, Object>) io.nop.api.core.json.JSON.parse(chunk.getArguments());
        assertEquals("beijing", args.get("location"));
        assertEquals("celsius", args.get("unit"));
    }

    @Test
    public void testParseStreamChunkFunctionCallWithoutArgs() {
        // 无 args 的 functionCall：arguments 通道保持 null（可选通道，不注入空串）
        GeminiDialect dialect = new GeminiDialect();
        String data = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[" +
                "{\"functionCall\":{\"name\":\"list_tools\"}}]}}]}";

        ChatStreamChunk chunk = dialect.parseStreamChunk(data);

        assertNotNull(chunk);
        assertEquals(StreamItemType.tool_call, chunk.getItemType());
        assertEquals("list_tools", chunk.getDelta());
        assertNull(chunk.getArguments());
    }

    @Test
    public void testBuildStreamChunkToolCallRoundTripsArguments() {
        // parseStreamChunk → buildStreamChunk 闭环不丢 args（网关反向转换同路径）
        GeminiDialect dialect = new GeminiDialect();
        String data = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[" +
                "{\"functionCall\":{\"name\":\"get_weather\",\"args\":{\"location\":\"beijing\"}}}]}}]}";

        Map<String, Object> rebuilt = dialect.buildStreamChunk(dialect.parseStreamChunk(data));

        List<?> parts = (List<?>) ((Map<?, ?>) ((Map<?, ?>) ((List<?>) rebuilt.get("candidates")).get(0)).get("content")).get("parts");
        Map<?, ?> functionCall = (Map<?, ?>) ((Map<?, ?>) parts.get(0)).get("functionCall");
        assertEquals("get_weather", functionCall.get("name"));
        Map<?, ?> args = (Map<?, ?>) functionCall.get("args");
        assertNotNull(args, "roundtrip must preserve functionCall args");
        assertEquals("beijing", args.get("location"));
    }

    @Test
    public void testParseResponseFunctionCall() {
        // P0 回归（条目建议）：Gemini 非流式 parseResponse 解析 functionCall part，
        // 产出携带 name/args 的 ChatToolCallMessage（修复前完全丢弃）。
        GeminiDialect dialect = new GeminiDialect();
        String responseJson = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[" +
                "{\"functionCall\":{\"name\":\"get_weather\",\"args\":{\"location\":\"beijing\"}}}]}," +
                "\"finishReason\":\"STOP\"}],\"model\":\"gemini-1.5-pro\"}";

        ChatResponse response = dialect.parseResponse(responseJson, newConfig());

        List<io.nop.ai.api.chat.messages.ChatToolCall> toolCalls = response.outputToolCalls();
        assertNotNull(toolCalls);
        assertEquals(1, toolCalls.size());
        assertEquals("get_weather", toolCalls.get(0).getName());
        assertEquals("beijing", toolCalls.get(0).getArguments().get("location"));

        ChatToolCallMessage msgToolCall = response.getMessages().stream()
                .filter(m -> m instanceof ChatToolCallMessage)
                .map(m -> (ChatToolCallMessage) m)
                .findFirst().orElse(null);
        assertNotNull(msgToolCall, "messages must contain a ChatToolCallMessage for the functionCall part");
        assertEquals("get_weather", msgToolCall.getName());
        assertEquals("beijing", msgToolCall.getArguments().get("location"));
    }
}
