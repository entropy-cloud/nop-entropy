package io.nop.http.server.filters;

import io.nop.core.initialize.CoreInitialization;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.server.IHttpServerFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class CoreInitializationGuardFilter implements IHttpServerFilter {
    static final Logger LOG = LoggerFactory.getLogger(CoreInitializationGuardFilter.class);

    @Override
    public int order() {
        return HIGH_PRIORITY;
    }

    @Override
    public CompletionStage<Void> filterAsync(IHttpServerContext context, Supplier<CompletionStage<Void>> next) {
        // 检查服务是否正在初始化。如果是，则拒绝请求并返回503错误。
        boolean suspended = CoreInitialization.isSuspended();
        if (suspended || CoreInitialization.isInitializerRunning()) {
            LOG.warn("nop.http.server.initializing: Server is {}. Rejecting request for url={}",
                    suspended ? "suspended" : "initializing", context.getRequestUrl());
            // 设置响应状态码为 503 Service Unavailable
            context.sendResponse(503, "Service is " + (suspended ? "suspended" : "initializing"));
            // 直接返回一个已完成的 CompletableFuture，终止过滤器链的后续执行
            return CompletableFuture.completedFuture(null);
        }
        return next.get();
    }
}
