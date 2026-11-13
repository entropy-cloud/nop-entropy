package io.nop.rpc.core;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.rpc.core.composite.CancellableRpcClient;
import io.nop.rpc.core.composite.PollingRpcClient;
import io.nop.rpc.core.interceptors.ClientContextRpcServiceInterceptor;
import io.nop.rpc.core.message.RpcMessageSender;
import io.nop.rpc.core.reflect.DefaultRpcMessageTransformer;
import io.nop.rpc.core.reflect.ReflectiveRpcService;
import io.nop.rpc.core.reflect.RpcInvocationHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-MA-15（plan 2560 裁定 H）：rpc 面「返回 CompletionStage 的方法不得同步抛」
 * 契约统一包络钉——各站点拒绝以 failed future 交付，调用方防御面随批。
 * <p>
 * 站点覆盖：
 * <ul>
 * <li>{@link ReflectiveRpcService} NO_HANDLER 同步抛 → failed future</li>
 * <li>{@link RpcMessageSender} INVALID_TOPIC 同步抛 → failed future</li>
 * <li>{@link ClientContextRpcServiceInterceptor} interceptAsync TIMEOUT_EXPIRED 同步抛 → failed future
 * （同步 intercept() 路径契约不变，仍同步抛）</li>
 * <li>调用方防御面：{@link CancellableRpcClient} / {@link PollingRpcClient} 首跳 /
 * {@link RpcInvocationHandler} async 分支——底层同步抛收敛为 failed future</li>
 * </ul>
 * 变异面：任一站点回退为同步抛即对应钉红。
 */
@Timeout(30)
public class TestRpcSyncThrowEnvelope_MA15 {

    interface EchoIf {
        CompletableFuture<String> echoAsync(String msg);
    }

    DefaultScheduledExecutor timer;

    @BeforeEach
    void setUp() {
        timer = DefaultScheduledExecutor.newSingleThreadTimer("test-ma15-timer");
    }

    @AfterEach
    void tearDown() {
        timer.destroy();
        io.nop.api.core.context.BaseContextProvider.clear();
    }

    static Throwable failureOf(CompletionStage<?> stage) {
        // 包络交付形态 = 异常完成的 CompletionStage（FutureHelper.reject 返回 ResolvedPromise，
        // 非 CompletableFuture 子类，断言以 CompletionStage 形态为准）
        assertTrue(stage.toCompletableFuture().isDone(), "failed-future delivery required");
        Throwable err = stage.toCompletableFuture().handle((r, e) -> e).join();
        while (err instanceof java.util.concurrent.CompletionException && err.getCause() != null)
            err = err.getCause();
        return err;
    }

    /**
     * 拒绝型 IRpcService：callAsync 同步抛（模拟 failed-future 包络未覆盖的残留站点）。
     */
    static class SyncThrowService implements IRpcService {
        @Override
        public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                         ICancelToken cancelToken) {
            throw new IllegalStateException("sync-throw-from-underlying");
        }
    }

    static ApiRequest<Object> request() {
        ApiRequest<Object> req = new ApiRequest<>();
        req.setData("payload");
        return req;
    }

    /**
     * 站点：ReflectiveRpcService NO_HANDLER → failed future（非同步抛）。
     */
    @Test
    public void testReflectiveServiceNoHandlerIsFailedFuture() {
        ReflectiveRpcService service = new ReflectiveRpcService("svc", EchoIf.class, new EchoIf() {
            @Override
            public CompletableFuture<String> echoAsync(String msg) {
                return CompletableFuture.completedFuture(msg);
            }
        }, DefaultRpcMessageTransformer.INSTANCE);

        Throwable err = failureOf(service.callAsync("noSuchMethod", request(), null));
        assertTrue(err instanceof NopException, "NO_HANDLER must be NopException, got: " + err);
        assertEquals("nop.err.rpc.no-handler", ((NopException) err).getErrorCode());
    }

    /**
     * 站点：RpcMessageSender INVALID_TOPIC → failed future（非同步抛）。
     */
    @Test
    public void testMessageSenderInvalidTopicIsFailedFuture() {
        RpcMessageSender sender = new RpcMessageSender(Map.of("known-topic", new SyncThrowService()));

        Throwable err = failureOf(sender.sendAsync("unknown-topic", request(), null));
        assertTrue(err instanceof NopException, "INVALID_TOPIC must be NopException, got: " + err);
        assertEquals("nop.err.rpc.invalid-message-topic", ((NopException) err).getErrorCode());
    }

    /**
     * 站点：ClientContextRpcServiceInterceptor.interceptAsync TIMEOUT_EXPIRED → failed future；
     * 同步 intercept() 契约不变（仍同步抛）。
     */
    @Test
    public void testInterceptorTimeoutExpiredIsFailedFutureOnAsyncPath() {
        ClientContextRpcServiceInterceptor interceptor = new ClientContextRpcServiceInterceptor();

        io.nop.api.core.context.IContext context = ContextProvider.newContext(true);
        context.setCallExpireTime(io.nop.api.core.time.CoreMetrics.currentTimeMillis() - 1000);
        try {
            ApiRequest<Object> req = request();
            Throwable err = failureOf(interceptor.interceptAsync(new io.nop.rpc.api.DefaultRpcServiceInvocation(
                    "svc", "method", req, null, false, new SyncThrowService()) {
                @Override
                public CompletionStage<ApiResponse<?>> proceedAsync() {
                    // transfer 在 proceedAsync 之前同步抛 TIMEOUT_EXPIRED，验证包络收敛
                    throw new AssertionError("must not be reached: transfer must throw first");
                }
            }));
            assertTrue(err instanceof NopException, "TIMEOUT_EXPIRED must be NopException, got: " + err);
            assertEquals("nop.err.api.timeout-expired", ((NopException) err).getErrorCode());
        } finally {
            context.close();
        }
    }

    /**
     * 防御面：CancellableRpcClient 底层同步抛 → failed future。
     */
    @Test
    public void testCancellableClientDefendsSyncThrow() {
        CancellableRpcClient client = new CancellableRpcClient(new SyncThrowService(), "cancelMethod");

        Throwable err = failureOf(client.callAsync("m", request(), null));
        assertEquals("sync-throw-from-underlying", err.getMessage());
    }

    /**
     * 防御面：PollingRpcClient 首跳同步抛 → failed future（轮询链不启动）。
     */
    @Test
    public void testPollingClientFirstHopDefendsSyncThrow() {
        PollingRpcClient client = new PollingRpcClient(new SyncThrowService(), "statusMethod", timer,
                100, 3);

        Throwable err = failureOf(client.callAsync("start", request(), null));
        assertEquals("sync-throw-from-underlying", err.getMessage());
    }

    /**
     * 防御面：RpcInvocationHandler async 分支 proceedAsync 同步抛 → 代理调用方收到
     * failed future（CompletionStage 形态交付）。
     */
    @Test
    public void testInvocationHandlerAsyncBranchDefendsSyncThrow() {
        RpcInvocationHandler handler = new RpcInvocationHandler("svc", new SyncThrowService(), null,
                DefaultRpcMessageTransformer.INSTANCE);
        EchoIf proxy = (EchoIf) Proxy.newProxyInstance(EchoIf.class.getClassLoader(),
                new Class<?>[]{EchoIf.class}, handler);

        Object ret = proxy.echoAsync("x");
        assertTrue(ret instanceof CompletionStage,
                "async proxy must deliver a CompletionStage even on sync throw, got: " + ret.getClass());
        Throwable err = failureOf((CompletionStage<?>) ret);
        assertEquals("sync-throw-from-underlying", err.getMessage());
    }

    /**
     * 同步契约非遮蔽（裁定 H 只约束异步返回形态）：intercept() 同步路径保持同步抛。
     */
    @Test
    public void testInterceptorSyncPathStillThrows() {
        ClientContextRpcServiceInterceptor interceptor = new ClientContextRpcServiceInterceptor();

        io.nop.api.core.context.IContext context = ContextProvider.newContext(true);
        context.setCallExpireTime(io.nop.api.core.time.CoreMetrics.currentTimeMillis() - 1000);
        try {
            assertThrows(NopException.class,
                    () -> interceptor.intercept(new io.nop.rpc.api.DefaultRpcServiceInvocation(
                            "svc", "method", request(), null, false, new SyncThrowService())),
                    "sync intercept() contract unchanged (MA-15 covers async return form only)");
        } finally {
            context.close();
        }
    }
}
