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
import io.nop.api.core.exceptions.NopException;
import io.nop.gateway.core.context.IGatewayContext;

import java.util.concurrent.CompletionStage;

/**
 * Gateway拦截器接口，用于在网关请求处理过程中执行拦截逻辑
 * <p>
 * 拦截器执行顺序：
 * <pre>
 * 1. match匹配条件判断
 * 2. onRequest：在请求发送前调用，可以修改请求
 * 3. invoke/forward：执行实际的调用或转发
 * 4. onResponse：在响应返回后调用，可以修改响应
 * 5. onError：在发生异常时调用
 * </pre>
 * <p>
 * 流式处理模式：
 * <pre>
 * 当进入流式传输模式时，onResponse/responseMapping/onError不再使用，
 * 转而使用以下流式回调方法：
 * - onStreamStart：流开始传输时调用
 * - onStreamElement：每个流元素到达时调用
 * - onStreamError：流处理出错时调用
 * - onStreamComplete：流传输完成时调用
 * </pre>
 */
public interface IGatewayInterceptor {

    default ApiRequest<?> onRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
        return request;
    }

    default ApiResponse<?> onResponse(ApiResponse<?> response, IGatewayContext svcCtx) {
        return response;
    }

    default ApiResponse<?> onError(Throwable exception, IGatewayContext svcCtx) {
        throw NopException.adapt(exception);
    }

    default void onStreamStart(ApiRequest<?> request, IGatewayContext svcCtx) {
    }


    default Object onStreamElement(Object element, IGatewayContext svcCtx) {
        return element;
    }

    default Object onStreamError(Throwable exception, IGatewayContext svcCtx) {
        throw NopException.adapt(exception);
    }

    default void onStreamComplete(IGatewayContext svcCtx) {
    }
}
