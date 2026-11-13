package io.nop.rpc.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.BaseContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.context.IContextProvider;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 4.3-C3-007（plan 2499 Phase 3 子分片 B，裁定③落地分工 nop-rpc-api 侧）：ContextBinder
 * 对 isClosed 残留强制替换 + ApiContextInterceptor 同步抛保护（C3-006 面随单）。
 * <p>
 * 缺陷：ContextBinder 构造器命中残留 context 时不复初始化（created=false）——若 provider
 * 的 currentContext() 返回已关闭残留（非 BaseContextProvider 的可插拔实现，或平台根因守卫
 * 未覆盖的附着路径），请求头 tenant/locale 永远不落盘，且跨请求复用死 context。
 * <p>
 * 判别口径：注入一个 currentContext() 原样返回 ThreadLocal 值的 provider（缺省
 * BaseContextProvider 的读点守卫会惰性清除 closed 残留，须可控 provider 才能复现该缺陷形态）
 * ——修复前 binder 复用残留（created=false、头部不落盘，红）；修复后强制替换为新建 context
 * （头部落盘、open 状态，绿）。
 */
public class TestContextBinderClosedResidue_C3_007_43 {

    /**
     * 可控 provider：currentContext() 原样返回 ThreadLocal 值（不做 isClosed 惰性清除），
     * 复现「读点返回已关闭残留」的缺陷形态。
     */
    static class RawProvider implements IContextProvider {
        final ThreadLocal<IContext> holder = new ThreadLocal<>();

        @Override
        public IContext currentContext() {
            return holder.get();
        }

        @Override
        public void detachContext() {
            holder.remove();
        }

        @Override
        public IContext getOrCreateContext() {
            IContext context = holder.get();
            if (context == null || context.isClosed()) {
                return newContext(true);
            }
            return context;
        }

        @Override
        public IContext newContext(boolean attach) {
            IContext context = new BaseContext();
            if (attach)
                holder.set(context);
            return context;
        }

        @Override
        public void attachContext(IContext context) {
            holder.set(context);
        }
    }

    private Object savedProvider;

    private void swapProvider(IContextProvider provider) throws Exception {
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
        swapProvider((IContextProvider) savedProvider);
        io.nop.api.core.context.BaseContextProvider.clear();
    }

    private static ApiRequest<Object> requestWithTenant(String tenant) {
        ApiRequest<Object> req = new ApiRequest<>();
        req.setData(Map.of("k", "v"));
        req.setHeader("nop-tenant", tenant);
        return req;
    }

    /**
     * 判别用例（红→绿）：currentContext() 返回已关闭残留时，ContextBinder 强制替换为新建
     * context 并落盘请求头（created=true）；修复前复用残留（created=false、头部不落盘）。
     */
    @Test
    public void testClosedResidueIsReplacedAndInitializedFromRequest() throws Exception {
        swapProvider(new RawProvider());

        // 造一个已关闭残留并经 RawProvider 原样暴露（绕过 BaseContextProvider 的读点守卫）
        IContext residue = ContextProvider.newContext(true);
        residue.close();
        assertTrue(residue.isClosed());
        assertSame(residue, ContextProvider.currentContext(),
                "raw provider must expose the closed residue for this defect form");

        ContextBinder binder = new ContextBinder(requestWithTenant("tenant-43"));
        try {
            IContext bound = ContextProvider.currentContext();
            assertNotNull(bound, "binder must provide a usable context");
            assertFalse(bound.isClosed(), "closed residue must not be reused");
            assertNotSame(residue, bound, "closed residue must be replaced, not reused");
            assertEquals("tenant-43", bound.getTenantId(),
                    "request headers must land on the replacement context (created=true path)");
        } finally {
            binder.close();
        }
    }

    /**
     * open context 复用语义零回退：既有 open 附着不被二次建 context 分叉、不复初始化
     * （ContextBinder 既有契约，与裁定③复用分支一致）。
     */
    @Test
    public void testOpenContextStillReusedWithoutReinit() {
        IContext existing = ContextProvider.newContext(true);
        existing.setTenantId("tenant-keep");
        try {
            ContextBinder binder = new ContextBinder(requestWithTenant("tenant-should-not-overwrite"));
            try {
                assertSame(existing, ContextProvider.currentContext(),
                        "open context must be reused (no second-context fork)");
                assertEquals("tenant-keep", existing.getTenantId(),
                        "open context must not be re-initialized from request headers");
            } finally {
                binder.close();
            }
            assertFalse(existing.isClosed(), "binder must not close a context it did not create");
        } finally {
            existing.close();
        }
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
     * ApiContextInterceptor 同步抛保护（C3-006 面）：proceedAsync 同步抛出时 binder 关闭，
     * 新建 context 不在线程上保持附着；异常原样传播（rule 24 失败可见）。
     */
    @Test
    public void testInterceptorSyncThrowClosesBinderAndPropagates() {
        ApiContextInterceptor interceptor = new ApiContextInterceptor();

        assertThrows(IllegalStateException.class,
                () -> interceptor.interceptAsync(invocation(requestWithTenant("tenant-interceptor"), null)),
                "sync exception must propagate");

        // binder 新建的 context 已随同步抛路径关闭：closed 残留对平台读点不可观测
        assertNull(ContextProvider.currentContext(),
                "binder-created context must be closed on the sync-throw path");
    }

    /**
     * 正常路径消费验证（裁定③发起线程残留面消费验证）：异步完成后 binder.close 关闭自建
     * context，发起线程经 isClosed 读点守卫读到 null（closed 残留不可观测）。
     */
    @Test
    public void testInterceptorAsyncCompletionClosesCreatedContext() throws Exception {
        ApiContextInterceptor interceptor = new ApiContextInterceptor();

        CompletionStage<ApiResponse<?>> result = interceptor.interceptAsync(
                invocation(requestWithTenant("tenant-async-43"),
                        CompletableFuture.completedFuture(ApiResponse.success("ok"))));

        assertEquals("ok", result.toCompletableFuture().get(2, TimeUnit.SECONDS).getData());

        assertNull(ContextProvider.currentContext(),
                "created context must be closed after async completion and unobservable");
    }
}
