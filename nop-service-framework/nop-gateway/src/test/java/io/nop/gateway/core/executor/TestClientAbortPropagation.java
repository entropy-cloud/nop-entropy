package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ICancelToken;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.model.GatewayInvokeModel;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.server.IHttpServerContext;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C3-010（plan 2550 裁定 E）钉：CL-025 断连传播。
 * pin11-a：转发前已断连 → invoke 以 CancellationException 失败，零上游 fetch。
 * pin11-b：首次 5xx 后断连 → 不进入重试（恰 1 次 fetch；旧形态重试到底，红）。
 * pin11-c：未断连基线 → 5xx 后正常重试（2 次 fetch 后成功），不回退 2508 重试语义。
 * pin11-d：CL-025 default 语义（不可观测档）——default isClientAborted()=false、
 * onClientAbort 无操作，纯接口实现零破坏。
 */
public class TestClientAbortPropagation {

    private GatewayRouteModel routeWithUrl() {
        GatewayInvokeModel invoke = new GatewayInvokeModel();
        invoke.setUrl((io.nop.core.lang.eval.IEvalAction) ctx -> "http://test-upstream/v1/chat");
        GatewayRouteModel route = new GatewayRouteModel();
        route.setInvoke(invoke);
        return route;
    }

    /** 动态代理 IHttpServerContext：仅 isClientAborted 可控，default 方法走接口缺省实现 */
    private static IHttpServerContext abortableContext(AtomicBoolean aborted) {
        return (IHttpServerContext) Proxy.newProxyInstance(
                IHttpServerContext.class.getClassLoader(),
                new Class<?>[]{IHttpServerContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isClientAborted")) {
                        return aborted.get();
                    }
                    if (method.isDefault()) {
                        // 调用接口 default 实现（CL-025 语义钉的核心：default 档零破坏）
                        Class<?> declaring = method.getDeclaringClass();
                        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaring, MethodHandles.lookup());
                        return lookup.findSpecial(declaring, method.getName(),
                                        MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
                                        declaring)
                                .bindTo(proxy)
                                .invokeWithArguments(args);
                    }
                    return null;
                });
    }

    private IHttpClient countingClient(AtomicInteger calls, AtomicInteger statusAfterFirst) {
        return new IHttpClient() {
            @Override
            public CompletionStage<IHttpResponse> fetchAsync(HttpRequest req, ICancelToken ct) {
                int n = calls.incrementAndGet();
                int status = n == 1 ? 500 : statusAfterFirst.get();
                return CompletableFuture.completedFuture(new IHttpResponse() {
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
                        return "upstream";
                    }

                    @Override
                    public <T> T getBodyAsBean(Class<T> cl) {
                        return null;
                    }

                    @Override
                    public String getBodyAsString() {
                        return "upstream";
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
                });
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
    }

    private GatewayContextImpl contextWith(AtomicBoolean aborted) {
        GatewayContextImpl ctx = new GatewayContextImpl();
        ctx.setHttpMethod("POST");
        ctx.setHttpServerContext(abortableContext(aborted));
        return ctx;
    }

    @Test
    public void pin11a_abortedBeforeForwardSkipsUpstream() {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean aborted = new AtomicBoolean(true); // 转发前已断连
        InvokeProcessor processor = new InvokeProcessor(null, countingClient(calls, new AtomicInteger(200)));
        GatewayRouteModel route = routeWithUrl();
        GatewayContextImpl ctx = contextWith(aborted);

        // failedFuture(CancellationException)：join 以 CancellationException 形态抛出
        //（消息为 join 自身的「join」包装——本钉断言类型 + 零上游 fetch）
        assertThrows(CancellationException.class,
                () -> processor.invoke(route, ApiRequest.build(Map.of()), ctx).toCompletableFuture().join());
        assertEquals(0, calls.get(), "no upstream fetch after abort");
    }

    @Test
    public void pin11b_abortAfterFirst5xxStopsRetry() {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean aborted = new AtomicBoolean(false);
        IHttpClient client = new IHttpClient() {
            @Override
            public CompletionStage<IHttpResponse> fetchAsync(HttpRequest req, ICancelToken ct) {
                int n = calls.incrementAndGet();
                if (n == 1) {
                    // 首次 500 响应，同时客户端断连（重试分支触发前 aborted 已置位）
                    aborted.set(true);
                    return CompletableFuture.completedFuture(fakeResponse(500));
                }
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
        // 首响应 500 进入重试分支——断连检查在延迟调度前拦截
        InvokeProcessor processor = new InvokeProcessor(null, client);
        GatewayRouteModel route = routeWithUrl();
        GatewayContextImpl ctx = contextWith(aborted);

        // 重试分支以 failedFuture 收敛：join 包 CompletionException（cause = CancellationException）
        CompletionException ex = assertThrows(CompletionException.class,
                () -> processor.invoke(route, ApiRequest.build(Map.of()), ctx).toCompletableFuture().join());
        assertTrue(ex.getCause() instanceof CancellationException,
                "retry must not fire after client abort: got " + ex.getCause());
        assertEquals(1, calls.get(), "retry must not fire after client abort (legacy form retries to exhaustion)");
    }

    @Test
    public void pin11c_notAbortedRetriesNormally() {
        AtomicInteger calls = new AtomicInteger();
        InvokeProcessor processor = new InvokeProcessor(null,
                countingClient(calls, new AtomicInteger(200)));
        GatewayRouteModel route = routeWithUrl();
        GatewayContextImpl ctx = contextWith(new AtomicBoolean(false));

        io.nop.api.core.beans.ApiResponse<?> resp = processor
                .invoke(route, ApiRequest.build(Map.of()), ctx)
                .toCompletableFuture().join();
        assertEquals(200, resp.getHttpStatus());
        assertEquals(2, calls.get(), "first 500 then retry succeeds (no regression of retry semantics)");
    }

    @Test
    public void pin11d_cl025DefaultSemanticsZeroBreakage() throws Throwable {
        AtomicBoolean abortCallbackRun = new AtomicBoolean(false);
        IHttpServerContext ctx = abortableContext(new AtomicBoolean(false));
        // default isClientAborted 档：未被覆写的适配器保持 false
        IHttpServerContext bare = (IHttpServerContext) Proxy.newProxyInstance(
                IHttpServerContext.class.getClassLoader(),
                new Class<?>[]{IHttpServerContext.class},
                (proxy, method, args) -> {
                    if (method.isDefault()) {
                        Class<?> declaring = method.getDeclaringClass();
                        MethodHandles.Lookup lookup =
                                MethodHandles.privateLookupIn(declaring, MethodHandles.lookup());
                        return lookup.findSpecial(declaring, method.getName(),
                                        MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
                                        declaring)
                                .bindTo(proxy)
                                .invokeWithArguments(args);
                    }
                    return null;
                });
        assertFalse(bare.isClientAborted(), "default isClientAborted must be false (observable-off tier)");
        bare.onClientAbort(() -> abortCallbackRun.set(true));
        assertFalse(abortCallbackRun.get(), "default onClientAbort must be a no-op");
        assertNotNull(ctx); // 可控实现代理形态可用
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
                return "ok";
            }

            @Override
            public <T> T getBodyAsBean(Class<T> cl) {
                return null;
            }

            @Override
            public String getBodyAsString() {
                return "ok";
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
