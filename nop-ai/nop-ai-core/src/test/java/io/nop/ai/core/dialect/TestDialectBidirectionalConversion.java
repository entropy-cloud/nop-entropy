package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ILlmDialect 双向转换参数化覆盖（plan 2026-08-15-0849-1 W4，Phase 3）。
 * <p>
 * 覆盖：请求方向（parseRequestBody(buildBody(req)) 语义相等 + E2E 原生请求体闭环）、
 * 响应方向（buildResponse(parseResponse(sample)) 原生响应闭环）、流式方向
 * （buildStreamChunk(parseStreamChunk(sample)) 原生流块闭环），每 dialect × 两方向。
 * <p>
 * roundtrip 有损点裁定（plan 附录）：finish_reason 样本取归一化目标值（stop/length）；
 * Anthropic roundtrip 请求显式 setMaxTokens；system 样本限一条；Anthropic usage 断言含 cache 字段；
 * Anthropic/Gemini/Ollama 请求方向样本不含 reasoning 消息（convertMessage 双 block 伪影，落档）；
 * Ollama 样本不含 system（buildBody 丢弃，落档）。
 */
public class TestDialectBidirectionalConversion extends JunitBaseTestCase {

    private LlmModel newConfig(ApiStyle style) {
        LlmModel config = new LlmModel();
        config.setApiStyle(style);
        return config;
    }

    // ==================== 请求方向：parseRequestBody(buildBody(req)) 语义相等 ====================

    @Test
    void testRequestRoundtrip_openai() {
        ChatRequest request = buildOpenAiRequest();
        OpenAiDialect dialect = new OpenAiDialect();

        Map<String, Object> body = dialect.buildBody(request, newConfig(ApiStyle.openai), null, "gpt-4", false);
        ChatRequest parsed = dialect.parseRequestBody(body);

        assertMessagesEqual(request.getMessages(), parsed.getMessages());
        assertOptionsEqual(request.getOptions(), parsed.getOptions());
        assertToolsEqual(request.getTools(), parsed.getTools());
    }

    @Test
    void testRequestRoundtrip_anthropic() {
        ChatRequest request = buildAnthropicRequest();
        AnthropicDialect dialect = new AnthropicDialect();

        Map<String, Object> body = dialect.buildBody(request, newConfig(ApiStyle.anthropic), null, "claude-3-5-sonnet-20241022", false);
        ChatRequest parsed = dialect.parseRequestBody(body);

        assertMessagesEqual(request.getMessages(), parsed.getMessages());
        assertOptionsEqual(request.getOptions(), parsed.getOptions());
        assertToolsEqual(request.getTools(), parsed.getTools());
    }

    @Test
    void testRequestRoundtrip_gemini() {
        ChatRequest request = buildGeminiRequest();
        GeminiDialect dialect = new GeminiDialect();

        Map<String, Object> body = dialect.buildBody(request, newConfig(ApiStyle.gemini), null, "gemini-pro", false);
        ChatRequest parsed = dialect.parseRequestBody(body);

        assertMessagesEqual(request.getMessages(), parsed.getMessages());
        assertOptionsEqual(request.getOptions(), parsed.getOptions());
        assertToolsEqual(request.getTools(), parsed.getTools());
    }

    @Test
    void testRequestRoundtrip_ollama() {
        ChatRequest request = buildOllamaRequest();
        OllamaDialect dialect = new OllamaDialect();

        Map<String, Object> body = dialect.buildBody(request, newConfig(ApiStyle.ollama), null, "llama2", false);
        ChatRequest parsed = dialect.parseRequestBody(body);

        assertMessagesEqual(request.getMessages(), parsed.getMessages());
        assertOptionsEqual(request.getOptions(), parsed.getOptions());
        assertToolsEqual(request.getTools(), parsed.getTools());
    }

    @Test
    void testRequestRoundtrip_responses() {
        ChatRequest request = buildResponsesRequest();
        ResponsesDialect dialect = new ResponsesDialect();

        Map<String, Object> body = dialect.buildBody(request, newConfig(ApiStyle.responses), null, "gpt-4o", false);
        ChatRequest parsed = dialect.parseRequestBody(body);

        assertMessagesEqual(request.getMessages(), parsed.getMessages());
        assertOptionsEqual(request.getOptions(), parsed.getOptions());
        assertToolsEqual(request.getTools(), parsed.getTools());
    }

    // ==================== E2E 请求方向闭环：原生请求体 → parseRequestBody → buildBody → 原生请求体 ====================

    @Test
    void testE2E_anthropicRequestClosedLoop() {
        AnthropicDialect dialect = new AnthropicDialect();
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("model", "claude-3-5-sonnet-20241022");
        sample.put("stream", false);
        sample.put("max_tokens", 4096);
        sample.put("temperature", 0.7f);
        sample.put("system", "You are a helpful assistant.");
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(messageWithTextBlock("user", "Hello!"));
        messages.add(messageWithTextBlock("model", "Hi back"));
        messages.add(messageWithToolUse());
        messages.add(messageWithToolResult());
        sample.put("messages", messages);
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(toolDefAnthropic());
        sample.put("tools", tools);

        ChatRequest parsed = dialect.parseRequestBody(sample);
        Map<String, Object> rebuilt = dialect.buildBody(parsed, newConfig(ApiStyle.anthropic), null,
                (String) sample.get("model"), false);

        assertEquals(sample.get("model"), rebuilt.get("model"));
        assertEquals(sample.get("stream"), rebuilt.get("stream"));
        assertEquals(4096, ((Number) rebuilt.get("max_tokens")).intValue());
        assertEquals(0.7f, ((Number) rebuilt.get("temperature")).floatValue(), 0.001f);
        assertEquals(sample.get("system"), rebuilt.get("system"));
        assertBlocksEqual((List<?>) sample.get("messages"), (List<?>) rebuilt.get("messages"));
        assertToolsBodyEqual((List<?>) sample.get("tools"), (List<?>) rebuilt.get("tools"));
    }

    @Test
    void testE2E_geminiRequestClosedLoop() {
        GeminiDialect dialect = new GeminiDialect();
        // Gemini buildBody 不产出 model（model 走 URL），样本与断言均不含 model
        Map<String, Object> sample = new LinkedHashMap<>();
        Map<String, Object> systemInstruction = new LinkedHashMap<>();
        systemInstruction.put("parts", List.of(Map.of("text", "You are a helpful assistant.")));
        sample.put("systemInstruction", systemInstruction);
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(contentWithTextPart("user", "Hello!"));
        contents.add(contentWithFunctionCallPart());
        sample.put("contents", contents);
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.7f);
        generationConfig.put("maxOutputTokens", 1024);
        generationConfig.put("topP", 0.9f);
        sample.put("generationConfig", generationConfig);
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(Map.of("functionDeclarations", List.of(toolDefGemini())));
        sample.put("tools", tools);

        ChatRequest parsed = dialect.parseRequestBody(sample);
        Map<String, Object> rebuilt = dialect.buildBody(parsed, newConfig(ApiStyle.gemini), null,
                "gemini-pro", false);

        assertEquals("You are a helpful assistant.",
                ((Map<?, ?>) ((List<?>) ((Map<?, ?>) rebuilt.get("systemInstruction")).get("parts")).get(0)).get("text"));
        assertEquals(0.7f, ((Number) ((Map<?, ?>) rebuilt.get("generationConfig")).get("temperature")).floatValue(), 0.001f);
        assertEquals(1024, ((Number) ((Map<?, ?>) rebuilt.get("generationConfig")).get("maxOutputTokens")).intValue());
        assertEquals(0.9f, ((Number) ((Map<?, ?>) rebuilt.get("generationConfig")).get("topP")).floatValue(), 0.001f);
        assertPartsEqual((List<?>) sample.get("contents"), (List<?>) rebuilt.get("contents"));
        assertNotNull(rebuilt.get("tools"));
    }

    @Test
    void testE2E_ollamaRequestClosedLoop() {
        OllamaDialect dialect = new OllamaDialect();
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("model", "llama2");
        sample.put("stream", false);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "Hello!"));
        messages.add(Map.of("role", "assistant", "content", "Hi back"));
        messages.add(Map.of("role", "assistant", "tool_calls", List.of(toolCallOllama())));
        messages.add(Map.of("role", "tool", "tool_call_id", "call_1", "content", "sunny"));
        sample.put("messages", messages);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", 0.7f);
        options.put("num_predict", 512);
        sample.put("options", options);
        sample.put("tools", List.of(toolDefOpenAi()));

        ChatRequest parsed = dialect.parseRequestBody(sample);
        Map<String, Object> rebuilt = dialect.buildBody(parsed, newConfig(ApiStyle.ollama), null,
                (String) sample.get("model"), false);

        assertEquals(sample.get("model"), rebuilt.get("model"));
        assertEquals(sample.get("stream"), rebuilt.get("stream"));
        assertEquals(0.7f, ((Number) ((Map<?, ?>) rebuilt.get("options")).get("temperature")).floatValue(), 0.001f);
        assertEquals(512, ((Number) ((Map<?, ?>) rebuilt.get("options")).get("num_predict")).intValue());
        List<?> rebuiltMessages = (List<?>) rebuilt.get("messages");
        assertEquals(4, rebuiltMessages.size());
        assertEquals("user", ((Map<?, ?>) rebuiltMessages.get(0)).get("role"));
        assertEquals("Hello!", ((Map<?, ?>) rebuiltMessages.get(0)).get("content"));
        assertEquals("Hi back", ((Map<?, ?>) rebuiltMessages.get(1)).get("content"));
        Map<?, ?> tcMsg = (Map<?, ?>) rebuiltMessages.get(2);
        assertEquals("call_1", ((Map<?, ?>) ((List<?>) tcMsg.get("tool_calls")).get(0)).get("id"));
        assertEquals("tool", ((Map<?, ?>) rebuiltMessages.get(3)).get("role"));
        assertEquals("sunny", ((Map<?, ?>) rebuiltMessages.get(3)).get("content"));
        assertToolsBodyEqual((List<?>) sample.get("tools"), (List<?>) rebuilt.get("tools"));
    }

    @Test
    void testE2E_responsesRequestClosedLoop() {
        ResponsesDialect dialect = new ResponsesDialect();
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("model", "gpt-4o");
        sample.put("stream", false);
        sample.put("instructions", "You are a helpful assistant.");
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(inputTextItem("user", "Hello!"));
        input.add(inputTextItem("assistant", "Hi back"));
        Map<String, Object> fcItem = new LinkedHashMap<>();
        fcItem.put("type", "function_call");
        fcItem.put("call_id", "call_1");
        fcItem.put("name", "get_weather");
        fcItem.put("arguments", "{\"city\":\"SF\"}");
        input.add(fcItem);
        Map<String, Object> fcoItem = new LinkedHashMap<>();
        fcoItem.put("type", "function_call_output");
        fcoItem.put("call_id", "call_1");
        fcoItem.put("output", "sunny");
        input.add(fcoItem);
        sample.put("input", input);
        sample.put("temperature", 0.7f);
        sample.put("max_output_tokens", 1024);
        sample.put("tools", List.of(toolDefResponses()));

        ChatRequest parsed = dialect.parseRequestBody(sample);
        Map<String, Object> rebuilt = dialect.buildBody(parsed, newConfig(ApiStyle.responses), null,
                (String) sample.get("model"), false);

        assertEquals(sample.get("model"), rebuilt.get("model"));
        assertEquals(sample.get("stream"), rebuilt.get("stream"));
        assertEquals(sample.get("instructions"), rebuilt.get("instructions"));
        assertEquals(0.7f, ((Number) rebuilt.get("temperature")).floatValue(), 0.001f);
        assertEquals(1024, ((Number) rebuilt.get("max_output_tokens")).intValue());
        List<?> rebuiltInput = (List<?>) rebuilt.get("input");
        assertEquals(4, rebuiltInput.size());
        assertEquals("message", ((Map<?, ?>) rebuiltInput.get(0)).get("type"));
        assertEquals("Hello!", textOfItem((Map<?, ?>) rebuiltInput.get(0)));
        assertEquals("Hi back", textOfItem((Map<?, ?>) rebuiltInput.get(1)));
        assertEquals("function_call", ((Map<?, ?>) rebuiltInput.get(2)).get("type"));
        assertEquals("{\"city\":\"SF\"}", ((Map<?, ?>) rebuiltInput.get(2)).get("arguments"));
        assertEquals("function_call_output", ((Map<?, ?>) rebuiltInput.get(3)).get("type"));
        assertEquals("sunny", ((Map<?, ?>) rebuiltInput.get(3)).get("output"));
        assertToolsBodyEqual((List<?>) sample.get("tools"), (List<?>) rebuilt.get("tools"));
    }

    // ==================== E2E 响应方向闭环：原生响应 → parseResponse → buildResponse → 原生响应 ====================

    @Test
    void testE2E_openaiResponseClosedLoop() {
        OpenAiDialect dialect = new OpenAiDialect();
        String sample = "{\"id\":\"chatcmpl-1\",\"model\":\"gpt-4\"," +
                "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Hi\"}," +
                "\"finish_reason\":\"stop\"}]," +
                "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}}";

        ChatResponse response = dialect.parseResponse(sample, newConfig(ApiStyle.openai));
        Map<String, Object> rebuilt = dialect.buildResponse(response);

        assertEquals("Hi", (String) ((Map<?, ?>) ((Map<?, ?>) ((List<?>) rebuilt.get("choices")).get(0)).get("message")).get("content"));
        assertEquals("stop", ((Map<?, ?>) ((List<?>) rebuilt.get("choices")).get(0)).get("finish_reason"));
        assertEquals(10, ((Number) ((Map<?, ?>) rebuilt.get("usage")).get("prompt_tokens")).intValue());
        assertEquals(20, ((Number) ((Map<?, ?>) rebuilt.get("usage")).get("completion_tokens")).intValue());
        assertEquals(30, ((Number) ((Map<?, ?>) rebuilt.get("usage")).get("total_tokens")).intValue());
    }

    @Test
    void testE2E_anthropicResponseClosedLoop() {
        AnthropicDialect dialect = new AnthropicDialect();
        String sample = "{\"id\":\"msg_123\",\"model\":\"claude-3-5-sonnet-20241022\"," +
                "\"content\":[{\"type\":\"thinking\",\"thinking\":\"Let me analyze...\"}," +
                "{\"type\":\"text\",\"text\":\"Hello!\"}," +
                "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"get_weather\"," +
                "\"input\":{\"location\":\"beijing\"}}]," +
                "\"stop_reason\":\"end_turn\"," +
                "\"usage\":{\"input_tokens\":10,\"output_tokens\":20," +
                "\"cache_creation_input_tokens\":500,\"cache_read_input_tokens\":200}}";

        ChatResponse response = dialect.parseResponse(sample, newConfig(ApiStyle.anthropic));
        Map<String, Object> rebuilt = dialect.buildResponse(response);

        assertEquals("msg_123", rebuilt.get("id"));
        assertEquals("claude-3-5-sonnet-20241022", rebuilt.get("model"));
        assertEquals("end_turn", rebuilt.get("stop_reason"), "normalized stop reverses to native end_turn");
        List<?> blocks = (List<?>) rebuilt.get("content");
        assertEquals(3, blocks.size());
        assertEquals("thinking", ((Map<?, ?>) blocks.get(0)).get("type"));
        assertEquals("Let me analyze...", ((Map<?, ?>) blocks.get(0)).get("thinking"));
        assertEquals("text", ((Map<?, ?>) blocks.get(1)).get("type"));
        assertEquals("Hello!", ((Map<?, ?>) blocks.get(1)).get("text"));
        assertEquals("tool_use", ((Map<?, ?>) blocks.get(2)).get("type"));
        assertEquals("toolu_1", ((Map<?, ?>) blocks.get(2)).get("id"));
        assertEquals("get_weather", ((Map<?, ?>) blocks.get(2)).get("name"));
        Map<?, ?> usage = (Map<?, ?>) rebuilt.get("usage");
        assertEquals(10, ((Number) usage.get("input_tokens")).intValue());
        assertEquals(20, ((Number) usage.get("output_tokens")).intValue());
        assertEquals(500, ((Number) usage.get("cache_creation_input_tokens")).intValue());
        assertEquals(200, ((Number) usage.get("cache_read_input_tokens")).intValue());
    }

    @Test
    void testE2E_geminiResponseClosedLoop() {
        GeminiDialect dialect = new GeminiDialect();
        String sample = "{\"model\":\"gemini-pro\"," +
                "\"candidates\":[{\"content\":{\"role\":\"model\"," +
                "\"parts\":[{\"text\":\"Let me think\",\"thought\":true},{\"text\":\"Hello!\"}]}," +
                "\"finishReason\":\"STOP\"}]," +
                "\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":20,\"totalTokenCount\":30}}";

        ChatResponse response = dialect.parseResponse(sample, newConfig(ApiStyle.gemini));
        Map<String, Object> rebuilt = dialect.buildResponse(response);

        assertEquals("gemini-pro", rebuilt.get("model"));
        Map<?, ?> candidate = (Map<?, ?>) ((List<?>) rebuilt.get("candidates")).get(0);
        assertEquals("STOP", candidate.get("finishReason"), "normalized stop reverses to native STOP");
        List<?> parts = (List<?>) ((Map<?, ?>) candidate.get("content")).get("parts");
        assertEquals(2, parts.size());
        assertEquals(true, ((Map<?, ?>) parts.get(0)).get("thought"));
        assertEquals("Let me think", ((Map<?, ?>) parts.get(0)).get("text"));
        assertEquals("Hello!", ((Map<?, ?>) parts.get(1)).get("text"));
        Map<?, ?> usage = (Map<?, ?>) rebuilt.get("usageMetadata");
        assertEquals(10, ((Number) usage.get("promptTokenCount")).intValue());
        assertEquals(20, ((Number) usage.get("candidatesTokenCount")).intValue());
        assertEquals(30, ((Number) usage.get("totalTokenCount")).intValue());
    }

    @Test
    void testE2E_ollamaResponseClosedLoop() {
        OllamaDialect dialect = new OllamaDialect();
        String sample = "{\"model\":\"llama2\"," +
                "\"message\":{\"role\":\"assistant\",\"content\":\"Hello\",\"thinking\":\"hmm\"}," +
                "\"done_reason\":\"stop\",\"prompt_eval_count\":10,\"eval_count\":20}";

        ChatResponse response = dialect.parseResponse(sample, newConfig(ApiStyle.ollama));
        Map<String, Object> rebuilt = dialect.buildResponse(response);

        assertEquals("llama2", rebuilt.get("model"));
        assertEquals("stop", rebuilt.get("done_reason"));
        Map<?, ?> message = (Map<?, ?>) rebuilt.get("message");
        assertEquals("Hello", message.get("content"));
        assertEquals("hmm", message.get("thinking"));
        assertEquals(10, ((Number) rebuilt.get("prompt_eval_count")).intValue());
        assertEquals(20, ((Number) rebuilt.get("eval_count")).intValue());
    }

    @Test
    void testE2E_ollamaResponseToolCallClosedLoop() {
        OllamaDialect dialect = new OllamaDialect();
        String sample = "{\"model\":\"qwen3\"," +
                "\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[" +
                "{\"id\":\"call_42\",\"type\":\"function\",\"function\":{" +
                "\"name\":\"get_weather\",\"arguments\":{\"location\":\"beijing\"}}}]}," +
                "\"done_reason\":\"stop\"}";

        ChatResponse response = dialect.parseResponse(sample, newConfig(ApiStyle.ollama));
        Map<String, Object> rebuilt = dialect.buildResponse(response);

        Map<?, ?> message = (Map<?, ?>) rebuilt.get("message");
        List<?> toolCalls = (List<?>) message.get("tool_calls");
        assertNotNull(toolCalls);
        assertEquals(1, toolCalls.size());
        Map<?, ?> tc = (Map<?, ?>) toolCalls.get(0);
        assertEquals("call_42", tc.get("id"));
        assertEquals("get_weather", ((Map<?, ?>) tc.get("function")).get("name"));
        assertEquals("beijing", ((Map<?, ?>) ((Map<?, ?>) tc.get("function")).get("arguments")).get("location"));
    }

    @Test
    void testE2E_responsesResponseClosedLoop() {
        ResponsesDialect dialect = new ResponsesDialect();
        String sample = "{\"id\":\"resp_1\",\"model\":\"gpt-4o\"," +
                "\"output\":[" +
                "{\"type\":\"reasoning\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"let me think\"}]}," +
                "{\"type\":\"message\",\"role\":\"assistant\"," +
                "\"content\":[{\"type\":\"output_text\",\"text\":\"answer\"}]}," +
                "{\"type\":\"function_call\",\"call_id\":\"call_abc\",\"name\":\"get_weather\"," +
                "\"arguments\":\"{\\\"city\\\":\\\"SF\\\"}\"}" +
                "]," +
                "\"status\":\"completed\"," +
                "\"usage\":{\"input_tokens\":10,\"output_tokens\":20,\"total_tokens\":30}}";

        ChatResponse response = dialect.parseResponse(sample, newConfig(ApiStyle.responses));
        Map<String, Object> rebuilt = dialect.buildResponse(response);

        assertEquals("resp_1", rebuilt.get("id"));
        assertEquals("gpt-4o", rebuilt.get("model"));
        assertEquals("completed", rebuilt.get("status"), "normalized stop reverses to native completed");
        List<?> output = (List<?>) rebuilt.get("output");
        assertEquals(3, output.size());
        assertEquals("reasoning", ((Map<?, ?>) output.get(0)).get("type"));
        assertEquals("let me think", textOfItem((Map<?, ?>) output.get(0)));
        assertEquals("message", ((Map<?, ?>) output.get(1)).get("type"));
        assertEquals("answer", textOfItem((Map<?, ?>) output.get(1)));
        assertEquals("function_call", ((Map<?, ?>) output.get(2)).get("type"));
        assertEquals("call_abc", ((Map<?, ?>) output.get(2)).get("call_id"));
        assertEquals("get_weather", ((Map<?, ?>) output.get(2)).get("name"));
        Map<?, ?> args = (Map<?, ?>) JSON.parse((String) ((Map<?, ?>) output.get(2)).get("arguments"));
        assertEquals("SF", args.get("city"));
        Map<?, ?> usage = (Map<?, ?>) rebuilt.get("usage");
        assertEquals(10, ((Number) usage.get("input_tokens")).intValue());
        assertEquals(20, ((Number) usage.get("output_tokens")).intValue());
        assertEquals(30, ((Number) usage.get("total_tokens")).intValue());
    }

    // ==================== E2E 流式方向闭环：原生流块 → parseStreamChunk → buildStreamChunk → 原生流块 ====================

    @Test
    void testE2E_openaiStreamClosedLoop() {
        OpenAiDialect dialect = new OpenAiDialect();
        String sample = "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\"," +
                "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}";

        ChatStreamChunk chunk = dialect.parseStreamChunk(sample);
        Map<String, Object> rebuilt = dialect.buildStreamChunk(chunk);

        assertEquals("chatcmpl-1", rebuilt.get("id"));
        assertEquals("chat.completion.chunk", rebuilt.get("object"));
        Map<?, ?> choice = (Map<?, ?>) ((List<?>) rebuilt.get("choices")).get(0);
        assertEquals(0, ((Number) choice.get("index")).intValue());
        assertEquals("Hello", ((Map<?, ?>) choice.get("delta")).get("content"));
    }

    @Test
    void testE2E_anthropicStreamClosedLoop() {
        AnthropicDialect dialect = new AnthropicDialect();

        // text DELTA
        String deltaSample = "{\"type\":\"content_block_delta\",\"index\":1," +
                "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}";
        Map<String, Object> rebuiltDelta = dialect.buildStreamChunk(dialect.parseStreamChunk(deltaSample));
        assertEquals("content_block_delta", rebuiltDelta.get("type"));
        assertEquals(1, ((Number) rebuiltDelta.get("index")).intValue());
        assertEquals("text_delta", ((Map<?, ?>) rebuiltDelta.get("delta")).get("type"));
        assertEquals("Hello", ((Map<?, ?>) rebuiltDelta.get("delta")).get("text"));

        // tool_call ADDED
        String addedSample = "{\"type\":\"content_block_start\",\"index\":2," +
                "\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"get_weather\"}}";
        Map<String, Object> rebuiltAdded = dialect.buildStreamChunk(dialect.parseStreamChunk(addedSample));
        assertEquals("content_block_start", rebuiltAdded.get("type"));
        assertEquals("toolu_1", ((Map<?, ?>) rebuiltAdded.get("content_block")).get("id"));
        assertEquals("get_weather", ((Map<?, ?>) rebuiltAdded.get("content_block")).get("name"));

        // DONE（message_delta：stop_reason + usage）
        String doneSample = "{\"type\":\"message_delta\"," +
                "\"delta\":{\"stop_reason\":\"end_turn\"}," +
                "\"usage\":{\"output_tokens\":50,\"cache_read_input_tokens\":100}}";
        Map<String, Object> rebuiltDone = dialect.buildStreamChunk(dialect.parseStreamChunk(doneSample));
        assertEquals("message_delta", rebuiltDone.get("type"));
        assertEquals("end_turn", ((Map<?, ?>) rebuiltDone.get("delta")).get("stop_reason"));
        assertEquals(50, ((Number) ((Map<?, ?>) rebuiltDone.get("usage")).get("output_tokens")).intValue());
        assertEquals(100, ((Number) ((Map<?, ?>) rebuiltDone.get("usage")).get("cache_read_input_tokens")).intValue());
    }

    @Test
    void testE2E_geminiStreamClosedLoop() {
        GeminiDialect dialect = new GeminiDialect();

        // text DELTA
        String deltaSample = "{\"candidates\":[{\"content\":{\"role\":\"model\"," +
                "\"parts\":[{\"text\":\"Hel\"}]}}]}";
        Map<String, Object> rebuiltDelta = dialect.buildStreamChunk(dialect.parseStreamChunk(deltaSample));
        List<?> parts = (List<?>) ((Map<?, ?>) ((Map<?, ?>) ((List<?>) rebuiltDelta.get("candidates")).get(0)).get("content")).get("parts");
        assertEquals("Hel", ((Map<?, ?>) parts.get(0)).get("text"));

        // tool_call ADDED
        String addedSample = "{\"candidates\":[{\"content\":{\"role\":\"model\"," +
                "\"parts\":[{\"functionCall\":{\"name\":\"get_weather\"}}]}}]}";
        Map<String, Object> rebuiltAdded = dialect.buildStreamChunk(dialect.parseStreamChunk(addedSample));
        List<?> addedParts = (List<?>) ((Map<?, ?>) ((Map<?, ?>) ((List<?>) rebuiltAdded.get("candidates")).get(0)).get("content")).get("parts");
        assertEquals("get_weather", ((Map<?, ?>) ((Map<?, ?>) addedParts.get(0)).get("functionCall")).get("name"));

        // DONE
        String doneSample = "{\"candidates\":[{\"finishReason\":\"STOP\"}]}";
        Map<String, Object> rebuiltDone = dialect.buildStreamChunk(dialect.parseStreamChunk(doneSample));
        assertEquals("STOP", ((Map<?, ?>) ((List<?>) rebuiltDone.get("candidates")).get(0)).get("finishReason"));
    }

    @Test
    void testE2E_ollamaStreamClosedLoop() {
        OllamaDialect dialect = new OllamaDialect();

        // text DELTA
        String deltaSample = "{\"model\":\"llama2\",\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}";
        Map<String, Object> rebuiltDelta = dialect.buildStreamChunk(dialect.parseStreamChunk(deltaSample));
        assertEquals("llama2", rebuiltDelta.get("model"));
        assertEquals("Hello", ((Map<?, ?>) rebuiltDelta.get("message")).get("content"));

        // tool_call ADDED
        String addedSample = "{\"model\":\"llama2\",\"message\":{\"tool_calls\":[" +
                "{\"id\":\"call_42\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\"}}]}}";
        Map<String, Object> rebuiltAdded = dialect.buildStreamChunk(dialect.parseStreamChunk(addedSample));
        Map<?, ?> tc = (Map<?, ?>) ((List<?>) ((Map<?, ?>) rebuiltAdded.get("message")).get("tool_calls")).get(0);
        assertEquals("call_42", tc.get("id"));
        assertEquals("get_weather", ((Map<?, ?>) tc.get("function")).get("name"));

        // DONE
        String doneSample = "{\"model\":\"llama2\",\"done_reason\":\"stop\"}";
        Map<String, Object> rebuiltDone = dialect.buildStreamChunk(dialect.parseStreamChunk(doneSample));
        assertEquals("stop", rebuiltDone.get("done_reason"));
    }

    @Test
    void testE2E_responsesStreamClosedLoop() {
        ResponsesDialect dialect = new ResponsesDialect();

        // text DELTA
        String deltaSample = "{\"type\":\"response.output_text.delta\",\"output_index\":0,\"delta\":\"Hello\"}";
        Map<String, Object> rebuiltDelta = dialect.buildStreamChunk(dialect.parseStreamChunk(deltaSample));
        assertEquals("response.output_text.delta", rebuiltDelta.get("type"));
        assertEquals(0, ((Number) rebuiltDelta.get("output_index")).intValue());
        assertEquals("Hello", rebuiltDelta.get("delta"));

        // tool_call ADDED
        String addedSample = "{\"type\":\"response.output_item.added\",\"output_index\":2," +
                "\"item\":{\"type\":\"function_call\",\"call_id\":\"call_abc\"," +
                "\"name\":\"get_weather\",\"arguments\":\"\"}}";
        Map<String, Object> rebuiltAdded = dialect.buildStreamChunk(dialect.parseStreamChunk(addedSample));
        assertEquals("response.output_item.added", rebuiltAdded.get("type"));
        assertEquals("function_call", ((Map<?, ?>) rebuiltAdded.get("item")).get("type"));
        assertEquals("call_abc", ((Map<?, ?>) rebuiltAdded.get("item")).get("call_id"));
        assertEquals("get_weather", ((Map<?, ?>) rebuiltAdded.get("item")).get("name"));

        // DONE
        String doneSample = "{\"type\":\"response.completed\"," +
                "\"response\":{\"id\":\"resp_1\",\"model\":\"gpt-4o\",\"status\":\"completed\"," +
                "\"usage\":{\"input_tokens\":10,\"output_tokens\":20,\"total_tokens\":30}}}";
        Map<String, Object> rebuiltDone = dialect.buildStreamChunk(dialect.parseStreamChunk(doneSample));
        assertEquals("response.completed", rebuiltDone.get("type"));
        assertEquals("completed", ((Map<?, ?>) rebuiltDone.get("response")).get("status"));
        assertEquals("resp_1", ((Map<?, ?>) rebuiltDone.get("response")).get("id"));
        assertEquals(10, ((Number) ((Map<?, ?>) ((Map<?, ?>) rebuiltDone.get("response")).get("usage")).get("input_tokens")).intValue());
    }

    // ==================== 请求构造（各 dialect 正向格式样本） ====================

    private ChatRequest buildOpenAiRequest() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("You are a helpful assistant."));
        messages.add(new ChatUserMessage("Hello!"));
        messages.add(new ChatAssistantMessage("Hi back"));
        ChatRequest request = new ChatRequest(messages, openAiOptions());
        request.setTools(List.of(toToolDefinition(toolDefOpenAi())));
        return request;
    }

    private ChatRequest buildAnthropicRequest() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("You are a helpful assistant."));
        messages.add(new ChatUserMessage("Hello!"));
        messages.add(new ChatAssistantMessage("Hi back"));
        messages.add(new ChatToolCallMessage("toolu_1", "get_weather", Map.of("location", "beijing")));
        messages.add(new ChatToolResponseMessage("toolu_1", null, "sunny"));
        ChatOptions options = new ChatOptions();
        options.setTemperature(0.7f);
        options.setMaxTokens(4096); // 有损点 (b)：显式设置避免 buildBody 默认 4096 注入干扰
        options.setTopP(0.9f);
        options.setStop(List.of("END"));
        ChatRequest request = new ChatRequest(messages, options);
        request.setTools(List.of(toToolDefinition(toolDefAnthropic())));
        return request;
    }

    private ChatRequest buildGeminiRequest() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("You are a helpful assistant."));
        messages.add(new ChatUserMessage("Hello!"));
        messages.add(new ChatAssistantMessage("Hi back"));
        messages.add(new ChatToolCallMessage(null, "get_weather", Map.of("location", "beijing")));
        messages.add(new ChatToolResponseMessage(null, "get_weather", "sunny"));
        ChatOptions options = new ChatOptions();
        options.setTemperature(0.7f);
        options.setMaxTokens(1024);
        options.setTopP(0.9f);
        options.setTopK(10);
        ChatRequest request = new ChatRequest(messages, options);
        request.setTools(List.of(toToolDefinition(toolDefGemini())));
        return request;
    }

    private ChatRequest buildOllamaRequest() {
        // Ollama buildBody 丢弃 system 消息（落档有损点），样本不含 system
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatUserMessage("Hello!"));
        messages.add(new ChatAssistantMessage("Hi back"));
        messages.add(new ChatToolCallMessage("call_1", "get_weather", Map.of("location", "beijing")));
        messages.add(new ChatToolResponseMessage("call_1", "get_weather", "sunny"));
        ChatOptions options = new ChatOptions();
        options.setTemperature(0.7f);
        options.setMaxTokens(512);
        options.setTopP(0.9f);
        ChatRequest request = new ChatRequest(messages, options);
        request.setTools(List.of(toToolDefinition(toolDefOpenAi())));
        return request;
    }

    private ChatRequest buildResponsesRequest() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("You are a helpful assistant."));
        messages.add(new ChatUserMessage("Hello!"));
        messages.add(new ChatAssistantMessage("Hi back"));
        messages.add(new ChatToolCallMessage("call_1", "get_weather", Map.of("city", "SF")));
        messages.add(new ChatToolResponseMessage("call_1", null, "sunny"));
        ChatOptions options = new ChatOptions();
        options.setTemperature(0.7f);
        options.setMaxTokens(1024);
        options.setTopP(0.9f);
        ChatRequest request = new ChatRequest(messages, options);
        request.setTools(List.of(toToolDefinition(toolDefResponses())));
        return request;
    }

    private ChatOptions openAiOptions() {
        ChatOptions options = new ChatOptions();
        options.setTemperature(0.7f);
        options.setMaxTokens(1000);
        options.setTopP(0.9f);
        return options;
    }

    // ==================== 断言辅助 ====================

    private void assertMessagesEqual(List<ChatMessage> expected, List<ChatMessage> actual) {
        if (expected == null) {
            assertNull(actual);
            return;
        }
        assertNotNull(actual);
        assertEquals(expected.size(), actual.size(), "message count");
        for (int i = 0; i < expected.size(); i++) {
            ChatMessage e = expected.get(i);
            ChatMessage a = actual.get(i);
            assertEquals(e.getClass(), a.getClass(), "message type at " + i);
            assertEquals(e.getContent(), a.getContent(), "message content at " + i);
            if (e instanceof ChatToolCallMessage) {
                ChatToolCallMessage et = (ChatToolCallMessage) e;
                ChatToolCallMessage at = (ChatToolCallMessage) a;
                assertEquals(et.getCallId(), at.getCallId(), "callId at " + i);
                assertEquals(et.getName(), at.getName(), "name at " + i);
                assertEquals(et.getArguments(), at.getArguments(), "arguments at " + i);
            }
            if (e instanceof ChatToolResponseMessage) {
                ChatToolResponseMessage et = (ChatToolResponseMessage) e;
                ChatToolResponseMessage at = (ChatToolResponseMessage) a;
                assertEquals(et.getCallId(), at.getCallId(), "callId at " + i);
                assertEquals(et.getContent(), at.getContent(), "content at " + i);
            }
        }
    }

    private void assertOptionsEqual(ChatOptions expected, ChatOptions actual) {
        assertNumEq(expected.getTemperature(), actual.getTemperature(), "temperature");
        assertNumEq(expected.getTopP(), actual.getTopP(), "topP");
        assertNumEq(expected.getTopK(), actual.getTopK(), "topK");
        assertNumEq(expected.getMaxTokens(), actual.getMaxTokens(), "maxTokens");
        assertEquals(expected.getStop(), actual.getStop(), "stop");
        assertEquals(expected.getToolChoice(), actual.getToolChoice(), "toolChoice");
    }

    private void assertNumEq(Object expected, Object actual, String field) {
        if (expected == null) {
            assertNull(actual, field);
            return;
        }
        assertNotNull(actual, field);
        assertEquals(((Number) expected).floatValue(), ((Number) actual).floatValue(), 0.001f, field);
    }

    private void assertToolsEqual(List<ChatToolDefinition> expected, List<ChatToolDefinition> actual) {
        if (expected == null) {
            assertNull(actual);
            return;
        }
        assertNotNull(actual);
        assertEquals(expected.size(), actual.size(), "tool count");
        for (int i = 0; i < expected.size(); i++) {
            ChatToolDefinition e = expected.get(i);
            ChatToolDefinition a = actual.get(i);
            assertEquals(e.getName(), a.getName(), "tool name at " + i);
            assertEquals(e.getDescription(), a.getDescription(), "tool description at " + i);
            assertEquals(e.getParameters(), a.getParameters(), "tool parameters at " + i);
        }
    }

    private void assertBlocksEqual(List<?> expected, List<?> actual) {
        assertEquals(expected.size(), actual.size(), "message count");
        for (int i = 0; i < expected.size(); i++) {
            Map<?, ?> e = (Map<?, ?>) expected.get(i);
            Map<?, ?> a = (Map<?, ?>) actual.get(i);
            assertEquals(e.get("role"), a.get("role"), "role at " + i);
            List<?> eBlocks = (List<?>) e.get("content");
            List<?> aBlocks = (List<?>) a.get("content");
            assertEquals(eBlocks.size(), aBlocks.size(), "block count at " + i);
            for (int j = 0; j < eBlocks.size(); j++) {
                assertEquals(eBlocks.get(j), aBlocks.get(j), "block " + j + " at message " + i);
            }
        }
    }

    private void assertPartsEqual(List<?> expected, List<?> actual) {
        assertEquals(expected.size(), actual.size(), "content count");
        for (int i = 0; i < expected.size(); i++) {
            Map<?, ?> e = (Map<?, ?>) expected.get(i);
            Map<?, ?> a = (Map<?, ?>) actual.get(i);
            assertEquals(e.get("role"), a.get("role"), "role at " + i);
            List<?> eParts = (List<?>) e.get("parts");
            List<?> aParts = (List<?>) a.get("parts");
            assertEquals(eParts.size(), aParts.size(), "part count at " + i);
            for (int j = 0; j < eParts.size(); j++) {
                assertEquals(eParts.get(j), aParts.get(j), "part " + j + " at content " + i);
            }
        }
    }

    private void assertToolsBodyEqual(List<?> expected, List<?> actual) {
        assertEquals(expected.size(), actual.size(), "tool count");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), "tool at " + i);
        }
    }

    // ==================== 样本构造辅助 ====================

    private Map<String, Object> messageWithTextBlock(String role, String text) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", List.of(Map.of("type", "text", "text", text)));
        return msg;
    }

    private Map<String, Object> messageWithToolUse() {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "model");
        // 含 convertMessage 伪影 text block（ChatToolCallMessage.getContent()=arguments JSON）——
        // 这是 buildBody 产出的原生形态，E2E 闭环样本必须与之一致
        Map<String, Object> textBlock = new LinkedHashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", "{\"location\":\"beijing\"}");
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool_use");
        block.put("id", "toolu_1");
        block.put("name", "get_weather");
        block.put("input", Map.of("location", "beijing"));
        msg.put("content", List.of(textBlock, block));
        return msg;
    }

    private Map<String, Object> messageWithToolResult() {
        Map<String, Object> msg = new LinkedHashMap<>();
        // getRole(ChatToolResponseMessage)="tool"（dialect 原生形态），与 buildBody 输出一致
        msg.put("role", "tool");
        // 含 convertMessage 伪影 text block（ChatToolResponseMessage.content=result 文本）
        Map<String, Object> textBlock = new LinkedHashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", "sunny");
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool_result");
        block.put("tool_use_id", "toolu_1");
        block.put("content", "sunny");
        msg.put("content", List.of(textBlock, block));
        return msg;
    }

    private Map<String, Object> contentWithTextPart(String role, String text) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", role);
        content.put("parts", List.of(Map.of("text", text)));
        return content;
    }

    private Map<String, Object> contentWithFunctionCallPart() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "model");
        // 含 convertMessage 伪影 text part（ChatToolCallMessage.getContent()=arguments JSON）——
        // 这是 buildBody 产出的原生形态，E2E 闭环样本必须与之一致
        Map<String, Object> functionCall = new LinkedHashMap<>();
        functionCall.put("name", "get_weather");
        functionCall.put("args", Map.of("location", "beijing"));
        content.put("parts", List.of(Map.of("text", "{\"location\":\"beijing\"}"), Map.of("functionCall", functionCall)));
        return content;
    }

    private Map<String, Object> inputTextItem(String role, String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "message");
        item.put("role", role);
        item.put("content", List.of(Map.of("type", "input_text", "text", text)));
        return item;
    }

    private String textOfItem(Map<?, ?> item) {
        List<?> parts = (List<?>) item.get("content");
        if (parts == null) {
            parts = (List<?>) item.get("summary");
        }
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        return (String) ((Map<?, ?>) parts.get(0)).get("text");
    }

    private Map<String, Object> toolDefAnthropic() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "get_weather");
        tool.put("description", "Get weather");
        tool.put("input_schema", Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));
        return tool;
    }

    private Map<String, Object> toolDefGemini() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "get_weather");
        tool.put("description", "Get weather");
        tool.put("parameters", Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));
        return tool;
    }

    private Map<String, Object> toolDefOpenAi() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        Map<String, Object> func = new LinkedHashMap<>();
        func.put("name", "get_weather");
        func.put("description", "Get weather");
        func.put("parameters", Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));
        tool.put("function", func);
        return tool;
    }

    private Map<String, Object> toolDefResponses() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("name", "get_weather");
        tool.put("description", "Get weather");
        tool.put("parameters", Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));
        return tool;
    }

    private Map<String, Object> toolCallOllama() {
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "call_1");
        tc.put("type", "function");
        Map<String, Object> func = new LinkedHashMap<>();
        func.put("name", "get_weather");
        func.put("arguments", Map.of("location", "beijing"));
        tc.put("function", func);
        return tc;
    }

    private ChatToolDefinition toToolDefinition(Map<String, Object> tool) {
        if (tool.containsKey("function")) {
            Map<?, ?> func = (Map<?, ?>) tool.get("function");
            return ChatToolDefinition.of((String) func.get("name"), (String) func.get("description"),
                    (Map<String, Object>) func.get("parameters"));
        }
        return ChatToolDefinition.of((String) tool.get("name"), (String) tool.get("description"),
                (Map<String, Object>) tool.get("parameters"));
    }
}
