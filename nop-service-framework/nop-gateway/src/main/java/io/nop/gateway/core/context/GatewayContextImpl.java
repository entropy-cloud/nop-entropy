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
import io.nop.core.context.ServiceContextImpl;
import io.nop.gateway.model.GatewayRouteModel;

import java.util.Map;

/**
 * Gateway上下文实现类，提供网关特定的上下文功能
 *
 * <p>该类扩展了ServiceContextImpl，在继承服务上下文所有功能的基础上，增加了网关特定的上下文信息，
 * 如当前路由、路径变量、流式模式标记和请求路径等。</p>
 *
 * <p>所有网关特定的数据通过getAttribute/setAttribute存储，不引入新的成员变量。</p>
 */
public class GatewayContextImpl extends ServiceContextImpl implements IGatewayContext {

    /**
     * 属性名：当前路由
     */
    public static final String ATTR_CURRENT_ROUTE = "currentRoute";

    /**
     * 属性名：路径变量
     */
    public static final String ATTR_PATH_VARIABLES = "pathVariables";

    /**
     * 属性名：流式模式标记
     */
    public static final String ATTR_STREAMING_MODE = "streamingMode";

    /**
     * 属性名：请求路径
     */
    public static final String ATTR_REQUEST_PATH = "requestPath";

    public static final String ATTR_QUERY_PARAMS = "queryParams";

    public static final String ATTR_HTTP_METHOD = "httpMethod";

    /**
     * 默认构造函数
     */
    public GatewayContextImpl() {
        super();
    }

    /**
     * 带变量的构造函数
     *
     * @param vars 初始变量映射
     */
    public GatewayContextImpl(Map<String, Object> vars) {
        super(vars);
    }

    @Override
    public GatewayRouteModel getCurrentRoute() {
        return (GatewayRouteModel) getAttribute(ATTR_CURRENT_ROUTE);
    }

    @Override
    public void setCurrentRoute(GatewayRouteModel route) {
        setAttribute(ATTR_CURRENT_ROUTE, route);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPathVariables() {
        return (Map<String, Object>) getAttribute(ATTR_PATH_VARIABLES);
    }

    @Override
    public void setPathVariables(Map<String, Object> pathVariables) {
        setAttribute(ATTR_PATH_VARIABLES, pathVariables);
    }

    @Override
    public boolean isStreamingMode() {
        Boolean mode = (Boolean) getAttribute(ATTR_STREAMING_MODE);
        return mode != null && mode;
    }

    @Override
    public void setStreamingMode(boolean streamingMode) {
        setAttribute(ATTR_STREAMING_MODE, streamingMode);
    }

    @Override
    public String getRequestPath() {
        return (String) getAttribute(ATTR_REQUEST_PATH);
    }

    @Override
    public void setRequestPath(String requestPath) {
        setAttribute(ATTR_REQUEST_PATH, requestPath);
    }

    @Override
    public Map<String, String> getQueryParams() {
        return (Map<String, String>) getAttribute(ATTR_QUERY_PARAMS);
    }

    @Override
    public void setQueryParams(Map<String, String> queryParams) {
        setAttribute(ATTR_QUERY_PARAMS, queryParams);
    }

    @Override
    public ApiRequest<?> getRequest() {
        return (ApiRequest<?>) super.getRequest();
    }

    @Override
    public ApiResponse<?> getResponse() {
        return (ApiResponse<?>) super.getResponse();
    }

    @Override
    public void setRequest(ApiRequest<?> request) {
        super.setRequest(request);
    }

    @Override
    public void setResponse(ApiResponse<?> response) {
        super.setResponse(response);
    }

    public String getHttpMethod(){
        return (String) getAttribute(ATTR_HTTP_METHOD);
    }

    public void setHttpMethod(String httpMethod){
        setAttribute(ATTR_HTTP_METHOD, httpMethod);
    }
}
