/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.context;

import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.IContext;
import io.nop.api.core.util.ApiHeaders;
import io.nop.commons.cache.ICache;
import io.nop.core.CoreConstants;

import java.util.Map;
import java.util.TreeMap;

/**
 * 服务上下文对应一次请求处理过程的上下文。服务接收到{@link io.nop.api.core.beans.ApiRequest}之后， 会把headers和body拆解后放置到{@link IServiceContext}上。 *
 * 服务响应函数处理完毕之后，框架根据{@link IServiceContext}上的
 * response,error,responseHeaders等信息构造{@link io.nop.api.core.beans.ApiResponse}
 */
public interface IServiceContext extends IExecutionContext, ISecurityContext {
    /**
     * 服务入口的请求对象，贯穿任务的处理过程。对应于ApiRequest的data部分
     */
    Object getRequest();

    void setRequest(Object request);

    Object getResponse();

    void setResponse(Object response);

    Map<String, Object> getRequestHeaders();

    void setRequestHeaders(Map<String, Object> requestHeaders);

    default Object getRequestHeader(String name) {
        return ApiHeaders.getHeader(getRequestHeaders(), name);
    }

    default void setRequestHeader(String name, Object value) {
        Map<String, Object> headers = getRequestHeaders();
        if (headers == null) {
            headers = new TreeMap<>();
            setRequestHeaders(headers);
        }
        ApiHeaders.setHeader(headers, name, value);
    }

    Map<String, Object> getResponseHeaders();

    void setResponseHeaders(Map<String, Object> headers);

    default void clearResponseHeaders() {
        Map<String, Object> headers = getResponseHeaders();
        if (headers != null)
            headers.clear();
    }

    default void setResponseHeader(String name, Object value) {
        Map<String, Object> headers = getRequestHeaders();
        if (headers == null) {
            headers = new TreeMap<>();
            setRequestHeaders(headers);
        }
        ApiHeaders.setHeader(headers, name, value);
    }

    default Object getResponseHeader(String name) {
        return ApiHeaders.getHeader(getResponseHeaders(), name);
    }

    /**
     * 创建子任务的context。
     */
    IServiceContext newChildContext();

    IServiceContext getParentContext();

    ICache<Object, Object> getCache();

    void setCache(ICache<Object, Object> cache);

    IActionAuthChecker getActionAuthChecker();

    void setActionAuthChecker(IActionAuthChecker checker);

    IDataAuthChecker getDataAuthChecker();

    void setDataAuthChecker(IDataAuthChecker checker);

    IUserContext getUserContext();

    void setUserContext(IUserContext userContext);

    IContext getContext();

    void setContext(IContext context);

    default String getUserId() {
        IContext ctx = getContext();
        return ctx == null ? null : ctx.getUserId();
    }

    static IServiceContext fromEvalContext(IEvalContext context) {
        if (context == null)
            return null;

        if (context instanceof IServiceContext)
            return (IServiceContext) context;
        return (IServiceContext) context.getEvalScope().getValue(CoreConstants.VAR_SVC_CTX);
    }
}