package io.nop.rpc.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.rpc.IRpcServiceInvocation;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-MA-12（plan 2560 裁定 E·验证型）：ApiContextInterceptor 同步抛保护回归钉。
 * <p>
 * 修复证据（plan 2499，commit 44e633da72 @ 2026-11-11 fresh-session 取证）：proceedAsync
 * 同步抛出时 whenComplete 不可能挂上，拦截器必须走 catch-close-rethrow 路径显式关闭
 * ContextBinder 新建的 context（ApiContextInterceptor :27-39），否则新建 context 在发起线程
 * 上滞留（C6-009 ThreadLocal 滞留子面移交 CL-025 平台轮，不在本批）。
 * <p>
 * 本钉为验证型载体：修复已落，钉住「同步抛 → binder closed + 异常原样传播」不被未来
 * 重构回退（变异面：摘除 catch 块中的 binder.close() 即红）。
 */
public class TestApiContextInterceptorSyncThrow {

    private Object savedProvider;

    private void swapProvider(Object provider) throws Exception {
        java.lang.reflect.Field f = ContextProvider.class.getDeclaredField("_instance");
        f.setAccessible(true);
        f.set(null, provider);
    }

    @BeforeEach
    public void saveProviderAndClear() throws Exception {
        java.lang.reflect.Field f = ContextProvider.class.getDeclaredField("_instance");
        f.setAccessible(true);
        savedProvider = f.get(null);
        io.nop.api.core.context.BaseContextProvider.clear();
    }

    @AfterEach
    public void restoreProviderAndClear() throws Exception {
        swapProvider(savedProvider);
        io.nop.api.core.context.BaseContextProvider.clear();
    }

    private static ApiRequest<Object> requestWithTenant(String tenant) {
        ApiRequest<Object> req = new ApiRequest<>();
        req.setData(Map.of("k", "v"));
        req.setHeader("nop-tenant", tenant);
        return req;
    }

    private IRpcServiceInvocation invocation(ApiRequest<?> request, CompletionStage<ApiResponse<?>> stage) {
        return new IRpcServiceInvocation() {
            @Override
            public String getServiceName() {
                return "svc";
            }

            @Override
            public String getServiceMethod() {
                return "method";
            }

            @Override
            public ApiRequest<?> getRequest() {
                return request;
            }

            @Override
            public ICancelToken getCancelToken() {
                return null;
            }

            @Override
            public CompletionStage<ApiResponse<?>> proceedAsync() {
                if (stage != null)
                    return stage;
                throw new IllegalStateException("sync-throw-from-proceed");
            }

            @Override
            public boolean isInbound() {
                return true;
            }
        };
    }

    /**
     * 回归钉（MA-12 裁定 E）：proceedAsync 同步抛出 → 异常原样传播 + binder 新建的
     * context 已被关闭（catch-close-rethrow 路径，plan 2499 commit 44e633da72）。
     */
    @Test
    public void testSyncThrowClosesBinderContextAndPropagates() {
        ApiContextInterceptor interceptor = new ApiContextInterceptor();

        IllegalStateException err = assertThrows(IllegalStateException.class,
                () -> interceptor.interceptAsync(invocation(requestWithTenant("tenant-ma12"), null)),
                "sync exception from proceedAsync must propagate unchanged");

        assertEquals("sync-throw-from-proceed", err.getMessage());

        // binder 新建的 context 已随同步抛路径关闭：closed 残留对平台读点不可观测
        assertNull(ContextProvider.currentContext(),
                "binder-created context must be closed on the sync-throw path (44e633da72)");
    }

    /**
     * 配对面零回退：正常异步完成路径 binder.close 经 whenComplete 关闭自建 context。
     */
    @Test
    public void testAsyncCompletionStillClosesCreatedContext() throws Exception {
        ApiContextInterceptor interceptor = new ApiContextInterceptor();

        CompletionStage<ApiResponse<?>> result = interceptor.interceptAsync(
                invocation(requestWithTenant("tenant-ma12-async"),
                        CompletableFuture.completedFuture(ApiResponse.success("ok"))));

        assertEquals("ok", result.toCompletableFuture().get(2, TimeUnit.SECONDS).getData());
        assertNull(ContextProvider.currentContext(),
                "created context must be closed after async completion");
    }

    /**
     * binder 建制证据（2499 取证登记）：ContextBinder(ApiRequest) 确实新建并初始化 context
     * （created=true），同步抛保护关闭的正是这个 context。
     */
    @Test
    public void testBinderCreatedContextIsInitializedFromRequest() {
        ContextBinder binder = new ContextBinder(requestWithTenant("tenant-ma12-evidence"));
        try {
            IContext bound = ContextProvider.currentContext();
            assertNotNull(bound, "binder must create and attach a context");
            assertEquals("tenant-ma12-evidence", bound.getTenantId(),
                    "request headers must land on the created context");
            assertTrue(!bound.isClosed(), "context must be open while request is in flight");
        } finally {
            binder.close();
        }
        assertNull(ContextProvider.currentContext());
    }
}
