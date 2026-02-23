/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.core.context.IGatewayContext;

import java.util.concurrent.CompletionStage;

public interface IGatewayInvocation {
    ApiRequest<?> proceedOnRequest(ApiRequest<?> request, IGatewayContext svcCtx);

    ApiResponse<?> proceedOnResponse(ApiResponse<?> response, IGatewayContext svcCtx);

    ApiResponse<?> proceedOnError(Throwable exception, IGatewayContext svcCtx);

    void proceedOnStreamStart(ApiRequest<?> request, IGatewayContext svcCtx);

    Object proceedOnStreamElement(Object element, IGatewayContext svcCtx);

    Object proceedOnStreamError(Throwable exception, IGatewayContext svcCtx);

    void proceedOnStreamComplete(IGatewayContext svcCtx);

    /**
     * 执行完整的调用流程，包括请求处理、实际调用和响应处理。
     * 拦截器可以通过调用此方法来继续执行后续逻辑。
     *
     * @param request 请求对象
     * @param svcCtx  网关上下文
     * @return 异步响应对象
     */
    CompletionStage<ApiResponse<?>> proceedInvoke(ApiRequest<?> request, IGatewayContext svcCtx);
}
