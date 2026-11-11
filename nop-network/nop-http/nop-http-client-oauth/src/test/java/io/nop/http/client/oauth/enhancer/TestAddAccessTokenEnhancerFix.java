package io.nop.http.client.oauth.enhancer;

import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.initialize.CoreInitialization;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.support.DefaultHttpResponse;
import io.nop.http.client.oauth.HttpClientAuthConfig;
import io.nop.http.client.oauth.HttpClientAuthConfigs;
import io.nop.http.client.oauth.OauthProviderConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAddAccessTokenEnhancerFix {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    static class StubHttpClient implements IHttpClient {
        volatile String lastTokenJson;
        final AtomicInteger fetchCount = new AtomicInteger();
        final AtomicReference<HttpRequest> lastServerEventRequest = new AtomicReference<>();
        /** 模拟 token endpoint 网络往返延迟，暴露锁内 IO 的持有时长。 */
        volatile long fetchDelayMillis;

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            fetchCount.incrementAndGet();
            long delay = fetchDelayMillis;
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            DefaultHttpResponse response = new DefaultHttpResponse();
            response.setHttpStatus(200);
            response.setBodyAsText(lastTokenJson);
            return CompletableFuture.completedFuture((IHttpResponse) response);
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                            DownloadOptions options, ICancelToken cancelToken) {
            DefaultHttpResponse response = new DefaultHttpResponse();
            response.setHttpStatus(200);
            return CompletableFuture.completedFuture((IHttpResponse) response);
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken) {
            DefaultHttpResponse response = new DefaultHttpResponse();
            response.setHttpStatus(200);
            return CompletableFuture.completedFuture((IHttpResponse) response);
        }

        @Override
        public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
            lastServerEventRequest.set(request);
            return subscriber -> {
            };
        }
    }

    static String tokenJson(String token, long expiresIn) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("access_token", token);
        map.put("token_type", "Bearer");
        map.put("expires_in", expiresIn);
        return JSON.serialize(map, false);
    }

    static AddAccessTokenHttpClientEnhancer.EnhancedClient newClient(StubHttpClient stub, long expireGap) {
        AddAccessTokenHttpClientEnhancer enhancer = new AddAccessTokenHttpClientEnhancer();
        HttpClientAuthConfigs configs = new HttpClientAuthConfigs();
        OauthProviderConfig provider = new OauthProviderConfig();
        provider.setTokenUri("http://localhost/oauth/token");
        provider.setExpireGap(expireGap);
        provider.setClientId("id");
        provider.setClientSecret("secret");
        Map<String, OauthProviderConfig> providers = new LinkedHashMap<>();
        providers.put("p1", provider);
        configs.setOauthProviders(providers);

        HttpClientAuthConfig clientConfig = new HttpClientAuthConfig();
        clientConfig.setUrlPattern("http://localhost/api/.*");
        clientConfig.setOauthProvider("p1");
        Map<String, HttpClientAuthConfig> clients = new LinkedHashMap<>();
        clients.put("c1", clientConfig);
        configs.setHttpClients(clients);

        enhancer.setAuthConfigs(configs);
        return enhancer.new EnhancedClient(stub);
    }

    @Test
    public void testFirstTokenFetchNoNpe() {
        StubHttpClient stub = new StubHttpClient();
        stub.lastTokenJson = tokenJson("token-1", 3600);
        AddAccessTokenHttpClientEnhancer.EnhancedClient client = newClient(stub, 0);

        HttpRequest request = HttpRequest.get("http://localhost/api/users");
        assertDoesNotThrow(() -> client.onRequestBegin(request, null));

        assertEquals("Bearer token-1", request.getHeader("authorization"));
    }

    @Test
    public void testExpiredTokenRefreshed() {
        StubHttpClient stub = new StubHttpClient();
        AddAccessTokenHttpClientEnhancer.EnhancedClient client = newClient(stub, 0);

        stub.lastTokenJson = tokenJson("old-token", 0);
        HttpRequest first = HttpRequest.get("http://localhost/api/a");
        assertDoesNotThrow(() -> client.onRequestBegin(first, null));
        assertEquals("Bearer old-token", first.getHeader("authorization"));

        // 旧 token 已过期（expiresIn=0），下次请求必须取到新 token 而不是复用旧值
        stub.lastTokenJson = tokenJson("new-token", 3600);
        HttpRequest second = HttpRequest.get("http://localhost/api/b");
        assertDoesNotThrow(() -> client.onRequestBegin(second, null));
        assertEquals("Bearer new-token", second.getHeader("authorization"));
    }

    /**
     * 4.3-MA-08（plan 2560 裁定 A）/C10-009② 先行失败载体：downloadAsync/uploadAsync/
     * fetchServerEventFlow 三入口必须同样注入 authorization header——缺陷形态下
     * EnhancedClient 仅 override onFetchBegin（仅 fetchAsync 经它），三入口一律 401。
     */
    /** 经真实增强客户端请求后读取请求头（download/upload 入口断言辅助）。 */
    private String capturedAuthOf(StubHttpClient stub, AddAccessTokenHttpClientEnhancer.EnhancedClient client,
                                  String entry) throws Exception {
        java.io.File targetFile = java.io.File.createTempFile("oauth-enhancer", ".tmp");
        targetFile.deleteOnExit();
        HttpRequest request = HttpRequest.get("http://localhost/api/entry-" + entry);
        if ("download".equals(entry)) {
            client.downloadAsync(request, io.nop.http.api.support.DefaultHttpOutputFile.create(targetFile), new DownloadOptions(), null)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
        } else if ("upload".equals(entry)) {
            client.uploadAsync(request, new IHttpInputFile() {
                @Override
                public java.io.File toFile() {
                    return targetFile;
                }

                @Override
                public String getName() {
                    return targetFile.getName();
                }

                @Override
                public java.io.InputStream getInputStream() {
                    try {
                        return java.nio.file.Files.newInputStream(targetFile.toPath());
                    } catch (java.io.IOException e) {
                        throw io.nop.api.core.exceptions.NopException.adapt(e);
                    }
                }

                @Override
                public long getLength() {
                    return targetFile.length();
                }
            }, new UploadOptions(), null).toCompletableFuture().get(5, TimeUnit.SECONDS);
        } else {
            client.fetchServerEventFlow(request, null).subscribe(new Flow.Subscriber<IServerEventResponse>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                public void onNext(IServerEventResponse item) {
                }

                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onComplete() {
                }
            });
        }
        return request.getHeader("authorization");
    }

    /** C10-009② 三入口逐个断言（download/upload/SSE），与 fetchAsync 合为四入口覆盖。 */
    @Test
    public void downloadUploadAndServerEventFlowCarryToken() throws Exception {
        StubHttpClient stub = new StubHttpClient();
        stub.lastTokenJson = tokenJson("token-4entry", 3600);
        AddAccessTokenHttpClientEnhancer.EnhancedClient client = newClient(stub, 0);

        assertEquals("Bearer token-4entry", capturedAuthOf(stub, client, "download"),
                "MA-08: downloadAsync must inject the authorization header (defect: bypassed token injection)");
        assertEquals("Bearer token-4entry", capturedAuthOf(stub, client, "upload"),
                "MA-08: uploadAsync must inject the authorization header (defect: bypassed token injection)");
        assertEquals("Bearer token-4entry", capturedAuthOf(stub, client, "sse"),
                "MA-08: fetchServerEventFlow must inject the authorization header (defect: bypassed token injection)");
    }

    /**
     * 4.3-MA-08（plan 2560 裁定 A）：per-provider singleflight 保持——同一 provider
     * 并发请求只允许一次 token endpoint 往返（fetch-outside-then-put 被否决的验证面），
     * 且锁内 IO 不得跨 provider 串行化（不同 provider 并发取 token 各自独立完成）。
     */
    @Test
    public void perProviderSingleflightAndCrossProviderParallelism() throws Exception {
        StubHttpClient stub = new StubHttpClient();
        stub.lastTokenJson = tokenJson("token-sf", 3600);
        stub.fetchDelayMillis = 150;
        AddAccessTokenHttpClientEnhancer.EnhancedClient client = newClient(stub, 0);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch startGate = new CountDownLatch(1);
            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        startGate.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    HttpRequest request = HttpRequest.get("http://localhost/api/x");
                    client.onRequestBegin(request, null);
                    assertEquals("Bearer token-sf", request.getHeader("authorization"));
                }));
            }
            startGate.countDown();
            for (java.util.concurrent.Future<?> f : futures)
                f.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, stub.fetchCount.get(),
                "MA-08: concurrent same-provider requests must collapse to exactly one token fetch"
                        + " (singleflight preserved under the per-provider lock)");
    }

    /**
     * 4.3-MA-15（plan 2560 裁定 H）：返回 CompletionStage 的方法不得同步抛——
     * 缺少 user context 时 onFetchBegin 的同步抛（:91）必须包络为 failed future；
     * fetchServerEventFlow 面（Flow.Publisher）以 onError 信号交付。
     */
    @Test
    public void syncThrowEnvelopedAsFailedFutureAndPublisherOnError() throws Exception {
        StubHttpClient stub = new StubHttpClient();
        AddAccessTokenHttpClientEnhancer.EnhancedClient client = newContextTokenClient(stub);

        CompletableFuture<Throwable> failure = new CompletableFuture<>();
        client.fetchAsync(HttpRequest.get("http://localhost/api/a"), null).whenComplete((r, e) -> {
            if (e != null)
                failure.complete(e);
            else
                failure.completeExceptionally(new AssertionError("expected failed future"));
        });
        assertNotNull(failure.get(5, TimeUnit.SECONDS),
                "MA-15/裁定H: fetchAsync must not synchronously throw on missing user context;"
                        + " the failure must arrive as a failed future");

        CompletableFuture<Throwable> sseFailure = new CompletableFuture<>();
        client.fetchServerEventFlow(HttpRequest.get("http://localhost/api/a"), null)
                .subscribe(new Flow.Subscriber<IServerEventResponse>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(IServerEventResponse item) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        sseFailure.complete(throwable);
                    }

                    @Override
                    public void onComplete() {
                        sseFailure.completeExceptionally(new AssertionError("expected onError"));
                    }
                });
        assertNotNull(sseFailure.get(5, TimeUnit.SECONDS),
                "MA-15/裁定H: fetchServerEventFlow sync throw must be delivered as Publisher onError");
    }

    static AddAccessTokenHttpClientEnhancer.EnhancedClient newContextTokenClient(StubHttpClient stub) {
        AddAccessTokenHttpClientEnhancer enhancer = new AddAccessTokenHttpClientEnhancer();
        HttpClientAuthConfigs configs = new HttpClientAuthConfigs();
        HttpClientAuthConfig clientConfig = new HttpClientAuthConfig();
        clientConfig.setUrlPattern("http://localhost/api/.*");
        clientConfig.setUseContextAccessToken(true);
        Map<String, HttpClientAuthConfig> clients = new LinkedHashMap<>();
        clients.put("c1", clientConfig);
        configs.setHttpClients(clients);
        enhancer.setAuthConfigs(configs);
        return enhancer.new EnhancedClient(stub);
    }
}
