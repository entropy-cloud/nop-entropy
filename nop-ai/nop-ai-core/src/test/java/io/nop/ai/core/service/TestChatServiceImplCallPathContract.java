package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.ICancelToken;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.client.UploadOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-ROUND4-CALL-PATH Phase 3 回归：{@code ChatServiceImpl.callAsync} 对
 * {@code ChatRequest.options == null} 按文档化契约 null-safe（不再 NPE）。
 * <p>
 * null options 走默认 stream=true 路径（等价于 options.stream == null 的既有行为），
 * 经公开入口 {@code callAsync} → {@code aggregateStreamToResponse} → {@code callStream}
 * → mock fetchServerEventFlow（空事件流）完整链路完成，断言不 NPE 且响应返回。
 */
public class TestChatServiceImplCallPathContract extends JunitBaseTestCase {

    private EmptyFlowHttpClient httpClient;
    private IChatService chatService;

    @BeforeEach
    void setUp() {
        ChatServiceImpl impl = new ChatServiceImpl();
        httpClient = new EmptyFlowHttpClient();
        impl.setHttpClient(httpClient);
        impl.setChatLogger(new DefaultChatLogger());
        chatService = impl;
    }

    @AfterEach
    void tearDown() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.service.default-llm", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.base-url", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.api-key", null);
        LlmConfigHelper.reset();
    }

    @Test
    void callAsyncWithNullOptionsDoesNotNpe() {
        // 默认 provider = test-sat（有 defaultModel），null options 走默认 stream=true 流式路径。
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.service.default-llm", "test-sat");
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.base-url", "https://api.example.com");
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.api-key", "sk-test");

        ChatRequest request = ChatRequest.userPrompt("hi");
        assertTrue(request.getOptions() == null, "ChatRequest(messages) leaves options null by contract");

        ChatResponse response = chatService.call(request, null);

        assertNotNull(response, "callAsync with null options must complete without NPE");
        assertTrue(response.isSuccess());
        assertNotNull(response.getRequestId(), "streamed response must carry the request id");
    }

    /**
     * 空事件流 IHttpClient：{@code fetchServerEventFlow} 立即 onComplete（无任何 SSE 事件），
     * 供 null options 默认流式路径的汇聚收敛。非流式/下载入口未使用。
     */
    private static class EmptyFlowHttpClient implements IHttpClient {
        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
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

        @Override
        public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                }
            });
        }
    }
}