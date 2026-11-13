package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.StreamItemPhase;
import io.nop.ai.api.chat.stream.StreamItemType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2519 (裁定⑧ / 3.1-C3-028, 红⑧ / C10-002 fixture matrix): the dialect
 * streaming-protocol family — four fixture axes over the five shipped
 * dialects:
 *
 * <ol>
 *   <li>single event, multiple tool_call entries — FULL enumeration (no
 *       first-entry truncation: OpenAI / Ollama; Gemini multi-part).</li>
 *   <li>empty-string frames no longer preempt the ladder (Ollama C3-031:
 *       thinking / tool_call frames reachable on empty content).</li>
 *   <li>usage four-dialect matrix (OpenAI include_usage terminal frame,
 *       Anthropic message_start cache tokens, Gemini usageMetadata, Ollama
 *       eval_count) — usage never null when the provider announces it.</li>
 *   <li>typed in-band error channel (Anthropic {@code type=error} →
 *       {@link DialectErrorChunk}, never a {@code [ERROR] ...} text DELTA).</li>
 * </ol>
 *
 * <p>Defect-state red: pre-fix, axis 1 truncated to the first entry, axis 2
 * dropped thinking/tool_calls behind the empty-string content branch, axis 3
 * returned null usage on all four chains, axis 4 polluted assistant text.
 * Snapshot: {@code _tmp/red-green-2519-red8.log} / {@code -green8.log}
 * (mutation probes: get(0) truncation, empty-string preemption, usage parsers
 * disabled, error→text DELTA).
 */
public class TestDialectProtocolFixtures_C3_028 extends io.nop.autotest.junit.JunitBaseTestCase {

    // ---- axis 1: single event, multiple tool_call entries — full enumeration

    @Test
    void testOpenAiSingleEventMultiToolCallsFullyEnumerated() {
        OpenAiDialect dialect = new OpenAiDialect();
        String data = "{\"id\":\"c1\",\"choices\":[{\"delta\":{\"tool_calls\":["
                + "{\"index\":0,\"id\":\"call-a\",\"type\":\"function\",\"function\":{\"name\":\"fnA\",\"arguments\":\"{\\\"x\\\"\"}},"
                + "{\"index\":1,\"id\":\"call-b\",\"type\":\"function\",\"function\":{\"name\":\"fnB\",\"arguments\":\"\"}}"
                + "]}}]}";
        List<ChatStreamChunk> chunks = dialect.parseStreamChunks(data);
        assertEquals(2, chunks.size(),
                "裁定⑧: both tool_call entries of one SSE event must be enumerated (pre-fix: first only)");
        assertEquals(StreamItemType.tool_call, chunks.get(0).getItemType());
        assertEquals("fnA", chunks.get(0).getDelta());
        assertEquals(StreamItemType.tool_call, chunks.get(1).getItemType());
        assertEquals("fnB", chunks.get(1).getDelta());
        // single-chunk compat delegate stays coherent (first entry, others reachable
        // through parseStreamChunks — the TestOllamaDialect :191-style parameter
        // retention pins assert no weakening)
        ChatStreamChunk single = dialect.parseStreamChunk(data);
        assertNotNull(single);
        assertEquals(StreamItemType.tool_call, single.getItemType());
    }

    @Test
    void testGeminiMultiPartEventFullyEnumerated() {
        GeminiDialect dialect = new GeminiDialect();
        String data = "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\"part-one\"},{\"text\":\"part-two\"},"
                + "{\"functionCall\":{\"name\":\"fnC\",\"args\":{\"y\":1}}}"
                + "]}}]}";
        List<ChatStreamChunk> chunks = dialect.parseStreamChunks(data);
        assertEquals(3, chunks.size(),
                "裁定⑧: every part of the Gemini candidate must be enumerated (pre-fix: first part only)");
        assertEquals("part-one", chunks.get(0).getDelta());
        assertEquals("part-two", chunks.get(1).getDelta());
        assertEquals(StreamItemType.tool_call, chunks.get(2).getItemType());
    }

    // ---- axis 2: empty-string frames no longer preempt the ladder (C3-031)

    @Test
    void testOllamaEmptyContentFrameDoesNotMaskThinking() {
        OllamaDialect dialect = new OllamaDialect();
        // Ollama reasoning models emit an empty content alongside message.thinking
        String data = "{\"model\":\"llama2\",\"message\":{\"content\":\"\",\"thinking\":\"reason-step\"}}";
        List<ChatStreamChunk> chunks = dialect.parseStreamChunks(data);
        assertEquals(1, chunks.size(), "the empty-string content must not preempt the ladder");
        assertEquals(StreamItemType.reasoning, chunks.get(0).getItemType(),
                "C3-031: thinking must be reachable on an empty-content frame (pre-fix: masked)");
        assertEquals("reason-step", chunks.get(0).getDelta());
    }

    @Test
    void testOllamaEmptyContentFrameDoesNotMaskToolCalls() {
        OllamaDialect dialect = new OllamaDialect();
        String data = "{\"model\":\"llama2\",\"message\":{\"content\":\"\",\"tool_calls\":["
                + "{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"fnO\",\"arguments\":{\"z\":2}}}"
                + "]}}";
        List<ChatStreamChunk> chunks = dialect.parseStreamChunks(data);
        assertEquals(1, chunks.size(), "the empty-string content must not preempt the ladder");
        assertEquals(StreamItemType.tool_call, chunks.get(0).getItemType(),
                "C3-031: tool_calls must be reachable on an empty-content frame");
        assertEquals("fnO", chunks.get(0).getDelta());
    }

    // ---- axis 3: usage four-dialect matrix

    @Test
    void testOpenAiUsageOnlyTerminalFrameParsesUsage() {
        OpenAiDialect dialect = new OpenAiDialect();
        // include_usage terminal frame: empty choices + usage
        String data = "{\"id\":\"c1\",\"choices\":[],\"usage\":{\"prompt_tokens\":11,"
                + "\"completion_tokens\":7,\"total_tokens\":18}}";
        List<ChatStreamChunk> chunks = dialect.parseStreamChunks(data);
        assertEquals(1, chunks.size(),
                "the usage-only terminal frame must produce a usage chunk (choices absent/empty tolerated)");
        assertNotNull(chunks.get(0).getUsage(), "pre-fix: stream usage was null on all OpenAI chains");
        assertEquals(11L, chunks.get(0).getUsage().getPromptTokens().longValue());
        assertEquals(7L, chunks.get(0).getUsage().getCompletionTokens().longValue());
        // the request body must ask the provider for usage (stream_options.include_usage)
        io.nop.ai.api.chat.ChatRequest request = io.nop.ai.api.chat.ChatRequest.userPrompt("hi");
        request.setOptions(io.nop.ai.api.chat.ChatOptions.builder()
                .provider("test").stream(true).build());
        io.nop.ai.core.model.LlmModel config = new io.nop.ai.core.model.LlmModel();
        config.setApiStyle(io.nop.ai.core.model.ApiStyle.openai);
        java.util.Map<String, Object> body = dialect.buildBody(request, config, null, "gpt-4", true);
        Object streamOptions = body.get("stream_options");
        assertNotNull(streamOptions, "裁定⑧: stream_options.include_usage must be enabled for stream=true");
        assertEquals(Boolean.TRUE, ((java.util.Map<?, ?>) streamOptions).get("include_usage"));
    }

    @Test
    void testAnthropicMessageStartUsageCarriesCacheTokens() {
        AnthropicDialect dialect = new AnthropicDialect();
        String data = "{\"type\":\"message_start\",\"message\":{\"model\":\"claude\",\"usage\":{"
                + "\"input_tokens\":100,\"cache_creation_input_tokens\":21,\"cache_read_input_tokens\":34}}}";
        ChatStreamChunk chunk = dialect.parseStreamChunk(data);
        assertNotNull(chunk);
        assertNotNull(chunk.getUsage(),
                "C3-030: message_start usage must enter the streaming chain");
        assertEquals(100L, chunk.getUsage().getPromptTokens().longValue());
        assertEquals(21L, chunk.getUsage().getCacheCreationTokens().longValue(),
                "cache_creation tokens must be mapped (aligned with the non-streaming mapping)");
        assertEquals(34L, chunk.getUsage().getCacheHitTokens().longValue(),
                "cache_read tokens must be mapped");
    }

    @Test
    void testGeminiUsageMetadataParsesUsage() {
        GeminiDialect dialect = new GeminiDialect();
        String data = "{\"candidates\":[],\"usageMetadata\":{\"promptTokenCount\":5,"
                + "\"candidatesTokenCount\":6,\"totalTokenCount\":11}}";
        List<ChatStreamChunk> chunks = dialect.parseStreamChunks(data);
        assertEquals(1, chunks.size());
        assertNotNull(chunks.get(0).getUsage(), "C3-030: Gemini usageMetadata must enter the streaming chain");
        assertEquals(5L, chunks.get(0).getUsage().getPromptTokens().longValue());
        assertEquals(6L, chunks.get(0).getUsage().getCompletionTokens().longValue());
        assertEquals(11L, chunks.get(0).getUsage().getTotalTokens().longValue());
    }

    @Test
    void testOllamaEvalCountParsesUsage() {
        OllamaDialect dialect = new OllamaDialect();
        String data = "{\"model\":\"llama2\",\"message\":{\"content\":\"\"},\"done\":true,"
                + "\"done_reason\":\"stop\",\"eval_count\":9,\"prompt_eval_count\":4}";
        List<ChatStreamChunk> chunks = dialect.parseStreamChunks(data);
        assertEquals(1, chunks.size(),
                "the done frame must enumerate to exactly the DONE chunk (usage merged, not dropped)");
        assertEquals(StreamItemPhase.DONE, chunks.get(0).getPhase());
        assertEquals("stop", chunks.get(0).getFinishReason());
        assertNotNull(chunks.get(0).getUsage(), "C3-030: Ollama eval_count must enter the streaming chain");
        assertEquals(9L, chunks.get(0).getUsage().getCompletionTokens().longValue());
        assertEquals(4L, chunks.get(0).getUsage().getPromptTokens().longValue());
    }

    // ---- axis 4: typed in-band error channel (C3-032)

    @Test
    void testAnthropicErrorEventIsTypedErrorChunkNotTextPollution() {
        AnthropicDialect dialect = new AnthropicDialect();
        String data = "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\","
                + "\"message\":\"Overloaded\"}}";
        ChatStreamChunk chunk = dialect.parseStreamChunk(data);
        assertTrue(chunk instanceof DialectErrorChunk,
                "裁定⑧: in-band error must be a typed error chunk, got: "
                        + (chunk == null ? "null" : chunk.getClass().getName()));
        DialectErrorChunk errorChunk = (DialectErrorChunk) chunk;
        assertEquals("overloaded_error", errorChunk.getErrorType());
        assertEquals("Overloaded", errorChunk.getErrorMessage());
        // well-formed terminal chunk for base-class consumers: never merged into
        // assistant text — no text DELTA payload carrying "[ERROR] ..."
        assertEquals(StreamItemPhase.DONE, errorChunk.getPhase());
        assertEquals("error", errorChunk.getFinishReason());
    }

    @Test
    void testResponsesDialectImmuneFaceUnchanged() {
        ResponsesDialect dialect = new ResponsesDialect();
        // Responses is usage/tool_call-immune (design §3.8 narrowed immunity) —
        // the pin asserts the face is unchanged by the family fix (zero diff face)
        String data = "{\"type\":\"response.output_text.delta\",\"output_index\":0,\"delta\":\"hi\"}";
        ChatStreamChunk chunk = dialect.parseStreamChunk(data);
        assertNotNull(chunk);
        assertEquals("hi", chunk.getDelta());
        assertNull(chunk.getUsage());
    }
}
