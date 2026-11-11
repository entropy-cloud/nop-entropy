/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.utils;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.context.IContext;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.utils.AuthMDCHelper;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.quarkus.web.filter.VertxHttpServerContext;
import io.quarkus.runtime.BlockingOperationControl;
import io.quarkus.runtime.ExecutorRecorder;
import io.vertx.ext.web.RoutingContext;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static io.nop.quarkus.web.QuarkusWebConstants.KEY_NOP_HTTP_SERVER_CONTEXT;

public class QuarkusExecutorHelper {
    static final ThreadLocal<VertxHttpServerContext> g_serverContext = new ThreadLocal<>();

    public static IHttpServerContext getHttpServerContext() {
        return g_serverContext.get();
    }

    public static VertxHttpServerContext makeServerContext(RoutingContext routingContext) {
        VertxHttpServerContext ctx = routingContext.get(KEY_NOP_HTTP_SERVER_CONTEXT);
        if (ctx == null) {
            ctx = new VertxHttpServerContext(routingContext);
            routingContext.put(KEY_NOP_HTTP_SERVER_CONTEXT, ctx);
        }
        return ctx;
    }

    public static <T> CompletionStage<T> withRoutingContext(RoutingContext routingContext, Supplier<CompletionStage<T>> task) {
        VertxHttpServerContext ctx = makeServerContext(routingContext);
        g_serverContext.set(ctx);
        try {
            IContext context = ctx.getContext();
            if (context == null)
                return task.get();

            CompletableFuture<T> future = new CompletableFuture<>();
            context.runOnContext(() -> {
                IUserContext userContext = IUserContext.get();
                if (userContext != null) {
                    AuthMDCHelper.bindMDC(userContext);
                }
                // 4.4-MA-05（plan 2562 裁定 C）：context 线程上重设 g_serverContext TL——
                // 任务（及 JsonRpcWebSocketEndpoint.extractInitialHeaders 这类消费方）经
                // getHttpServerContext 读 IP/headers。set 必须与 finally remove 配对，否则
                // event-loop 线程持有上一请求的 VertxHttpServerContext 跨请求泄漏（安全相关）。
                g_serverContext.set(ctx);
                try {
                    // C6-007（plan 2524，future 族第 5 例宿主侧，R21 族纪律）：task.get()
                    // 先求值入局部变量 + bindResult 移入 try + catch Throwable 终结返回 future
                    // ——task.get() 同步异常原样逃逸 lambda 后落入平台 flushTasks 吞异常通道
                    //（ContextTaskQueue :157-159 登记设计语义），调用方永久悬挂
                    //（4.4-MA-05：2524 MA-02 语义冻结段零回退——仅外扩 TL set/remove 配对）
                    CompletionStage<T> stage;
                    try {
                        stage = task.get();
                        FutureHelper.bindResult(stage, future);
                    } catch (Throwable e) {
                        future.completeExceptionally(NopException.adapt(e));
                    }
                } finally {
                    AuthMDCHelper.unbindMDC();
                    // 4.4-MA-05（plan 2562 裁定 C）：context 线程 TL 配对 remove——任务完成后
                    // 该线程不得残留本请求的 server context
                    g_serverContext.remove();
                }
            });
            return future;
        } finally {
            g_serverContext.remove();
        }
    }

    public static CompletionStage<Object> executeBlocking(Callable<?> task) {
        // 如果已经在工作线程上
        if (BlockingOperationControl.isBlockingAllowed()) {
            return FutureHelper.futureCall(task);
        }

        // 如果当前在IO线程上，则调度到工作线程池上再执行
        CompletableFuture<Object> future = new CompletableFuture<>();
        ExecutorRecorder.getCurrent().execute(() -> {
            CompletionStage<Object> promise = FutureHelper.futureCall(task);
            FutureHelper.bindResult(promise, future);
        });
        return future;
    }
}
