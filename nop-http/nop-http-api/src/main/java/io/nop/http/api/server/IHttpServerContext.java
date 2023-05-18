/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.api.server;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;

import java.net.HttpCookie;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

import static io.nop.http.api.HttpApiErrors.ARG_HEADER_NAME;

/**
 * 封装http请求过程，底层支持不同的Web框架，如vertx，servlet等
 */
public interface IHttpServerContext {
    String HEADER_AUTHORIZATION = "authorization";
    String BEARER_PREFIX = "Bearer ";

    String HEADER_X_REQUESTED_WITH = "x-requested-with";
    String HEADER_X_ACCESS_TOKEN = "x-access-token";
    String HEADER_ACCEPT = "accept";

    String getHost();

    /**
     * 不包含queryString的后端服务路径
     */
    String getRequestPath();

    /**
     * 包含queryString的完整请求链接
     */
    String getRequestUrl();

    String getQueryParam(String name);

    Map<String, Object> getRequestHeaders();

    Object getRequestHeader(String headerName);

    default String getRequestStringHeader(String headerName) {
        return (String) getRequestHeader(headerName);
    }

    default long getRequestLongHeader(String headerName, long defaultValue) {
        Long value = ConvertHelper.toLong(getRequestHeader(headerName), err -> new NopException(err).param(ARG_HEADER_NAME, headerName));
        if (value == null)
            return defaultValue;
        return value;
    }

    String getCookie(String name);

    void addCookie(String sameSite, HttpCookie cookie);

    void removeCookie(String name);

    void removeCookie(String name, String domain, String path);

    void setResponseHeader(String headerName, Object value);

    void sendRedirect(String url);

    void sendResponse(int httpStatus, String body);

    boolean isResponseSent();

    String getAcceptableContentType();

    String getResponseContentType();

    void setResponseContentType(String contentType);

    CompletionStage<Object> executeBlocking(Callable<?> task);
}