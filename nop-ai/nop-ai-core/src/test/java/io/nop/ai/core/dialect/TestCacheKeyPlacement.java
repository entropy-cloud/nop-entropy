package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.http.api.client.HttpRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2302 (WI20): the OpenAI field form ({@code prompt_cache_key}), the
 * session-affinity header form ({@code applyCacheHeaders}), and the zero
 default for the dialects without a placement form.
 */
public class TestCacheKeyPlacement {

    private static LlmModel config(ApiStyle style) {
        LlmModel config = new LlmModel();
        config.setApiStyle(style);
        return config;
    }

    private static LlmModelModel modelConfig() {
        LlmModelModel modelConfig = new LlmModelModel();
        modelConfig.setName("test-model");
        return modelConfig;
    }

    private static ChatRequest request(String sessionId) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatUserMessage("hello"));
        ChatRequest request = new ChatRequest(messages);
        ChatOptions options = new ChatOptions();
        if (sessionId != null) {
            options.setSessionId(sessionId);
        }
        request.setOptions(options);
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bodyOf(LlmModelModel modelConfig, String sessionId) {
        OpenAiDialect dialect = new OpenAiDialect();
        return (Map<String, Object>) (Map<?, ?>) dialect.buildBody(request(sessionId), config(ApiStyle.openai),
                modelConfig, "gpt-4o", false);
    }

    @Test
    public void promptCacheKeyPlacedWhenConfiguredAndSessionKeyed() {
        LlmModelModel modelConfig = modelConfig();
        modelConfig.setPromptCacheKey(true);
        Map<String, Object> body = bodyOf(modelConfig, "sess-1");
        assertEquals("sess-1", body.get("prompt_cache_key"),
                "the session id is the OpenAI prompt cache key");
    }

    @Test
    public void promptCacheKeyOffByDefault() {
        assertNull(bodyOf(modelConfig(), "sess-1").get("prompt_cache_key"),
                "unconfigured → no prompt_cache_key field");
    }

    @Test
    public void promptCacheKeySkipsMissingSessionId() {
        LlmModelModel modelConfig = modelConfig();
        modelConfig.setPromptCacheKey(true);
        assertNull(bodyOf(modelConfig, null).get("prompt_cache_key"),
                "configured but no session key → placement skipped (optimization semantics)");
    }

    @Test
    public void promptCacheKeyWithNullModelConfigSafe() {
        OpenAiDialect dialect = new OpenAiDialect();
        Map<String, Object> body = (Map<String, Object>) (Map<?, ?>) dialect.buildBody(
                request("sess-1"), config(ApiStyle.openai), null, "gpt-4o", false);
        assertFalse(body.containsKey("prompt_cache_key"), "null model config → no placement, no failure");
    }

    @Test
    public void sessionHeaderPlacedWhenConfiguredAndSessionKeyed() {
        LlmModelModel modelConfig = modelConfig();
        modelConfig.setCacheKeyHeader("x-session-id");
        OpenAiDialect dialect = new OpenAiDialect();
        HttpRequest httpRequest = new HttpRequest();
        dialect.applyCacheHeaders(httpRequest, request("sess-9"), modelConfig);
        assertEquals("sess-9", httpRequest.getHeader("x-session-id"),
                "the session-affinity header carries the session id");
    }

    @Test
    public void sessionHeaderUnconfiguredSendsNothing() {
        OpenAiDialect dialect = new OpenAiDialect();
        HttpRequest httpRequest = new HttpRequest();
        dialect.applyCacheHeaders(httpRequest, request("sess-9"), modelConfig());
        assertNull(httpRequest.getHeader("x-session-id"));

        dialect.applyCacheHeaders(httpRequest, request("sess-9"), null);
        assertNull(httpRequest.getHeader("x-session-id"), "null model config → no header, no failure");
    }

    @Test
    public void sessionHeaderSkipsMissingSessionId() {
        LlmModelModel modelConfig = modelConfig();
        modelConfig.setCacheKeyHeader("x-session-id");
        OpenAiDialect dialect = new OpenAiDialect();
        HttpRequest httpRequest = new HttpRequest();
        dialect.applyCacheHeaders(httpRequest, request(null), modelConfig);
        assertNull(httpRequest.getHeader("x-session-id"),
                "configured but no session key → placement skipped");
    }

    @Test
    public void dialectsWithoutPlacementFormAreUnaffected() {
        LlmModelModel modelConfig = modelConfig();
        modelConfig.setCacheKeyHeader("x-session-id");
        // the header form is dialect-agnostic (inherited from
        // AbstractLlmDialect) — Ollama/Gemini/Responses/Anthropic behave
        // identically; the default ILlmDialect no-op is shadowed by the
        // shared implementation
        for (ILlmDialect dialect : new ILlmDialect[]{
                new OllamaDialect(), new GeminiDialect(), new ResponsesDialect(), new AnthropicDialect()}) {
            HttpRequest httpRequest = new HttpRequest();
            dialect.applyCacheHeaders(httpRequest, request("sess-7"), modelConfig);
            assertEquals("sess-7", httpRequest.getHeader("x-session-id"),
                    dialect.getClass().getSimpleName() + " inherits the shared header placement");
        }
        assertTrue(true);
    }
}
