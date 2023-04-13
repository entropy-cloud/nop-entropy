/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.rpc;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ApiHeaders;

import java.util.concurrent.CompletionStage;

/**
 * 根据ApiRequest中的信息初始化IContext。当请求执行完毕之后自动清除context
 */
public class ApiContextInterceptor implements IRpcServiceInterceptor {

    public int order() {
        return ApiConstants.INTERCEPTOR_PRIORITY_API_CONTEXT;
    }

    @Override
    public CompletionStage<ApiResponse<?>> interceptAsync(
            IRpcServiceInvocation inv) {

        IContext context = ContextProvider.newContext();

        ApiRequest<?> request = inv.getRequest();
        initContext(context, request);

        return inv.proceedAsync().whenComplete(
                (ret, err) -> {
                    context.close();
                });
    }

    @Override
    public ApiResponse<?> intercept(
            IRpcServiceInvocation inv) {

        IContext context = ContextProvider.newContext();

        ApiRequest<?> request = inv.getRequest();
        initContext(context, request);

        try {
            return inv.proceed();
        } finally {
            context.close();
        }
    }

    void initContext(IContext context, ApiRequest<?> request) {
        String timezone = ApiHeaders.getTimeZone(request);
        String locale = ApiHeaders.getLocale(request);
        String tenantId = ApiHeaders.getTenant(request);
        long timeout = ApiHeaders.getTimeout(request, -1);

        context.setLocale(locale);
        context.setTenantId(tenantId);
        context.setTimezone(timezone);

        if (timeout > 0) {
            context.setCallExpireTime(CoreMetrics.currentTimeMillis() + timeout);
        }
    }
}
