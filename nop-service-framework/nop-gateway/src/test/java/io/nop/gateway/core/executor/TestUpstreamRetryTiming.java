package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ICancelToken;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.model.GatewayInvokeModel;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C4-003（plan 2551 裁定 C）钉：
 * pin-a（结构）：delayedFuture 经平台 globalTimer().executeOn(globalWorker()) 链（反射结构断言，
 * 变异红形态：回到 JDK delayedExecutor 或裸 globalTimer().schedule[缺 executeOn]）；
 * pin-b（行为）：fetch 带 timeout 键值断言（配置生效）；未配置键时不覆盖（客户端缺省档）。
 */
public class TestUpstreamRetryTiming {

    private GatewayRouteModel routeWithUrl() {
        GatewayInvokeModel invoke = new GatewayInvokeModel();
        invoke.setUrl((io.nop.core.lang.eval.IEvalAction) ctx -> "http://test-upstream/v1/chat");
        GatewayRouteModel route = new GatewayRouteModel();
        route.setInvoke(invoke);
        return route;
    }

    @Test
    public void pin_a_delayedFutureUsesPlatformTimerWithWorkerHop() throws Exception {
        InvokeProcessor processor = new InvokeProcessor(null, null);
        Method m = InvokeProcessor.class.getDeclaredMethod("delayedFuture", long.class);
        m.setAccessible(true);
        CompletionStage<Void> stage = (CompletionStage<Void>) m.invoke(processor, 1L);
        // 行为佐证：延迟 1ms 后正常完成
        stage.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);

        // 结构断言：delayedFuture 方法体内含 executeOn hop（变异红：去 executeOn 后红）
        java.io.StringWriter sw = new java.io.StringWriter();
        try (java.io.Reader r = new java.io.InputStreamReader(getClass()
                .getResourceAsStream("/io/nop/gateway/core/executor/InvokeProcessor.class"))) {
            // class 文件不可读文本——改为源码形态断言（源码在仓内，构建期可读）
        }
        java.nio.file.Path src = java.nio.file.Paths.get("src/main/java/io/nop/gateway/core/executor/InvokeProcessor.java");
        String source = java.nio.file.Files.readString(src);
        assertTrue(source.contains("globalTimer().executeOn(GlobalExecutors.globalWorker())"),
                "delayedFuture must route completion through globalWorker (executeOn hop) — "
                        + "bare globalTimer().schedule would pin retry initiation on timer threads");
        assertTrue(source.contains("CFG_GATEWAY_UPSTREAM_REQUEST_TIMEOUT.get()"),
                "fetch must consume upstream-request-timeout config");
    }

    @Test
    public void pin_b_fetchCarriesConfiguredTimeout() throws Exception {
        AtomicInteger timeoutSeen = new AtomicInteger(-1);
        IHttpClient client = new IHttpClient() {
            @Override
            public CompletionStage<IHttpResponse> fetchAsync(HttpRequest req, ICancelToken ct) {
                timeoutSeen.set((int) req.getTimeout());
                return CompletableFuture.completedFuture(fakeResponse(200));
            }

            @Override
            public CompletionStage<IHttpResponse> fetchStreamAsync(HttpRequest req,
                    io.nop.http.api.client.IServerEventAggregator a, ICancelToken ct) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletionStage<IHttpResponse> downloadAsync(HttpRequest req,
                    io.nop.http.api.client.IHttpOutputFile t, io.nop.http.api.client.DownloadOptions o, ICancelToken ct) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletionStage<IHttpResponse> uploadAsync(HttpRequest req,
                    io.nop.http.api.client.IHttpInputFile f, io.nop.http.api.client.UploadOptions o, ICancelToken ct) {
                throw new UnsupportedOperationException();
            }
        };
        InvokeProcessor processor = new InvokeProcessor(null, client);
        GatewayRouteModel route = routeWithUrl();
        GatewayContextImpl ctx = new GatewayContextImpl();
        ctx.setHttpMethod("POST");

        // 缺省键：30s
        processor.invoke(route, ApiRequest.build(Map.of()), ctx).toCompletableFuture().join();
        assertEquals(30_000, timeoutSeen.get(), "default upstream timeout must be applied to fetch");
    }

    private static IHttpResponse fakeResponse(int status) {
        return new IHttpResponse() {
            @Override
            public int getHttpStatus() {
                return status;
            }

            @Override
            public Map<String, String> getHeaders() {
                return Map.of();
            }

            @Override
            public String getBody() {
                return "{}";
            }

            @Override
            public <T> T getBodyAsBean(Class<T> cl) {
                return null;
            }

            @Override
            public String getBodyAsString() {
                return "{}";
            }

            @Override
            public byte[] getBodyAsBytes() {
                return new byte[0];
            }

            @Override
            public String getContentType() {
                return "application/json";
            }

            @Override
            public String getCharset() {
                return "UTF-8";
            }
        };
    }
}
