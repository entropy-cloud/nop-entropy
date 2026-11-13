package io.nop.quarkus.web.utils;

import io.nop.api.core.context.IContext;
import io.nop.quarkus.web.filter.VertxHttpServerContext;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 4.4-MA-05（plan 2562 裁定 C）载体：runOnContext 回调内 g_serverContext TL 的 set/remove
 * 配对（承载 C10-00x 传播面）。
 *
 * <p>pin-a（传播）：context 线程执行任务期间 g_serverContext 可达（任务经
 * {@link QuarkusExecutorHelper#getHttpServerContext()} 读 IP/headers ——
 * JsonRpcWebSocketEndpoint.extractInitialHeaders 消费面）。
 * pin-b（配对 remove）：任务完成后同一 context 线程上 TL 必须为 null——缺配对 remove 会使
 * event-loop 线程持上一请求的 VertxHttpServerContext 跨请求泄漏（安全相关）。
 * pin-c（2524 MA-02 语义冻结零回退）：task.get() 同步异常仍终结返回 future
 *（completeExceptionally），且异常路径同样完成 TL 配对 remove。
 *
 * <p>实现注记：IContext stub 的 runOnContext 以同步内联执行模拟 context 派发（与 Vertx
 * event-loop 一样在「context 线程」上运行任务体），使 TL 断言在同线程上确定性可判——
 * 真实 Vertx 派发下缺陷态跨线程不可判别（2522 结构钉先例）。
 */
@Timeout(60)
public class TestQuarkusExecutorHelperPropagation {

    /**
     * 返回按类型缺省的动态 stub：boolean→false、数值→0、其余→null；
     * runOnContext/execute 以内联执行模拟 context 派发。
     */
    private static IContext inlineDispatchContext() {
        return (IContext) Proxy.newProxyInstance(IContext.class.getClassLoader(),
                new Class<?>[]{IContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "runOnContext":
                        case "execute":
                            ((Runnable) args[0]).run();
                            return null;
                        case "toString":
                            return "inline-dispatch-stub-context";
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) return false;
                            if (rt == long.class) return 0L;
                            if (rt == int.class) return 0;
                            return null;
                    }
                });
    }

    /** RoutingContext stub：仅 get/put 有状态（makeServerContext 需要），其余缺省 null。 */
    private static RoutingContext mapBackedRoutingContext() {
        Map<String, Object> props = new HashMap<>();
        return (RoutingContext) Proxy.newProxyInstance(RoutingContext.class.getClassLoader(),
                new Class<?>[]{RoutingContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "get":
                            return props.get(args[0]);
                        case "put":
                            return props.put((String) args[0], args[1]);
                        case "toString":
                            return "stub-routing-context";
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) return false;
                            if (rt == int.class) return 0;
                            return null;
                    }
                });
    }

    @Test
    public void serverContextReachableDuringTaskAndNullAfterOnContextThread() throws Exception {
        RoutingContext routingContext = mapBackedRoutingContext();
        VertxHttpServerContext ctx = new VertxHttpServerContext(routingContext);
        ctx.setContext(inlineDispatchContext());

        // 捕获任务执行期间的 TL 可达性（pin-a）
        AtomicReference<Object> duringTask = new AtomicReference<>();

        CompletionStage<String> stage = QuarkusExecutorHelper.withRoutingContext(routingContext, () -> {
            duringTask.set(QuarkusExecutorHelper.getHttpServerContext());
            return CompletableFuture.completedFuture("ok");
        });

        assertEquals("ok", stage.toCompletableFuture().get());

        assertNotNull(duringTask.get(),
                "pin-a: g_serverContext must be reachable inside the runOnContext task"
                        + " (consumers read IP/headers via getHttpServerContext)");
        // pin-b：任务完成后 context 线程上 TL 必须为 null（配对 remove）
        assertNull(QuarkusExecutorHelper.getHttpServerContext(),
                "pin-b: after the task completes, the context thread must not retain the"
                        + " previous request's server context (cross-request bleed = red here)");
    }

    @Test
    public void taskThrowableStillTerminatesFutureAndPairsTlRemove() throws Exception {
        RoutingContext routingContext = mapBackedRoutingContext();
        VertxHttpServerContext ctx = new VertxHttpServerContext(routingContext);
        ctx.setContext(inlineDispatchContext());

        AtomicReference<Boolean> tlDuringTask = new AtomicReference<>();
        CompletableFuture<String> failing = new CompletableFuture<>();
        failing.completeExceptionally(new IllegalStateException("task-boom"));

        CompletionStage<String> stage = QuarkusExecutorHelper.withRoutingContext(routingContext, () -> {
            tlDuringTask.set(QuarkusExecutorHelper.getHttpServerContext() != null);
            return failing;
        });

        try {
            stage.toCompletableFuture().get();
            fail("task failure must terminate the returned future exceptionally (2524 MA-02 frozen)");
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException,
                    "cause must be the adapted task failure");
        }
        assertTrue(Boolean.TRUE.equals(tlDuringTask.get()),
                "pin-a must hold on the exception path too");
        assertNull(QuarkusExecutorHelper.getHttpServerContext(),
                "pin-c: exception path must also pair the TL remove (2524 MA-02 semantics zero-regression)");
    }
}
