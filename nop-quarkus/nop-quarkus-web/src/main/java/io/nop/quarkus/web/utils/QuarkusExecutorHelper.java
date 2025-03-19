/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.utils;

import io.nop.api.core.auth.IUserContext;
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
                try {
                    FutureHelper.bindResult(task.get(), future);
                } finally {
                    AuthMDCHelper.unbindMDC();
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
