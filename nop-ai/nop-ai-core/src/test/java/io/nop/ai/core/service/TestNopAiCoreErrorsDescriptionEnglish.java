package io.nop.ai.core.service;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.model.LlmModel;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.core.NopAiCoreErrors.ARG_HTTP_STATUS;
import static io.nop.ai.core.NopAiCoreErrors.ARG_LLM_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_COMMAND_NOT_FOUND;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_EMPTY_TOOLS_NODE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_FILE_CONTENT_NO_PATH;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_FILE_PATH_IS_EMPTY;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_INVALID_EXPR_VAR_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_INVALID_RESPONSE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_MANDATORY_INPUT_IS_EMPTY;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_MANDATORY_OUTPUT_IS_EMPTY;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_NO_VAR_IN_SCOPE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_PROMPT_EMPTY_EXPR;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_PROMPT_UNCLOSED_EXPR;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_PROMPT_USE_UNDEFINED_VAR;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RATE_LIMITED;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RESULT_INVALID_END_LINE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RESULT_INVALID_NUMBER;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RESULT_IS_EMPTY;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RESULT_NO_EXPECTED_PART;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_NO_BASE_URL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_NO_DEFAULT_LLMS;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_OPTION_NOT_SET;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_INVALID_THOUGHT;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_NODE_PARSE_FAILED;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_UNKNOWN_PROMPT_EXPR_PREFIX;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_UNKNOWN_TOOL_CALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-4 description Englishization regression guard (plan
 * 2026-09-15-1231-2 Phase 1): the 25 {@code NopAiCoreErrors} codes whose
 * Chinese descriptions were translated to English. Pins three contracts so a
 * future drift is caught at test time:
 *
 * <ul>
 *   <li><b>ID stability</b> — each code keeps its {@code nop.err.ai.*} id
 *       string (persisted/logged id, renaming is a behavior change).</li>
 *   <li><b>English description</b> — no CJK codepoints and the semantics
 *       keyword is present (not a blanked/emptied description).</li>
 *   <li><b>Parameter contract</b> — every {@code {param}} placeholder that
 *       the throw sites attach is still present in the template.</li>
 * </ul>
 *
 * <p>End-to-end (real production throw sites, not test stubs):
 * {@link ChatServiceImpl#checkRateLimit} → {@link ERR_AI_RATE_LIMITED}, and
 * {@link DefaultAiChatService#sendChatAsync} (non-200 HTTP response) →
 * {@link ERR_AI_SERVICE_HTTP_ERROR}.
 */
public class TestNopAiCoreErrorsDescriptionEnglish {

    private record CodeSpec(ErrorCode code, String expectedId, String... placeholders) {}

    private static final List<CodeSpec> ROUND4_CODES = List.of(
            new CodeSpec(ERR_AI_SERVICE_NO_DEFAULT_LLMS, "nop.err.ai.service.no-default-llms"),
            new CodeSpec(ERR_AI_SERVICE_NO_BASE_URL, "nop.err.ai.service.no-base-url", "{llmName}"),
            new CodeSpec(ERR_AI_SERVICE_OPTION_NOT_SET, "nop.err.ai.service.option-not-set", "{llmName}", "{optionName}"),
            new CodeSpec(ERR_AI_SERVICE_HTTP_ERROR, "nop.err.ai.service.http-error", "{llmName}", "{httpStatus}"),
            new CodeSpec(ERR_AI_RATE_LIMITED, "nop.err.ai.service.rate-limited", "{llmName}"),
            new CodeSpec(ERR_AI_RESULT_IS_EMPTY, "nop.err.ai.service.result-is-empty"),
            new CodeSpec(ERR_AI_RESULT_INVALID_END_LINE, "nop.err.ai.service.result-invalid-end-line"),
            new CodeSpec(ERR_AI_RESULT_NO_EXPECTED_PART, "nop.err.ai.service.result-no-expected-part", "{expected}"),
            new CodeSpec(ERR_AI_RESULT_INVALID_NUMBER, "nop.err.ai.service.result-invalid-number",
                    "{name}", "{value}"),
            new CodeSpec(ERR_AI_TOOLS_INVALID_THOUGHT, "nop.err.ai.tools.invalid-thought", "{value}"),
            new CodeSpec(ERR_AI_INVALID_RESPONSE, "nop.err.ai.service.invalid-response"),
            new CodeSpec(ERR_AI_MANDATORY_INPUT_IS_EMPTY, "nop.err.ai.mandatory-input-is-empty", "{inputName}"),
            new CodeSpec(ERR_AI_MANDATORY_OUTPUT_IS_EMPTY, "nop.err.ai.mandatory-output-is-empty", "{outputName}"),
            new CodeSpec(ERR_AI_PROMPT_USE_UNDEFINED_VAR, "nop.err.ai.prompt-var-not-defined", "{varName}"),
            new CodeSpec(ERR_AI_UNKNOWN_PROMPT_EXPR_PREFIX, "nop.err.ai.prompt-expr-prefix-unknown", "{prefix}"),
            new CodeSpec(ERR_AI_NO_VAR_IN_SCOPE, "nop.err.ai.no-var-in-scope", "{varName}"),
            new CodeSpec(ERR_AI_PROMPT_UNCLOSED_EXPR, "nop.err.ai.prompt-unclosed-expr"),
            new CodeSpec(ERR_AI_PROMPT_EMPTY_EXPR, "nop.err.ai.prompt-empty-expr"),
            new CodeSpec(ERR_AI_INVALID_EXPR_VAR_NAME, "nop.err.ai.invalid-expr-var-name", "{varName}"),
            new CodeSpec(ERR_AI_UNKNOWN_TOOL_CALL, "nop.err.ai.unknown-tool-call", "{toolName}"),
            new CodeSpec(ERR_AI_FILE_CONTENT_NO_PATH, "nop.err.ai.file-content.no-path"),
            new CodeSpec(ERR_AI_COMMAND_NOT_FOUND, "nop.err.ai.command.not-found", "{command}"),
            new CodeSpec(ERR_AI_EMPTY_TOOLS_NODE, "nop.err.ai.command.empty-tools-node"),
            new CodeSpec(ERR_AI_TOOLS_NODE_PARSE_FAILED, "nop.err.ai.command.tools-node-parse-failed"),
            new CodeSpec(ERR_AI_FILE_PATH_IS_EMPTY, "nop.err.ai.command.file-path-empty")
    );

    @Test
    public void testRound4CodesDescriptionIsEnglishAndContractStable() {
        assertEquals(25, ROUND4_CODES.size(),
                "round-4 scope must stay 25 codes (drift from the registered count is caught)");
        for (CodeSpec spec : ROUND4_CODES) {
            ErrorCode code = spec.code();
            assertEquals(spec.expectedId(), code.getErrorCode(),
                    "error-code id must stay stable for " + spec.expectedId());
            String description = code.getDescription();
            assertNotNull(description, "description must not be null for " + spec.expectedId());
            assertTrue(!description.isBlank(), "description must not be blank for " + spec.expectedId());
            assertNoCjk(description, spec.expectedId());
            for (String placeholder : spec.placeholders()) {
                assertTrue(description.contains(placeholder),
                        "description of " + spec.expectedId() + " must keep placeholder " + placeholder
                                + ", actual=" + description);
            }
        }
    }

    // ========================================================================
    // ERR_AI_RATE_LIMITED — real throw site ChatServiceImpl.checkRateLimit
    // ========================================================================

    private static final String TEST_PROVIDER = "test-http-provider";

    static final class FakeRateLimiter implements IRateLimiter {
        private int granted;
        private final int maxPermits;

        FakeRateLimiter(int maxPermits) {
            this.maxPermits = maxPermits;
        }

        @Override
        public boolean tryAcquire(int permits, long timeout) {
            if (granted < maxPermits) {
                granted++;
                return true;
            }
            return false;
        }

        @Override
        public double getPermitsPerSecond() {
            return 0;
        }

        @Override
        public long getAcquireSuccessCount() {
            return granted;
        }

        @Override
        public long getAcquireFailCount() {
            return 0;
        }

        @Override
        public void resetStats() {
            granted = 0;
        }
    }

    static final class TestableChatService extends ChatServiceImpl {
        private final IRateLimiter limiter;

        TestableChatService(IRateLimiter limiter) {
            this.limiter = limiter;
        }

        @Override
        protected IRateLimiter createRateLimiter(double rate) {
            return limiter;
        }
    }

    @Test
    public void testRateLimitedThrowSiteCarriesEnglishDescription() {
        TestableChatService service = new TestableChatService(new FakeRateLimiter(1));
        LlmModel config = new LlmModel();
        config.setRateLimit(1.0);

        service.checkRateLimit(TEST_PROVIDER, config);

        NopException ex = assertThrows(NopException.class,
                () -> service.checkRateLimit(TEST_PROVIDER, config),
                "quota-exhausted second call must fail fast with ERR_AI_RATE_LIMITED");
        assertEquals(ERR_AI_RATE_LIMITED.getErrorCode(), ex.getErrorCode(),
                "throw site must keep pointing at ERR_AI_RATE_LIMITED");
        assertEquals("nop.err.ai.service.rate-limited", ex.getErrorCode(),
                "error-code id must stay stable");
        assertEquals(TEST_PROVIDER, ex.getParam(ARG_LLM_NAME),
                "llmName must be attached as a param");
        assertEquals(429, ex.getParam(ARG_HTTP_STATUS),
                "httpStatus=429 must be attached (LlmErrorClassifier RATE_LIMITED contract)");
        String description = ex.getDescription();
        assertNoCjk(description, "nop.err.ai.service.rate-limited");
        assertTrue(description.contains("rate-limited"),
                "throw-site description must be English, actual=" + description);
    }

    // ========================================================================
    // ERR_AI_SERVICE_HTTP_ERROR — real throw site DefaultAiChatService
    // ========================================================================

    static final class TestableDefaultAiChatService extends DefaultAiChatService {
        TestableDefaultAiChatService() {
            super();
        }

        @Override
        protected LlmModel loadLlmModel(String llmName) {
            LlmModel model = new LlmModel();
            model.setBaseUrl("http://localhost:8080");
            model.setChatUrl("/v1/chat/completions");
            return model;
        }

        @Override
        protected String getApiKey(String llmName) {
            return "";
        }
    }

    @Test
    public void testHttpErrorThrowSiteCarriesEnglishDescription() {
        TestableDefaultAiChatService service = new TestableDefaultAiChatService();
        service.setHttpClient(new Non200HttpClient());

        AiChatOptions options = new AiChatOptions();
        options.setProvider(TEST_PROVIDER);
        options.setModel("test-model");

        NopException ex = awaitNopException(service, options);
        assertEquals(ERR_AI_SERVICE_HTTP_ERROR.getErrorCode(), ex.getErrorCode(),
                "non-200 response must fail fast with ERR_AI_SERVICE_HTTP_ERROR");
        assertEquals("nop.err.ai.service.http-error", ex.getErrorCode(),
                "error-code id must stay stable");
        assertEquals(TEST_PROVIDER, ex.getParam(ARG_LLM_NAME),
                "llmName must be attached as a param");
        assertEquals(500, ex.getParam(ARG_HTTP_STATUS),
                "httpStatus must be attached as a param");
        String description = ex.getDescription();
        assertNoCjk(description, "nop.err.ai.service.http-error");
        assertTrue(description.contains("HTTP status"),
                "throw-site description must be English, actual=" + description);
    }

    private static NopException awaitNopException(DefaultAiChatService service, AiChatOptions options) {
        CompletableFuture<?> future = service.sendChatAsync(Prompt.userText("hi"), options, null)
                .toCompletableFuture();
        java.util.concurrent.CompletionException ce = assertThrows(
                java.util.concurrent.CompletionException.class, future::join,
                "sendChatAsync must complete exceptionally with the HTTP error");
        assertTrue(ce.getCause() instanceof NopException,
                "cause must be NopException, actual=" + ce.getCause());
        return (NopException) ce.getCause();
    }

    /** Minimal {@link IHttpClient} that always returns an HTTP 500 response. */
    private static final class Non200HttpClient implements IHttpClient {
        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(new FakeHttpResponse(500));
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                            DownloadOptions options, io.nop.api.core.util.ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, io.nop.api.core.util.ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeHttpResponse implements IHttpResponse {
        private final int status;

        FakeHttpResponse(int status) {
            this.status = status;
        }

        @Override
        public int getHttpStatus() {
            return status;
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public String getCharset() {
            return "UTF-8";
        }

        @Override
        public byte[] getBodyAsBytes() {
            return new byte[0];
        }

        @Override
        public String getBodyAsString() {
            return "";
        }

        @Override
        public <T> T getBodyAsBean(Class<T> beanClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getBody() {
            return null;
        }

        @Override
        public Map<String, String> getHeaders() {
            return Map.of();
        }
    }

    private static void assertNoCjk(String text, String errorId) {
        assertTrue(text.codePoints().noneMatch(TestNopAiCoreErrorsDescriptionEnglish::isCjk),
                "description of " + errorId + " must not contain CJK characters, actual=" + text);
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF);
    }
}