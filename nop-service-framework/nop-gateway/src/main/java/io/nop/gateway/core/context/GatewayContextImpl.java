/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.context;

import io.nop.core.context.ServiceContextImpl;

import java.util.Map;

/**
 * Gateway上下文实现类，提供网关特定的上下文功能
 *
 * <p>该类扩展了ServiceContextImpl，在继承服务上下文所有功能的基础上，增加了网关特定的上下文信息，
 * 如当前路由、路径变量、流式模式标记和请求路径等。</p>
 */
public class GatewayContextImpl extends ServiceContextImpl implements IGatewayContext {

    private Object currentRoute;

    private Map<String, Object> pathVariables;

    private boolean streamingMode;

    private String requestPath;

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
    public synchronized Object getCurrentRoute() {
        return currentRoute;
    }

    @Override
    public synchronized void setCurrentRoute(Object route) {
        this.currentRoute = route;
    }

    @Override
    public synchronized Map<String, Object> getPathVariables() {
        return pathVariables;
    }

    @Override
    public synchronized void setPathVariables(Map<String, Object> pathVariables) {
        this.pathVariables = pathVariables;
    }

    @Override
    public synchronized boolean isStreamingMode() {
        return streamingMode;
    }

    @Override
    public synchronized void setStreamingMode(boolean streamingMode) {
        this.streamingMode = streamingMode;
    }

    @Override
    public synchronized String getRequestPath() {
        return requestPath;
    }

    @Override
    public synchronized void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }
}
