/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.web.filter;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.server.IAsyncBody;
import io.nop.http.api.server.IHttpServerContext;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.HttpCookie;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

public class ServletHttpServerContext implements IHttpServerContext {
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    public ServletHttpServerContext(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

    @Override
    public String getHost() {
        return request.getRemoteHost();
    }

    @Override
    public String getRequestPath() {
        return request.getRequestURI();
    }

    @Override
    public String getRequestUrl() {
        return request.getRequestURI();
    }

    @Override
    public String getQueryParam(String name) {
        return request.getParameter(name);
    }

    @Override
    public Map<String, Object> getRequestHeaders() {
        Map<String, Object> ret = new HashMap<>();
        Enumeration<String> it = request.getHeaderNames();
        while (it.hasMoreElements()) {
            String name = it.nextElement();
            ret.put(name, request.getHeader(name));
        }
        return ret;
    }

    @Override
    public Object getRequestHeader(String headerName) {
        return request.getHeader(headerName);
    }

    @Override
    public String getCookie(String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
            return null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name))
                return cookie.getValue();
        }
        return null;
    }

    @Override
    public void addCookie(String sameSite, HttpCookie cookie) {
        Cookie retCookie = new Cookie(cookie.getName(), cookie.getValue());
        retCookie.setHttpOnly(cookie.isHttpOnly());
        // issues/I6RRQ7
        if (Objects.nonNull(cookie.getDomain())) {
            retCookie.setDomain(cookie.getDomain());
        }
        retCookie.setPath(cookie.getPath());
        retCookie.setSecure(cookie.getSecure());
        retCookie.setMaxAge((int) cookie.getMaxAge());
        response.addCookie(retCookie);
    }

    @Override
    public void removeCookie(String name) {
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) {
                cookie.setMaxAge(0);
                response.addCookie(cookie);
                return;
            }
        }
        Cookie retCookie = new Cookie(name, "");
        retCookie.setPath("/");
        retCookie.setMaxAge(0);
        response.addCookie(retCookie);
    }

    @Override
    public void removeCookie(String name, String domain, String path) {
        Cookie retCookie = new Cookie(name, "");
        retCookie.setPath(path);
        retCookie.setDomain(domain);
        retCookie.setMaxAge(0);
        response.addCookie(retCookie);
    }

    @Override
    public void setResponseHeader(String headerName, Object value) {
        response.setHeader(headerName, String.valueOf(value));
    }

    @Override
    public void sendRedirect(String url) {
        try {
            response.sendRedirect(url);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void sendResponse(int httpStatus, String body) {
        try {
            response.sendError(httpStatus, body);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public boolean isResponseSent() {
        return response.isCommitted();
    }

    @Override
    public String getAcceptableContentType() {
        return request.getContentType();
    }

    @Override
    public String getResponseContentType() {
        return response.getContentType();
    }

    @Override
    public void setResponseContentType(String contentType) {
        response.setContentType(contentType);
    }

    @Override
    public IAsyncBody getRequestBody() {
        return new IAsyncBody() {
            @Override
            public CompletionStage<String> getTextAsync() {
                return FutureHelper.futureCall(() -> {
                    String text = IoHelper.readText(request.getInputStream(), StringHelper.ENCODING_UTF8);
                    return text;
                });
            }
        };
    }

    @Override
    public CompletionStage<Object> executeBlocking(Callable<?> task) {
        return FutureHelper.futureCall(task);
    }
}
