/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.web.filter;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.server.IAsyncBody;
import io.nop.http.api.server.IHttpServerContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.net.HttpCookie;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

public class ServletHttpServerContext implements IHttpServerContext {
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    private IContext context;

    private String characterEncoding;

    public ServletHttpServerContext(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
        this.context = ContextProvider.currentContext();
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
    public Map<String, String> getQueryParams() {
        Map<String, String> ret = new LinkedHashMap<>();
        Enumeration<String> it = request.getParameterNames();
        while (it.hasMoreElements()) {
            String name = it.nextElement();
            String value = getQueryParam(name);
            ret.put(name, value);
        }
        return ret;
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
        retCookie.setHttpOnly(true);
        retCookie.setMaxAge(0);
        retCookie.setSecure(true);
        response.addCookie(retCookie);
    }

    @Override
    public void removeCookie(String name, String domain, String path) {
        Cookie retCookie = new Cookie(name, "");
        retCookie.setPath(path);
        if (Objects.nonNull(domain))
            retCookie.setDomain(domain);
        retCookie.setHttpOnly(true);
        retCookie.setSecure(true);
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
        if (characterEncoding != null && !contentType.contains("charset=")) {
            contentType += ";charset=" + characterEncoding;
        }
        response.setContentType(contentType);
    }

    @Override
    public void setResponseCharacterEncoding(String encoding) {
        this.characterEncoding = encoding;
        response.setCharacterEncoding(encoding);
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

    @Override
    public IContext getContext() {
        return context;
    }

    @Override
    public void setContext(IContext context) {
        this.context = context;
    }
}
