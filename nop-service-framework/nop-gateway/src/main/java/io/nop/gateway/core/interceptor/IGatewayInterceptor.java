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
import io.nop.core.context.IServiceContext;

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

    /**
     * 请求发送前调用
     *
     * @param request     API请求对象，包含请求头、选择器和数据
     * @param invocation  网关调用上下文，包含路由配置等信息
     * @param serviceContext 服务上下文
     * @throws Exception 可以抛出异常中断请求处理
     */
    void onRequest(ApiRequest<?> request, GatewayInvocation invocation, IServiceContext serviceContext)
            throws Exception;

    /**
     * 响应返回后调用
     *
     * @param response    API响应对象，包含状态码、错误信息和响应数据
     * @param invocation  网关调用上下文，包含路由配置等信息
     * @param serviceContext 服务上下文
     * @throws Exception 可以抛出异常中断响应处理
     */
    void onResponse(ApiResponse<?> response, GatewayInvocation invocation, IServiceContext serviceContext)
            throws Exception;

    /**
     * 请求处理过程中发生异常时调用
     *
     * @param exception   抛出的异常对象
     * @param invocation  网关调用上下文，包含路由配置等信息
     * @param serviceContext 服务上下文
     * @throws Exception 可以抛出新的异常或继续传播原异常
     */
    void onError(Throwable exception, GatewayInvocation invocation, IServiceContext serviceContext)
            throws Exception;

    /**
     * 流式传输开始时调用
     * <p>
     * 此方法在onRequest执行成功后，进入流式传输模式时调用
     *
     * @param request     API请求对象，包含请求头、选择器和数据
     * @param invocation  网关调用上下文，包含路由配置等信息
     * @param serviceContext 服务上下文
     * @throws Exception 可以抛出异常中断流式传输
     */
    default void onStreamStart(ApiRequest<?> request, GatewayInvocation invocation, IServiceContext serviceContext)
            throws Exception {
        // 默认空实现
    }

    /**
     * 每个流元素到达时调用
     * <p>
     * 此方法在流式传输过程中，每当收到一个新的数据元素时调用
     *
     * @param element     流数据元素，具体类型取决于数据源
     * @param invocation  网关调用上下文，包含路由配置等信息
     * @param serviceContext 服务上下文
     * @return 可以返回处理后的元素，或返回null过滤掉该元素
     * @throws Exception 可以抛出异常中断流式传输
     */
    default Object onStreamElement(Object element, GatewayInvocation invocation, IServiceContext serviceContext)
            throws Exception {
        // 默认空实现，直接返回原元素
        return element;
    }

    /**
     * 流式传输过程中发生错误时调用
     * <p>
     * 此方法在流式传输过程中发生异常时调用，此时无法返回完整响应
     *
     * @param exception   抛出的异常对象
     * @param invocation  网关调用上下文，包含路由配置等信息
     * @param serviceContext 服务上下文
     * @return 可以返回特殊的错误信息对象
     * @throws Exception 可以抛出新的异常或继续传播原异常
     */
    default Object onStreamError(Throwable exception, GatewayInvocation invocation, IServiceContext serviceContext)
            throws Exception {
        // 默认实现，将异常包装后抛出
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
        throw new RuntimeException(exception);
    }

    /**
     * 流式传输完成时调用
     * <p>
     * 此方法在所有流元素处理完成后调用，无论是否发生错误
     *
     * @param invocation  网关调用上下文，包含路由配置等信息
     * @param serviceContext 服务上下文
     * @throws Exception 可以抛出异常
     */
    default void onStreamComplete(GatewayInvocation invocation, IServiceContext serviceContext)
            throws Exception {
        // 默认空实现
    }
}
