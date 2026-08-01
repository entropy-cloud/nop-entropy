package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-08-01-1505-1 Phase 2: {@code ChatServiceImpl.buildHttpRequest} 按 api 载体
 * （{@code ChatOptions.accountKey}/{@code accountBaseUrl}）构造请求（设计 §3.6 跨层下沉）。
 *
 * <p>验证：（1）指定 accountKey 时请求用该 key（非 resolveApiKey 主账号）；
 * （2）accountBaseUrl 覆盖生效；（3）无 accountKey 时退回主账号（零回归）。
 * 这是接线验证（Rule #23）：accountKey 经 ChatOptions 跨层下沉到请求构造确实生效。
 */
public class TestChatServiceImplAccountRequest extends JunitBaseTestCase {

    private CapturingHttpClient httpClient;
    private IChatService chatService;

    @BeforeEach
    void setUp() {
        ChatServiceImpl impl = new ChatServiceImpl();
        httpClient = new CapturingHttpClient();
        impl.setHttpClient(httpClient);
        impl.setChatLogger(new DefaultChatLogger());
        chatService = impl;
    }

    @AfterEach
    void tearDown() {
        LlmConfigHelper.reset();
    }

    @Test
    void accountKeyFlowsIntoRequestHeaders() {
        // default provider (openai apiStyle): setHeaders 设 Authorization: Bearer {apiKey}。
        httpClient.queueResponse(successResponse());

        ChatOptions options = ChatOptions.builder()
                .provider("default")
                .model("gpt-4")
                .stream(false)
                .accountKey("sk-account-from-chain")
                .accountBaseUrl("https://api.example.com")
                .maxTokens(50)
                .build();
        ChatRequest req = ChatRequest.userPrompt("hi");
        req.setOptions(options);

        chatService.call(req, null);

        HttpRequest sent = httpClient.lastRequest;
        assertNotNull(sent, "a request must have been sent");
        String token = sent.getBearerToken();
        assertNotNull(token, "Bearer token must be set (openai apiStyle, apiKeyHeader null)");
        assertTrue(token.contains("sk-account-from-chain"),
                "accountKey from ChatOptions must flow into request headers (not resolveApiKey). "
                        + "Bearer token was: " + token);
    }

    @Test
    void accountBaseUrlOverridesProviderBaseUrl() {
        httpClient.queueResponse(successResponse());

        ChatOptions options = ChatOptions.builder()
                .provider("default")
                .model("gpt-4")
                .stream(false)
                .accountKey("sk-with-baseurl")
                .accountBaseUrl("https://custom-account-host.example.com")
                .maxTokens(50)
                .build();
        ChatRequest req = ChatRequest.userPrompt("hi");
        req.setOptions(options);

        chatService.call(req, null);

        HttpRequest sent = httpClient.lastRequest;
        assertNotNull(sent);
        assertTrue(sent.getUrl().contains("custom-account-host.example.com"),
                "accountBaseUrl override must flow into request URL. URL was: " + sent.getUrl());
    }

    @Test
    void noAccountKeyFallsBackToResolveApiKey() {
        // 零回归：accountKey 为 null → ChatServiceImpl 用 resolveApiKey(provider)。
        // 设主账号 config 变量，断言请求 header 带主账号 key。
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                "nop.ai.llm.default.api-key", "sk-primary-default");
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                "nop.ai.llm.default.base-url", "https://api.example.com");
        try {
            httpClient.queueResponse(successResponse());

            ChatOptions options = ChatOptions.builder()
                    .provider("default")
                    .model("gpt-4")
                    .stream(false)
                    .maxTokens(50)
                    .build();
            assertNull(options.getAccountKey(), "default accountKey must be null");
            ChatRequest req = ChatRequest.userPrompt("hi");
            req.setOptions(options);

            chatService.call(req, null);

            HttpRequest sent = httpClient.lastRequest;
            assertNotNull(sent);
            String token = sent.getBearerToken();
            assertNotNull(token);
            assertTrue(token.contains("sk-primary-default"),
                    "null accountKey → resolveApiKey(provider) = primary key. Bearer token was: " + token);
        } finally {
            io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                    "nop.ai.llm.default.api-key", null);
            io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                    "nop.ai.llm.default.base-url", null);
        }
    }

    private static IHttpResponse successResponse() {
        FakeHttpResponse r = new FakeHttpResponse();
        r.status = 200;
        r.body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],"
                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
        r.headers = new LinkedHashMap<>();
        return r;
    }

    /**
     * CapturingHttpClient：记录最后一次发送的 {@link HttpRequest}（供断言 apiKey/baseUrl）。
     */
    private static class CapturingHttpClient implements IHttpClient {
        HttpRequest lastRequest;

        void queueResponse(IHttpResponse resp) {
            // single-shot; reuse the queue pattern minimally
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            this.lastRequest = request;
            return CompletableFuture.completedFuture(successResponse());
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
