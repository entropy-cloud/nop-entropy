package io.nop.ai.core.service;

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.embedding.EmbeddingOptions;
import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.model.LlmModel;
import io.nop.api.core.config.AppConfig;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.core.NopAiCoreErrors.ARG_LLM_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_EMBEDDING_ACCOUNT_CHAIN_EXHAUSTED;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_EMBEDDING_NO_EMBED_URL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_EMBEDDING_NO_PROVIDER;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_EMBEDDING_UNSUPPORTED_API_STYLE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RATE_LIMITED;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * K1（plan knowledge-rag/01）Phase 2：{@code EmbeddingServiceImpl} 的 OpenAI 兼容
 * 协议、路由、限流、重试、账号链 failover 行为验证（stub IHttpClient，wire-level
 * 断言请求 URL/body/headers——Rule #23 接线验证）。
 *
 * <p>重试延迟经 {@link DirectEmbeddingService}（同线程立即执行）消除真实等待。
 */
public class TestEmbeddingServiceImpl extends JunitBaseTestCase {

    private static final String PROVIDER = "test-embed";

    private StubHttpClient httpClient;
    private DirectEmbeddingService service;

    @BeforeEach
    void setUp() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm." + PROVIDER + ".api-key",
                "sk-embed-primary");
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm." + PROVIDER + ".base-url",
                "https://primary-embed.example.com/v1");
        httpClient = new StubHttpClient();
        service = new DirectEmbeddingService(httpClient);
    }

    @AfterEach
    void tearDown() {
        LlmConfigHelper.reset();
    }

    private EmbeddingOptions options() {
        EmbeddingOptions opts = new EmbeddingOptions();
        opts.setProvider(PROVIDER);
        return opts;
    }

    @Test
    void singleEmbedSuccessCarriesVectorAndModelMetadata() {
        httpClient.queue(200, "{\"data\":[{\"index\":0,\"embedding\":[0.25,0.5,0.75]}],"
                + "\"model\":\"text-embed-test\",\"usage\":{\"prompt_tokens\":3,\"total_tokens\":3}}");

        VectorData vd = service.embed(AiDocument.fromText("hello"), options());

        assertNotNull(vd);
        assertEquals(3, vd.getVector().length);
        assertEquals(0.25, vd.getVector()[0], 1e-9);
        assertEquals(0.75, vd.getVector()[2], 1e-9);
        assertEquals("text-embed-test", vd.getMetadata("model"));
        assertEquals(3, vd.getMetadata("dimension"));
        Object usage = vd.getMetadata("usage");
        assertTrue(usage instanceof Map, "usage must be carried as metadata map");
        assertEquals(3, ((Map<?, ?>) usage).get("total_tokens"));

        HttpRequest sent = httpClient.requests.get(0);
        assertTrue(sent.getUrl().startsWith("https://primary-embed.example.com/v1/embeddings"),
                "request must hit baseUrl + embedUrl: " + sent.getUrl());
        assertEquals("POST", sent.getMethod());
        String body = sent.getBody().toString();
        assertTrue(body.contains("\"model\":\"text-embed-test\""), "body must carry model: " + body);
        assertTrue(body.contains("\"input\":[\"hello\"]"), "body must carry input: " + body);
        String token = sent.getBearerToken();
        assertNotNull(token);
        assertTrue(token.contains("sk-embed-primary"), "primary apiKey must flow into headers");
    }

    @Test
    void batchEmbedRestoresIndexOrderFromResponse() {
        httpClient.queue(200, "{\"data\":["
                + "{\"index\":1,\"embedding\":[2.0,2.0]},"
                + "{\"index\":0,\"embedding\":[1.0,1.0]}]}");

        List<VectorData> result = service.embedAll(
                List.of(AiDocument.fromText("a"), AiDocument.fromText("b")), options());

        assertEquals(2, result.size());
        assertEquals(1.0, result.get(0).getVector()[0], 1e-9);
        assertEquals(2.0, result.get(1).getVector()[0], 1e-9);
    }

    @Test
    void emptyBatchSkipsNetwork() {
        List<VectorData> result = service.embedAll(List.of(), options());
        assertTrue(result.isEmpty());
        assertEquals(0, httpClient.requests.size());
    }

    @Test
    void missingEmbedUrlFailsLoud() {
        // test-accounts.llm.xml 无 <embedUrl>
        EmbeddingOptions opts = new EmbeddingOptions();
        opts.setProvider("test-accounts");

        NopException error = assertThrows(NopException.class,
                () -> service.embed(AiDocument.fromText("x"), opts));
        assertEquals(ERR_AI_EMBEDDING_NO_EMBED_URL.getErrorCode(), error.getErrorCode());
    }

    @Test
    void noProviderFailsLoud() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.embedding.default-llm", null);

        NopException error = assertThrows(NopException.class,
                () -> service.embed(AiDocument.fromText("x"), new EmbeddingOptions()));
        assertEquals(ERR_AI_EMBEDDING_NO_PROVIDER.getErrorCode(), error.getErrorCode());
    }

    @Test
    void defaultProviderConfigResolvesWhenOptionsProviderAbsent() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.embedding.default-llm", PROVIDER);
        httpClient.queue(200, "{\"data\":[{\"index\":0,\"embedding\":[1.5]}]}");

        VectorData vd = service.embed(AiDocument.fromText("x"), new EmbeddingOptions());
        assertEquals(1.5, vd.getVector()[0], 1e-9);
    }

    @Test
    void unsupportedApiStyleFailsLoud() {
        EmbeddingOptions opts = new EmbeddingOptions();
        opts.setProvider("test-embed-badstyle");

        NopException error = assertThrows(NopException.class,
                () -> service.embed(AiDocument.fromText("x"), opts));
        assertEquals(ERR_AI_EMBEDDING_UNSUPPORTED_API_STYLE.getErrorCode(), error.getErrorCode());
    }

    @Test
    void rateLimitRejectionFailsFastWith429Semantics() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.service.rate-limit-acquire-timeout",
                "0");
        // 加载的 LlmModel 已冻结；限流语义验证用独立实例（与 chat 侧 checkRateLimit 同款验证形态）
        LlmModel config = new LlmModel();
        config.setRateLimit(1e-9);

        service.checkRateLimit(PROVIDER, config);
        NopException error = assertThrows(NopException.class,
                () -> service.checkRateLimit(PROVIDER, config));
        assertEquals(ERR_AI_RATE_LIMITED.getErrorCode(), error.getErrorCode());
        assertEquals(429, error.getParam("httpStatus"));
    }

    @Test
    void serverErrorRetriesThenSucceeds() {
        httpClient.queue(503, "{\"error\":{\"message\":\"upstream unavailable\"}}");
        httpClient.queue(200, "{\"data\":[{\"index\":0,\"embedding\":[0.5]}]}");

        VectorData vd = service.embed(AiDocument.fromText("x"), options());

        assertNotNull(vd);
        assertEquals(2, httpClient.requests.size(), "503 TRANSIENT must be retried once");
    }

    @Test
    void unauthorizedFallsBackToAccountChain() {
        httpClient.queue(401, "{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"bad key\"}}");
        httpClient.queue(200, "{\"data\":[{\"index\":0,\"embedding\":[0.5]}]}");

        VectorData vd = service.embed(AiDocument.fromText("x"), options());

        assertNotNull(vd);
        assertEquals(2, httpClient.requests.size(),
                "401 → AUTH_INVALID (errorMappings) must switch to the backup account");
        HttpRequest second = httpClient.requests.get(1);
        assertTrue(second.getUrl().contains("backup-embed.example.com"),
                "backup account baseUrl override must apply: " + second.getUrl());
        String token = second.getBearerToken();
        assertNotNull(token);
        assertTrue(token.contains("sk-embed-backup-1"), "backup apiKey must apply");
    }

    @Test
    void accountChainExhaustedFailsLoud() {
        httpClient.queue(401, "{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"bad key\"}}");
        httpClient.queue(401, "{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"bad key\"}}");

        NopException error = assertThrows(NopException.class,
                () -> service.embed(AiDocument.fromText("x"), options()));
        assertEquals(ERR_AI_EMBEDDING_ACCOUNT_CHAIN_EXHAUSTED.getErrorCode(), error.getErrorCode());
        assertEquals(2, httpClient.requests.size(),
                "primary + 1 backup account, no silent retry beyond the chain");
    }

    @Test
    void nonTransientStopsImmediately() {
        httpClient.queue(400, "{\"error\":{\"code\":\"invalid_request\",\"message\":\"bad input\"}}");

        NopException error = assertThrows(NopException.class,
                () -> service.embed(AiDocument.fromText("x"), options()));
        assertEquals(ERR_AI_SERVICE_HTTP_ERROR.getErrorCode(), error.getErrorCode());
        assertEquals(1, httpClient.requests.size(), "NON_TRANSIENT must not retry");
    }

    /**
     * 直接在同线程执行重试的测试变体：消除退避等待，保留完整重试/failover 语义。
     */
    static class DirectEmbeddingService extends EmbeddingServiceImpl {
        DirectEmbeddingService(IHttpClient httpClient) {
            setHttpClient(httpClient);
        }

        @Override
        protected void scheduleRetry(long delayMs, Runnable task) {
            task.run();
        }
    }

    /**
     * 请求捕获桩：按入队顺序返回预设响应，并记录每个已发送请求供 wire-level 断言
     * （与 TestChatServiceImplAccountRequest.CapturingHttpClient 同款范式）。
     */
    private static class StubHttpClient implements IHttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        private final List<IHttpResponse> queued = new ArrayList<>();

        void queue(int status, String body) {
            queued.add(new FakeHttpResponse(status, body));
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            requests.add(request);
            if (queued.isEmpty()) {
                throw new AssertionError("no queued response for request: " + request.getUrl());
            }
            return CompletableFuture.completedFuture(queued.remove(0));
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
        final int status;
        final String body;

        FakeHttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
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
            return Map.of();
        }
    }
}
