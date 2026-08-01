package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan W2e-1/W2e-2 (Phase 2) focused test for the config-driven error-response
 * normalization ({@link ILlmDialect#parseErrorResponse}). Verifies:
 * <ul>
 *   <li>OpenAI 429 双分类：{@code insufficient_quota} → QUOTA_EXCEEDED，
 *       {@code rate_limit_exceeded} → RATE_LIMITED（first-match-wins 顺序）。</li>
 *   <li>401 默认启发式 → NON_TRANSIENT（零回归红线：不是 AUTH_INVALID）。</li>
 *   <li>未配置 {@code <errorMappings>} 的 provider 走默认启发式（零回归）。</li>
 *   <li>Retry-After 头归一为 retryAfterMs（毫秒）。</li>
 * </ul>
 */
public class TestLlmDialectErrorResponse extends JunitBaseTestCase {

    @AfterEach
    public void tearDown() {
        LlmConfigHelper.reset();
    }

    @Test
    void openAiInsufficientQuotaClassifiedAsQuotaExceeded() {
        ILlmDialect dialect = LlmDialectFactory.getDialect(
                LlmConfigHelper.loadConfig("deepseek").getApiStyle());
        LlmModel config = LlmConfigHelper.loadConfig("deepseek");

        String body = "{\"error\":{\"code\":\"insufficient_quota\","
                + "\"type\":\"requests\",\"message\":\"You exceeded your current quota\"}}";

        ChatResponse resp = dialect.parseErrorResponse(body, 429, null, config);

        assertFalse(resp.isSuccess(), "non-200 must produce a non-success ChatResponse");
        assertEquals(ErrorClassification.QUOTA_EXCEEDED, resp.getErrorClassification(),
                "insufficient_quota must classify as QUOTA_EXCEEDED (first-match-wins over generic 429)");
        assertEquals(429, resp.getHttpStatus());
    }

    @Test
    void openAiRateLimitExceededClassifiedAsRateLimited() {
        ILlmDialect dialect = LlmDialectFactory.getDialect(
                LlmConfigHelper.loadConfig("deepseek").getApiStyle());
        LlmModel config = LlmConfigHelper.loadConfig("deepseek");

        String body = "{\"error\":{\"code\":\"rate_limit_exceeded\","
                + "\"type\":\"requests\",\"message\":\"Too many requests\"}}";

        ChatResponse resp = dialect.parseErrorResponse(body, 429, null, config);
        assertEquals(ErrorClassification.RATE_LIMITED, resp.getErrorClassification(),
                "rate_limit_exceeded must classify as RATE_LIMITED");
    }

    @Test
    void authInvalidReachableViaConfiguredMapping() {
        // 设计 §6.1：AUTH_INVALID 只能经显式 <errorMappings> 到达。deepseek (extends default)
        // 配置了 openai-auth-invalid (httpStatus=401,403 + errorCodes=invalid_api_key,...)，
        // 故 401 + invalid_api_key → AUTH_INVALID（这是配置化行为，而非默认启发式）。
        ILlmDialect dialect = LlmDialectFactory.getDialect(
                LlmConfigHelper.loadConfig("deepseek").getApiStyle());
        LlmModel config = LlmConfigHelper.loadConfig("deepseek");

        String body = "{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"Unauthorized\"}}";
        ChatResponse resp = dialect.parseErrorResponse(body, 401, null, config);
        assertEquals(ErrorClassification.AUTH_INVALID, resp.getErrorClassification(),
                "401 + invalid_api_key matches configured openai-auth-invalid → AUTH_INVALID");
    }

    @Test
    void authErrorWithoutMappingFallsBackToNonTransient() {
        // 零回归红线：默认启发式把 401/403 映射为 NON_TRANSIENT（不是 AUTH_INVALID）。
        // 用空配置验证启发式（未配置 errorMappings 时 401 → NON_TRANSIENT）。
        ILlmDialect dialect = LlmDialectFactory.getDialect(
                LlmConfigHelper.loadConfig("deepseek").getApiStyle());
        LlmModel emptyConfig = new LlmModel();

        ChatResponse resp = dialect.parseErrorResponse("{}", 401, null, emptyConfig);
        assertEquals(ErrorClassification.NON_TRANSIENT, resp.getErrorClassification(),
                "unconfigured 401 → NON_TRANSIENT (zero-regression heuristic, not AUTH_INVALID)");
    }

    @Test
    void serverErrorClassifiedAsTransient() {
        ILlmDialect dialect = LlmDialectFactory.getDialect(
                LlmConfigHelper.loadConfig("deepseek").getApiStyle());
        LlmModel config = LlmConfigHelper.loadConfig("deepseek");

        ChatResponse resp = dialect.parseErrorResponse("{\"error\":{\"type\":\"server_error\"}}",
                503, null, config);
        assertEquals(ErrorClassification.TRANSIENT, resp.getErrorClassification(),
                "5xx must classify as TRANSIENT");
    }

    @Test
    void unconfiguredProviderUsesHeuristic() {
        // 构造一个没有任何 errorMappings 的 LlmModel，验证默认启发式（零回归红线）。
        ILlmDialect dialect = LlmDialectFactory.getDialect(
                LlmConfigHelper.loadConfig("deepseek").getApiStyle());
        LlmModel emptyConfig = new LlmModel();

        assertEquals(ErrorClassification.RATE_LIMITED,
                dialect.parseErrorResponse("{}", 429, null, emptyConfig).getErrorClassification(),
                "unconfigured 429 → RATE_LIMITED (zero-regression)");
        assertEquals(ErrorClassification.TRANSIENT,
                dialect.parseErrorResponse("{}", 500, null, emptyConfig).getErrorClassification(),
                "unconfigured 5xx → TRANSIENT (zero-regression)");
        assertEquals(ErrorClassification.NON_TRANSIENT,
                dialect.parseErrorResponse("{}", 400, null, emptyConfig).getErrorClassification(),
                "unconfigured 4xx → NON_TRANSIENT (zero-regression)");
    }

    @Test
    void retryAfterHeaderNormalizedToMillis() {
        ILlmDialect dialect = LlmDialectFactory.getDialect(
                LlmConfigHelper.loadConfig("deepseek").getApiStyle());
        LlmModel config = LlmConfigHelper.loadConfig("deepseek");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Retry-After", "5");

        ChatResponse resp = dialect.parseErrorResponse(
                "{\"error\":{\"code\":\"rate_limit_exceeded\"}}", 429, headers, config);
        assertEquals(ErrorClassification.RATE_LIMITED, resp.getErrorClassification());
        assertNotNull(resp.getRetryAfterMs(), "Retry-After header must be normalized to retryAfterMs");
        assertEquals(5000L, resp.getRetryAfterMs(), "Retry-After: 5 (seconds) → 5000 ms");
    }

    @Test
    void retryAfterMsHeaderTakesPrecedence() {
        ILlmDialect dialect = LlmDialectFactory.getDialect(
                LlmConfigHelper.loadConfig("deepseek").getApiStyle());
        LlmModel config = LlmConfigHelper.loadConfig("deepseek");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("retry-after-ms", "1500");
        headers.put("Retry-After", "5");

        ChatResponse resp = dialect.parseErrorResponse(
                "{\"error\":{\"code\":\"rate_limit_exceeded\"}}", 429, headers, config);
        assertEquals(1500L, resp.getRetryAfterMs(),
                "retry-after-ms (millis) must take precedence over retry-after (seconds)");
    }

    @Test
    void noRetryAfterYieldsNull() {
        ILlmDialect dialect = LlmDialectFactory.getDialect(
                LlmConfigHelper.loadConfig("deepseek").getApiStyle());
        LlmModel config = LlmConfigHelper.loadConfig("deepseek");

        ChatResponse resp = dialect.parseErrorResponse(
                "{\"error\":{\"code\":\"insufficient_quota\"}}", 429, null, config);
        assertEquals(ErrorClassification.QUOTA_EXCEEDED, resp.getErrorClassification());
        assertNull(resp.getRetryAfterMs(),
                "no Retry-After hint → null (policy falls back to full-jitter)");
    }

    @Test
    void nonJsonBodyDoesNotCrash() {
        ILlmDialect dialect = LlmDialectFactory.getDialect(
                LlmConfigHelper.loadConfig("deepseek").getApiStyle());
        LlmModel config = LlmConfigHelper.loadConfig("deepseek");

        ChatResponse resp = dialect.parseErrorResponse("Gateway Timeout", 504, null, config);
        assertEquals(ErrorClassification.TRANSIENT, resp.getErrorClassification(),
                "non-JSON body must not crash; heuristic still applies");
        assertFalse(resp.isSuccess());
    }
}
