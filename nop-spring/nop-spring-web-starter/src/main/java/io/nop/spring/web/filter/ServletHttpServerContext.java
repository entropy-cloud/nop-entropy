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
import io.nop.http.api.server.IStreamResponseWriter;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

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
    public String getMethod() {
        return request.getMethod();
    }

    @Override
    public String getRemoteAddr() {
        return request.getRemoteAddr();
    }

    @Override
    public int getRemotePort() {
        return request.getRemotePort();
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
        Map<String, Object> ret = new TreeMap<>();
        Enumeration<String> it = request.getHeaderNames();
        while (it.hasMoreElements()) {
            String name = it.nextElement();
            String normalized = name.toLowerCase(Locale.ENGLISH);
            ret.put(normalized, request.getHeader(name));
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
        response.setStatus(httpStatus);

        if (!StringHelper.isEmpty(body)) {
            try (OutputStream out = response.getOutputStream()) {
                IoHelper.write(out, body.getBytes(), null);
                out.flush();
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public void sendResponse(int httpStatus, InputStream body) {
        response.setStatus(httpStatus);

        try (OutputStream out = response.getOutputStream()) {
            IoHelper.copy(body, out);
            out.flush();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public CompletionStage<Void> sendStreamingResponse(int httpStatus, String contentType,
                                                       Flow.Publisher<String> publisher) {
        response.setStatus(httpStatus);
        if (contentType != null) {
            response.setContentType(contentType);
        }
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        AsyncContext asyncCtx = request.startAsync();
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        publisher.subscribe(new Flow.Subscriber<String>() {
            private Flow.Subscription subscription;
            private OutputStream out;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                try {
                    this.out = response.getOutputStream();
                    subscription.request(1);
                } catch (Exception e) {
                    subscription.cancel();
                    completeWithError(e);
                }
            }

            @Override
            public void onNext(String item) {
                if (item != null && !completed.get()) {
                    try {
                        out.write(item.getBytes(StringHelper.CHARSET_UTF8));
                        out.flush();
                    } catch (Exception e) {
                        subscription.cancel();
                        completeWithError(e);
                        return;
                    }
                }
                subscription.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
                completeWithError(throwable);
            }

            @Override
            public void onComplete() {
                if (completed.compareAndSet(false, true)) {
                    try {
                        if (out != null) {
                            out.flush();
                        }
                        asyncCtx.complete();
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
            }

            private void completeWithError(Throwable error) {
                if (completed.compareAndSet(false, true)) {
                    asyncCtx.complete();
                    future.completeExceptionally(error);
                }
            }
        });

        return future;
    }

    @Override
    public CompletionStage<Void> sendStreamingResponse(int httpStatus, String contentType,
                                                       Function<IStreamResponseWriter, CompletionStage<Void>> writerFn) {
        response.setStatus(httpStatus);
        if (contentType != null) {
            response.setContentType(contentType);
        }
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        AsyncContext asyncCtx = request.startAsync();
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        try {
            OutputStream out = response.getOutputStream();
            IStreamResponseWriter writer = new IStreamResponseWriter() {
                @Override
                public CompletionStage<Void> write(String chunk) {
                    try {
                        out.write(chunk.getBytes(StringHelper.CHARSET_UTF8));
                        out.flush();
                        return FutureHelper.success(null);
                    } catch (Exception e) {
                        return FutureHelper.reject(e);
                    }
                }

                @Override
                public CompletionStage<Void> complete() {
                    if (completed.compareAndSet(false, true)) {
                        try {
                            out.flush();
                            asyncCtx.complete();
                            future.complete(null);
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        }
                    }
                    return FutureHelper.success(null);
                }

                @Override
                public CompletionStage<Void> fail(Throwable error) {
                    if (completed.compareAndSet(false, true)) {
                        asyncCtx.complete();
                        future.completeExceptionally(error);
                    }
                    return FutureHelper.success(null);
                }
            };

            CompletionStage<Void> result = writerFn.apply(writer);
            if (result != null) {
                result.whenComplete((v, err) -> {
                    if (err != null) {
                        writer.fail(err);
                    } else {
                        writer.complete();
                    }
                });
            }
        } catch (Exception e) {
            if (completed.compareAndSet(false, true)) {
                asyncCtx.complete();
                future.completeExceptionally(e);
            }
        }

        return future;
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
