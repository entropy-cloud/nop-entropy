/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.quarkus.web.filter;

import io.nop.commons.util.StringHelper;
import io.nop.http.api.server.IAsyncBody;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.quarkus.web.utils.QuarkusExecutorHelper;
import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.impl.CookieImpl;
import io.vertx.ext.web.RoutingContext;

import java.net.HttpCookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class VertxHttpServerContext implements IHttpServerContext {
    private final RoutingContext context;

    private boolean responseSent;

    public VertxHttpServerContext(RoutingContext context) {
        this.context = context;
    }

    @Override
    public String getHost() {
        return context.request().host();
    }

    @Override
    public String getRequestPath() {
        return context.normalizedPath();
    }

    @Override
    public String getRequestUrl() {
        String uri = context.request().absoluteURI();
        return uri;
    }

    @Override
    public String getQueryParam(String name) {
        List<String> list = context.queryParam(name);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Map<String, Object> getRequestHeaders() {
        MultiMap map = context.request().headers();
        Map<String, Object> ret = new HashMap<>();
        for (Map.Entry<String, String> entry : map) {
            ret.putIfAbsent(entry.getKey().toLowerCase(), entry.getValue());
        }
        return ret;
    }

    @Override
    public Object getRequestHeader(String headerName) {
        return context.request().getHeader(headerName);
    }

    @Override
    public String getCookie(String name) {
        Cookie cookie = context.request().getCookie(name);
        if (cookie == null)
            return null;
        return cookie.getValue();
    }

    @Override
    public void resumeRequest() {
        context.request().resume();
    }

    @Override
    public void addCookie(String sameSite, HttpCookie httpCookie) {
        CookieImpl cookie = new CookieImpl(httpCookie.getName(), httpCookie.getValue());
        cookie.setDomain(httpCookie.getDomain());
        cookie.setHttpOnly(httpCookie.isHttpOnly());
        cookie.setPath(httpCookie.getPath());
        cookie.setSecure(httpCookie.getSecure());
        if (CookieSameSite.LAX.toString().equals(sameSite)) {
            cookie.setSameSite(CookieSameSite.LAX);
        } else if (CookieSameSite.NONE.toString().equals(sameSite)) {
            cookie.setSameSite(CookieSameSite.NONE);
        } else if (CookieSameSite.STRICT.toString().equals(sameSite)) {
            cookie.setSameSite(CookieSameSite.STRICT);
        }
        context.response().addCookie(cookie);
    }

    @Override
    public void removeCookie(String name) {
        context.response().removeCookie(name);
    }

    @Override
    public void removeCookie(String name, String domain, String path) {
        context.response().removeCookie(name, domain, path);
    }

    @Override
    public void setResponseHeader(String headerName, Object value) {
        if (value == null) {
            context.response().headers().remove(headerName);
        } else {
            context.response().headers().set(headerName, String.valueOf(value));
        }
    }

    @Override
    public void sendRedirect(String url) {
        responseSent = true;
        context.redirect(url);
    }

    @Override
    public void sendResponse(int httpStatus, String body) {
        responseSent = true;
        context.response().setStatusCode(httpStatus);
        context.response().send(body);
    }

    public boolean isResponseSent() {
        return responseSent;
    }

    @Override
    public String getAcceptableContentType() {
        return context.getAcceptableContentType();
    }

    @Override
    public String getResponseContentType() {
        return context.response().headers().get(HttpHeaders.CONTENT_TYPE);
    }

    @Override
    public void setResponseContentType(String contentType) {
        context.response().headers().set(HttpHeaders.CONTENT_TYPE, contentType);
    }

    public CompletionStage<Void> proceedAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        context.addEndHandler(ret -> {
            if (ret.failed()) {
                future.completeExceptionally(ret.cause());
            } else {
                future.complete(null);
            }
        });
        context.next();
        return future;
    }

    @Override
    public IAsyncBody getRequestBody() {
        return new IAsyncBody() {
            @Override
            public CompletionStage<String> getTextAsync() {
                CompletableFuture<String> future = new CompletableFuture<>();
                context.request().body().onSuccess(v -> {
                            future.complete(v.toString(StringHelper.CHARSET_UTF8));
                        })
                        .onFailure(e -> {
                            future.completeExceptionally(e);
                        });
                return future;
            }
        };
    }

    @Override
    public CompletionStage<Object> executeBlocking(Callable<?> task) {
        return QuarkusExecutorHelper.executeBlocking(task);
    }
}
