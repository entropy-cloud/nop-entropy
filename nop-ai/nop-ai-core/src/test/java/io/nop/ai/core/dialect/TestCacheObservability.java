package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.ai.core.model.LlmResponseModel;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2303 (WI21): cache observability across dialects — out-of-the-box
 * default paths (no per-provider configuration needed), explicit-config
 * precedence, raw usage capture, and the honest "no cache fields" case
 * (Ollama). Non-stream response surface (streaming usage is a registered
 * residual anchored to WI23).
 */
public class TestCacheObservability extends JunitBaseTestCase {

    @AfterEach
    void tearDown() {
        LlmConfigHelper.reset();
    }

    private static ChatRequest request() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new io.nop.ai.api.chat.messages.ChatUserMessage("hi"));
        ChatRequest request = new ChatRequest(messages);
        request.setOptions(new ChatOptions());
        return request;
    }

    @Test
    void openAiCacheHitParsedByDefaultWithoutConfig() {
        OpenAiDialect dialect = new OpenAiDialect();
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],"
                + "\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":10,\"total_tokens\":110,"
                + "\"prompt_tokens_details\":{\"cached_tokens\":80}}}";

        ChatResponse response = dialect.parseResponse(body, new LlmModel());
        ChatUsage usage = response.getUsage();
        assertEquals(80, usage.getCacheHitTokens(), "out-of-the-box default path parses the cached tokens");
        assertNull(usage.getCacheCreationTokens(), "OpenAI has no cache-write concept — creation stays unset");
        assertEquals(100, usage.getPromptTokens());
        Map<String, Object> raw = usage.getRawUsage();
        assertEquals(100, raw.get("prompt_tokens"), "raw usage object captured (dialect-level root)");
        assertEquals(80, ((Map<?, ?>) raw.get("prompt_tokens_details")).get("cached_tokens"));
    }

    @Test
    public void explicitConfigPathBeatsDialectDefault() {
        OpenAiDialect dialect = new OpenAiDialect();
        String body = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}],"
                + "\"usage\":{\"prompt_tokens\":50,\"prompt_tokens_details\":{\"cached_tokens\":40}}}";
        LlmModel config = new LlmModel();
        LlmResponseModel responseConfig = new LlmResponseModel();
        responseConfig.setPromptCacheHitTokensPath("usage.some_custom_field");
        config.setResponse(responseConfig);

        ChatResponse response = dialect.parseResponse(body, config);
        assertNull(response.getUsage().getCacheHitTokens(), "explicit config path wins (missing field → null)");
        assertEquals(40, response.getUsage().getRawUsage().get("prompt_tokens_details") instanceof Map
                ? ((Map<?, ?>) response.getUsage().getRawUsage().get("prompt_tokens_details")).get("cached_tokens")
                : null,
                "raw capture is independent of the path resolution");
    }

    @Test
    public void geminiCacheHitParsedByDefault() {
        GeminiDialect dialect = new GeminiDialect();
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}],\"role\":\"model\"}}],"
                + "\"usageMetadata\":{\"promptTokenCount\":100,\"candidatesTokenCount\":10,"
                + "\"totalTokenCount\":110,\"cachedContentTokenCount\":60}}";

        ChatResponse response = dialect.parseResponse(body, new LlmModel());
        assertEquals(60, response.getUsage().getCacheHitTokens());
        assertEquals("gemini-root", response.getUsage().getRawUsage().containsKey("promptTokenCount")
                ? "gemini-root" : null, "raw usage root is usageMetadata");
        assertEquals(100, response.getUsage().getRawUsage().get("promptTokenCount"));
    }

    @Test
    public void ollamaHasNoCacheFieldsByDesign() {
        OllamaDialect dialect = new OllamaDialect();
        String body = "{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},"
                + "\"prompt_eval_count\":100,\"eval_count\":10,\"done\":true}";

        ChatResponse response = dialect.parseResponse(body, new LlmModel());
        assertEquals(100, response.getUsage().getPromptTokens());
        assertNull(response.getUsage().getCacheHitTokens(),
                "Ollama local runtime has no cache fields — explicit null, not an omission");
        assertNull(response.getUsage().getRawUsage(), "no usage root — no raw capture");
    }

    @Test
    public void anthropicRawUsageCapturedWithoutTouchingBespokeCacheMapping() {
        AnthropicDialect dialect = new AnthropicDialect();
        String body = "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],"
                + "\"stop_reason\":\"end_turn\","
                + "\"usage\":{\"input_tokens\":100,\"output_tokens\":10,"
                + "\"cache_read_input_tokens\":70,\"cache_creation_input_tokens\":20}}";

        ChatResponse response = dialect.parseResponse(body, new LlmModel());
        assertEquals(70, response.getUsage().getCacheHitTokens(), "bespoke Anthropic mapping untouched");
        assertEquals(20, response.getUsage().getCacheCreationTokens());
        assertEquals(100, response.getUsage().getRawUsage().get("input_tokens"), "raw usage captured");
    }

    @Test
    public void responsesCacheHitParsedInNonStreamAndStreamPaths() {
        ResponsesDialect dialect = new ResponsesDialect();
        String body = "{\"id\":\"resp_1\",\"status\":\"completed\","
                + "\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"ok\"}]}],"
                + "\"usage\":{\"input_tokens\":100,\"output_tokens\":10,\"total_tokens\":110,"
                + "\"input_tokens_details\":{\"cached_tokens\":64}}}";

        ChatResponse response = dialect.parseResponse(body, new LlmModel());
        assertEquals(64, response.getUsage().getCacheHitTokens(), "non-stream cached tokens parsed");
        assertEquals(100, response.getUsage().getRawUsage().get("input_tokens"));

        String chunk = "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\","
                + "\"usage\":{\"input_tokens\":100,\"output_tokens\":10,"
                + "\"input_tokens_details\":{\"cached_tokens\":64}}}}";
        io.nop.ai.api.chat.stream.ChatStreamChunk parsed = dialect.parseStreamChunk(chunk);
        assertEquals(64, parsed.getUsage().getCacheHitTokens(), "stream path shares the same parser");
    }

    @Test
    public void usageCopyCarriesRawUsageByReference() {
        ChatUsage usage = new ChatUsage(100, 10);
        usage.setCacheHitTokens(80);
        Map<String, Object> raw = Map.of("prompt_tokens", 100);
        usage.setRawUsage(raw);

        ChatUsage copy = usage.copy();
        assertSame(raw, copy.getRawUsage(), "raw usage is a shared read-only view (plan 2303 copy semantics)");
        assertEquals(80, copy.getCacheHitTokens());
    }

    @Test
    public void deepseekExplicitConfigOverridesOpenAiDefaultAndKeepsInheritedPaths() {
        // x:extends merge semantics guard: the child <response> node carries
        // only the cache-hit path — the inherited OpenAI-form paths must
        // survive the merge, and the explicit deepseek path must win over the
        // OpenAI dialect default
        LlmModel config = LlmConfigHelper.loadConfig("deepseek");
        LlmResponseModel response = config.getResponse();
        assertEquals("usage.prompt_cache_hit_tokens", response.getPromptCacheHitTokensPath(),
                "deepseek explicit cache-hit path in place");
        assertEquals("choices.0.message.content", response.getContentPath(),
                "inherited OpenAI-form paths survive the x:extends merge");
        assertEquals("usage.prompt_tokens", response.getPromptTokensPath());
        assertNull(response.getPromptCacheCreationTokensPath(), "DeepSeek has no cache-write concept");
    }
}
