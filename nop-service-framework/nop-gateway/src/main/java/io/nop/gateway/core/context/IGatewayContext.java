/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.context;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.core.context.IServiceContext;
import io.nop.gateway.model.GatewayRouteModel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gateway上下文接口，扩展自服务上下文，提供网关特定的上下文信息
 *
 * <p>网关上下文包含当前路由信息、路径变量、流式模式标记和请求路径等网关特定的数据。</p>
 */
public interface IGatewayContext extends IServiceContext {

    /**
     * 获取当前匹配的路由
     *
     * @return 当前路由对象，如果未匹配到路由则返回null
     */
    GatewayRouteModel getCurrentRoute();

    /**
     * 设置当前匹配的路由
     *
     * @param route 当前路由对象
     */
    void setCurrentRoute(GatewayRouteModel route);

    /**
     * 获取路径变量
     *
     * <p>路径变量是从请求URL中提取的动态参数，例如对于路径 /users/{userId}，
     * 当请求为 /users/123 时，路径变量为 {"userId": "123"}</p>
     *
     * @return 路径变量映射，如果不存在则返回null
     */
    Map<String, Object> getPathVariables();

    /**
     * 设置路径变量
     *
     * @param pathVariables 路径变量映射
     */
    void setPathVariables(Map<String, Object> pathVariables);

    /**
     * 判断是否为流式响应模式
     *
     * <p>流式模式下，网关会以流式方式转发响应数据，适用于大文件传输或实时数据推送场景</p>
     *
     * @return 如果是流式模式返回true，否则返回false
     */
    boolean isStreamingMode();

    /**
     * 设置流式响应模式
     *
     * @param streamingMode 是否启用流式模式
     */
    void setStreamingMode(boolean streamingMode);

    /**
     * 获取请求路径
     *
     * <p>请求路径是客户端请求的原始URL路径，不包含查询参数</p>
     *
     * @return 请求路径
     */
    String getRequestPath();

    /**
     * 设置请求路径
     *
     * @param requestPath 请求路径
     */
    void setRequestPath(String requestPath);

    Map<String, String> getQueryParams();

    void setQueryParams(Map<String, String> queryParams);

    default String getQueryParam(String name) {
        Map<String, String> params = getQueryParams();
        return params == null ? null : params.get(name);
    }

    default void setQueryParam(String name, String value) {
        Map<String, String> params = getQueryParams();
        if (params == null) {
            params = new LinkedHashMap<>();
            setQueryParams(params);
        }
        params.put(name, value);
    }

    String getHttpMethod();

    ApiRequest<?> getRequest();

    void setRequest(ApiRequest<?> request);

    ApiResponse<?> getResponse();

    void setResponse(ApiResponse<?> response);
}
