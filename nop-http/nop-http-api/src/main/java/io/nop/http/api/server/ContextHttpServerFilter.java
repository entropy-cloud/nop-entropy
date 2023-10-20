/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.api.server;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ApiStringHelper;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static io.nop.api.core.ApiConfigs.CFG_RPC_PROPAGATE_HEADERS;

public class ContextHttpServerFilter implements IHttpServerFilter {

    @Override
    public int order() {
        return HIGH_PRIORITY;
    }

    @Override
    public CompletionStage<Void> filterAsync(IHttpServerContext context, Supplier<CompletionStage<Void>> next) {
        IContext ctx = ContextProvider.newContext();
        initContext(ctx, context);

        MDC.put(ApiConstants.MDC_NOP_TRACE, ctx.getTraceId());
        try {
            return next.get().whenComplete((r, e) -> {
                ctx.close();
            });
        } finally {
            MDC.remove(ApiConstants.MDC_NOP_TRACE);
        }
    }

    protected void initContext(IContext ctx, IHttpServerContext context) {
        String traceId = context.getRequestStringHeader(ApiConstants.MDC_NOP_TRACE);
        String tenantId = context.getRequestStringHeader(ApiConstants.HEADER_TENANT);

        String timezone = context.getRequestStringHeader(ApiConstants.HEADER_TIMEZONE);
        String locale = context.getRequestStringHeader(ApiConstants.HEADER_LOCALE);

        // 前台主动要求限制服务超时时间
        long expireTime = context.getRequestLongHeader(ApiConstants.HEADER_TIMEOUT, -1L);

        ctx.setTimezone(timezone);
        ctx.setLocale(locale);
        ctx.setTenantId(tenantId);

        ctx.setPropagateRpcHeaders(getPropagateHeaders(context));

        if (ApiStringHelper.isEmpty(traceId)) {
            traceId = UUID.randomUUID().toString();
            ctx.setTraceId(traceId);
        }

        if (expireTime > 0)
            ctx.setCallExpireTime(expireTime);

    }

    protected Map<String, Object> getPropagateHeaders(IHttpServerContext context) {
        Collection<String> headers = ConvertHelper.toCsvSet(CFG_RPC_PROPAGATE_HEADERS.get());
        return ApiHeaders.getHeaders(context.getRequestHeaders(), headers);
    }
}