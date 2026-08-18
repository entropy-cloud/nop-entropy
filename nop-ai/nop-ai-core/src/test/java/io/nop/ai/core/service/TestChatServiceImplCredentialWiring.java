package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.credential.IAiModelCredentialResolver;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W7-successor（plan 2026-08-13-1118-3 Phase 3）：{@link ChatServiceImpl} credentialId 接线 + 优先级链
 * 端到端验证（Rule #22/#23：从用户入口到 LLM 调用出口的完整路径）。
 *
 * <p>注入 fake {@link IAiModelCredentialResolver}（无需 DB，resolver impl 行为由 nop-ai-service
 * 的 {@code TestAiModelCredentialResolver} 覆盖），经 FakeHttpClient 捕获 HTTP 请求，断言：
 * <ul>
 *   <li><b>resolution</b>：resolver 返回 credential apiKey → dialect header 注入该 key（接线 Rule #23）</li>
 *   <li><b>priority</b>（D2）：accountKey > credentialId > resolveApiKey —— accountKey 非空时覆盖 credential</li>
 *   <li><b>fallback</b>：resolver 返回 null（未配 credentialId / 身份不对应）→ 回退 resolveApiKey（零回归）</li>
 *   <li><b>fail-closed</b>（D5）：resolver 抛 NopException（凭证失效）→ 调用中止，异常传播</li>
 * </ul>
 *
 * <p>「.llm.xml↔NopAiModel 身份对应前提」（F1，防假绿）：本测试用 {@code default} provider（存在于
 * default.llm.xml），resolver fake 模拟「NopAiModel 行存在且 credentialId 解析成功」的对应场景，
 * 以及「resolver 返回 null」（身份不对应/未配）的回退场景。
 */
public class TestChatServiceImplCredentialWiring extends JunitBaseTestCase {

    static final ErrorCode ERR_TEST_CRED_NOT_FOUND =
            ErrorCode.define("ERR_TEST_CRED_NOT_FOUND", "credential not found", "credentialId");

    private FakeHttpClient httpClient;
    private ChatServiceImpl chatService;
    private FakeCredentialResolver resolver;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl();
        httpClient = new FakeHttpClient();
        chatService.setHttpClient(httpClient);
        chatService.setChatLogger(new DefaultChatLogger());
        resolver = new FakeCredentialResolver();
        chatService.setCredentialResolver(resolver);
    }

    @AfterEach
    void tearDown() {
        LlmConfigHelper.reset();
    }

    @Test
    void credentialApiKeyIsInjectedIntoRequestHeader() {
        httpClient.setResponse(ok("{}")); // empty success body
        resolver.apiKey = "sk-from-credential-lib";

        ChatRequest req = ChatRequest.userPrompt("hi");
        req.setOptions(ChatOptions.builder()
                .provider("default")
                .model("gpt-4")
                .stream(false)
                .accountBaseUrl("https://api.example.com")
                .build());

        chatService.call(req, null);

        assertEquals("sk-from-credential-lib", httpClient.lastRequest.getBearerToken(),
                "configured credential apiKey must be injected into the request Authorization header (wiring Rule #23)");
        assertEquals("default", resolver.lastProvider, "resolver must be consulted with the request provider");
        assertEquals("gpt-4", resolver.lastModel, "resolver must be consulted with the resolved model name");
    }

    @Test
    void accountKeyOverridesCredentialId() {
        // priority chain D2: accountKey > credentialId
        httpClient.setResponse(ok("{}"));
        resolver.apiKey = "sk-credential";

        ChatRequest req = ChatRequest.userPrompt("hi");
        req.setOptions(ChatOptions.builder()
                .provider("default")
                .model("gpt-4")
                .stream(false)
                .accountKey("sk-account-fallback")
                .accountBaseUrl("https://api.example.com")
                .build());

        chatService.call(req, null);

        assertEquals("sk-account-fallback", httpClient.lastRequest.getBearerToken(),
                "accountKey (coordinator FALLBACK) must win over credentialId (priority chain D2)");
    }

    /**
     * D6-04（A1-audit successor，2026-08-17）：空白 accountKey（纯空格）不是有效账号 key——
     * 必须回退解析链（credentialId → config-var），不得把空白当"非空"注入凭证头。
     */
    @Test
    void blankAccountKeyFallsThroughToCredentialChain() {
        httpClient.setResponse(ok("{}"));
        resolver.apiKey = "sk-from-credential-lib";

        ChatRequest req = ChatRequest.userPrompt("hi");
        req.setOptions(ChatOptions.builder()
                .provider("default")
                .model("gpt-4")
                .stream(false)
                .accountKey("   ") // 纯空白：非显式纠正账号
                .accountBaseUrl("https://api.example.com")
                .build());

        chatService.call(req, null);

        assertEquals("sk-from-credential-lib", httpClient.lastRequest.getBearerToken(),
                "blank accountKey must fall through to the credential chain (D6-04 isBlank)");
        assertEquals("default", resolver.lastProvider, "resolver must be consulted when accountKey is blank");
    }

    @Test
    void fallsBackToConfigVarWhenCredentialReturnsNull() {
        // resolver returns null => fallback to resolveApiKey(config-var/secret). With no config/secret set,
        // apiKey is empty => bearer token absent. The call still proceeds (no exception) => zero regression.
        httpClient.setResponse(ok("{}"));
        resolver.apiKey = null; // simulate: no credentialId / identity mismatch

        ChatRequest req = ChatRequest.userPrompt("hi");
        req.setOptions(ChatOptions.builder()
                .provider("default")
                .model("gpt-4")
                .stream(false)
                .accountBaseUrl("https://api.example.com")
                .build());

        ChatResponse response = chatService.call(req, null);

        assertTrue(response.isSuccess(), "null credential resolution must fall back without error (zero regression)");
        // resolver was consulted (proving the hook is wired) but returned null
        assertEquals("default", resolver.lastProvider);
    }

    @Test
    void failClosedWhenResolverThrows() {
        // resolver throws (credentialId set but credential missing/soft-deleted/decrypt-failed) => D5 fail-closed
        resolver.throwing = new NopException(ERR_TEST_CRED_NOT_FOUND).param("credentialId", "cred-x");

        ChatRequest req = ChatRequest.userPrompt("hi");
        req.setOptions(ChatOptions.builder()
                .provider("default")
                .model("gpt-4")
                .stream(false)
                .accountBaseUrl("https://api.example.com")
                .build());

        NopException ex = assertThrows(NopException.class, () -> chatService.call(req, null));
        assertEquals("ERR_TEST_CRED_NOT_FOUND", ex.getErrorCode(),
                "resolver fail-closed exception must propagate and abort the call (D5, not swallowed)");
    }

    @Test
    void noResolverWiredFallsBackToConfigVar() {
        // deployment without nop-credential: resolver not wired (null) => resolveApiKey path, zero regression
        chatService.setCredentialResolver(null);
        httpClient.setResponse(ok("{}"));

        ChatRequest req = ChatRequest.userPrompt("hi");
        req.setOptions(ChatOptions.builder()
                .provider("default")
                .model("gpt-4")
                .stream(false)
                .accountBaseUrl("https://api.example.com")
                .build());

        ChatResponse response = chatService.call(req, null);
        assertTrue(response.isSuccess(), "no resolver wired => config-var/secret path, zero regression");
    }

    private static FakeHttpResponse ok(String body) {
        FakeHttpResponse r = new FakeHttpResponse();
        r.status = 200;
        r.body = body;
        r.headers = new LinkedHashMap<>();
        return r;
    }

    private static class FakeCredentialResolver implements IAiModelCredentialResolver {
        String apiKey;
        String lastProvider;
        String lastModel;
        RuntimeException throwing;

        @Override
        public String resolveApiKeyByCredential(String provider, String model) {
            this.lastProvider = provider;
            this.lastModel = model;
            if (throwing != null) {
                throw throwing;
            }
            return apiKey;
        }
    }

    private static class FakeHttpClient implements IHttpClient {
        IHttpResponse response;
        HttpRequest lastRequest;

        void setResponse(IHttpResponse response) {
            this.response = response;
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            this.lastRequest = request;
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                            DownloadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeHttpResponse implements IHttpResponse {
        int status;
        String body;
        Map<String, String> headers;

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
            return body != null ? body.getBytes() : new byte[0];
        }

        @Override
        public String getBodyAsString() {
            return body;
        }

        @Override
        public <T> T getBodyAsBean(Class<T> beanClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getBody() {
            return body;
        }

        @Override
        public Map<String, String> getHeaders() {
            return headers;
        }
    }
}
