/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.quarkus.web.filter;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class VertxHttpServerContext implements IHttpServerContext {
    private final RoutingContext routingContext;

    private boolean responseSent;

    private IContext context;

    private String characterEncoding;


    public VertxHttpServerContext(RoutingContext routingContext) {
        this.routingContext = routingContext;
        this.context = ContextProvider.currentContext();
    }

    @Override
    public String getHost() {
        return routingContext.request().host();
    }

    @Override
    public String getRequestPath() {
        return routingContext.normalizedPath();
    }

    @Override
    public String getRemoteAddr() {
        return routingContext.request().remoteAddress().hostAddress();
    }

    @Override
    public int getRemotePort() {
        return routingContext.request().remoteAddress().port();
    }

    @Override
    public String getRequestUrl() {
        String uri = routingContext.request().absoluteURI();
        return uri;
    }

    @Override
    public String getQueryParam(String name) {
        List<String> list = routingContext.queryParam(name);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Map<String, String> getQueryParams() {
        Map<String, String> ret = new LinkedHashMap<>();
        routingContext.queryParams().forEach((name, value) -> {
            ret.put(name, value);
        });
        return ret;
    }

    @Override
    public Map<String, Object> getRequestHeaders() {
        MultiMap map = routingContext.request().headers();
        Map<String, Object> ret = new TreeMap<>();
        for (Map.Entry<String, String> entry : map) {
            String normalized = entry.getKey().toLowerCase(Locale.ENGLISH);
            ret.putIfAbsent(normalized, entry.getValue());
        }
        return ret;
    }

    @Override
    public Object getRequestHeader(String headerName) {
        return routingContext.request().getHeader(headerName);
    }

    @Override
    public String getCookie(String name) {
        Cookie cookie = routingContext.request().getCookie(name);
        if (cookie == null)
            return null;
        return cookie.getValue();
    }

    @Override
    public void resumeRequest() {
        routingContext.request().resume();
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
        routingContext.response().addCookie(cookie);
    }

    @Override
    public void removeCookie(String name) {
        routingContext.response().removeCookie(name);
    }

    @Override
    public void removeCookie(String name, String domain, String path) {
        routingContext.response().removeCookie(name, domain, path);
    }

    @Override
    public void setResponseHeader(String headerName, Object value) {
        if (value == null) {
            routingContext.response().headers().remove(headerName);
        } else {
            routingContext.response().headers().set(headerName, String.valueOf(value));
        }
    }

    @Override
    public void sendRedirect(String url) {
        responseSent = true;
        routingContext.redirect(url);
    }

    @Override
    public void sendResponse(int httpStatus, String body) {
        responseSent = true;
        routingContext.response().setStatusCode(httpStatus);
        routingContext.response().send(body);
    }

    public boolean isResponseSent() {
        return responseSent;
    }

    @Override
    public String getAcceptableContentType() {
        return routingContext.getAcceptableContentType();
    }

    @Override
    public String getResponseContentType() {
        return routingContext.response().headers().get(HttpHeaders.CONTENT_TYPE);
    }

    @Override
    public void setResponseContentType(String contentType) {
        if (characterEncoding != null && !contentType.contains("charset="))
            contentType += ";charset=" + characterEncoding;
        routingContext.response().headers().set(HttpHeaders.CONTENT_TYPE, contentType);
    }

    @Override
    public void setResponseCharacterEncoding(String encoding) {
        this.characterEncoding = encoding;
        String contentType = getResponseContentType();
        if (contentType != null) {
            setResponseContentType(contentType);
        }
    }

    public CompletionStage<Void> proceedAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        routingContext.addBodyEndHandler(ret -> {
            future.complete(null);
        });
        routingContext.addEndHandler(ret -> {
            if (ret.failed()) {
                future.completeExceptionally(ret.cause());
            } else {
                future.complete(null);
            }
        });

        routingContext.next();
        return future;
    }

    @Override
    public IAsyncBody getRequestBody() {
        return new IAsyncBody() {
            @Override
            public CompletionStage<String> getTextAsync() {
                CompletableFuture<String> future = new CompletableFuture<>();
                routingContext.request().body().onSuccess(v -> {
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

    @Override
    public IContext getContext() {
        return context;
    }

    @Override
    public void setContext(IContext context) {
        this.context = context;
    }
}
