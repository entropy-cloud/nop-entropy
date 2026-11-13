/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.web.filter;

import io.nop.api.core.config.AppConfig;
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
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
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
    /**
     * C6-004（plan 2562 裁定 H）：startAsync 缺省无超时（无超时无监听面）——两站点统一加
     * 可配超时，缺省 60s，配置项遵循模块配置约定（AppConfig），键：
     * {@code nop.spring.web.async-timeout-millis}。
     */
    public static final long DEFAULT_ASYNC_TIMEOUT_MILLIS = 60000L;
    static final String CFG_ASYNC_TIMEOUT_MILLIS = "nop.spring.web.async-timeout-millis";

    private final HttpServletRequest request;
    private final HttpServletResponse response;

    /**
     * C1-005（plan 2562 裁定 H 搭车）：跨线程读写（异步/工作线程 vs 请求线程），volatile 可见性。
     */
    private volatile IContext context;

    private volatile String characterEncoding;

    private final long asyncTimeoutMillis;

    public ServletHttpServerContext(HttpServletRequest request, HttpServletResponse response) {
        this(request, response, resolveAsyncTimeoutMillis());
    }

    public ServletHttpServerContext(HttpServletRequest request, HttpServletResponse response,
                                    long asyncTimeoutMillis) {
        this.request = request;
        this.response = response;
        this.context = ContextProvider.currentContext();
        this.asyncTimeoutMillis = asyncTimeoutMillis > 0 ? asyncTimeoutMillis : DEFAULT_ASYNC_TIMEOUT_MILLIS;
    }

    private static long resolveAsyncTimeoutMillis() {
        Long value = AppConfig.getConfigProvider()
                .getConfigValue(CFG_ASYNC_TIMEOUT_MILLIS, DEFAULT_ASYNC_TIMEOUT_MILLIS);
        if (value == null || value.longValue() <= 0) {
            return DEFAULT_ASYNC_TIMEOUT_MILLIS;
        }
        return value.longValue();
    }

    @Override
    public String getMethod() {
        return request.getMethod();
    }

    /**
     * CL-025（plan 2550 裁定 E，能力勘误 E-2550-2）：Servlet 6.0 API 无轮询式客户端断连
     * 观测（HttpServletRequest.getConnection() 属 Servlet 6.1 Connection API，仓内
     * servlet-api=6.0 无此能力；写失败 IOException 属响应路径事后感知，非转发前检查）。
     * 保持 default 不可观测档（false）——网关重试链在本适配器下维持既有重试语义，勘误
     * 登记 plan 2550；升级 Servlet 6.1 后由 Connection API 承接真实检测。
     */
    @Override
    public boolean isClientAborted() {
        return false;
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
        // plan 2406 (P1-4.2-002): IHttpServerContext requires the full request link including the
        // queryString; getRequestURI() alone dropped it (e.g. AuthHttpServerFilter redirect-back).
        StringBuilder url = new StringBuilder(request.getRequestURL());
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            url.append('?').append(query);
        }
        return url.toString();
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
        // P1-6.8-001（plan 2391）：请求无任何 Cookie 时 getCookies() 返回 null
        //（Servlet 规范允许）——直接落到兜底过期 Cookie 分支（对齐 getCookie 的 null 守卫形态）。
        // 此前 NPE 使无 Cookie 的登出请求（过期会话/扫描器/curl）500。
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    cookie.setMaxAge(0);
                    response.addCookie(cookie);
                    return;
                }
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
            try (Writer out = response.getWriter()) {
                out.write(body);
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

        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean asyncClosed = new AtomicBoolean(false);
        AsyncContext asyncCtx = startAsyncWithTimeout(asyncClosed);

        publisher.subscribe(new Flow.Subscriber<String>() {
            private Flow.Subscription subscription;
            private Writer out;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                try {
                    this.out = response.getWriter();
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
                        out.write(item);
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
                        if (asyncClosed.compareAndSet(false, true)) {
                            asyncCtx.complete();
                        }
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
            }

            /**
             * 4.4-MA-03（plan 2562 裁定 A）：错误路径不再调用 asyncCtx.complete()——
             * complete 洗白截断（错误以正常完成收口）消除；错误路径仅经裁定 H 的
             * async timeout 终止（AsyncListener.onTimeout → complete 收口）。
             * future.completeExceptionally 语义保持（C10-018 断言面）。
             */
            private void completeWithError(Throwable error) {
                if (completed.compareAndSet(false, true)) {
                    future.completeExceptionally(error);
                }
            }
        });

        return future;
    }

    /**
     * C6-004（plan 2562 裁定 H）：startAsync 两站点统一加可配超时（缺省 60s）+
     * AsyncListener（onTimeout → complete 收口）。超时监听器吸收 C6-004「二次 ISE 噪声」
     * （唯一 complete 收口方经 asyncClosed CAS 串行化），并闭合裁定 A 移除错误路径
     * complete 后的超时-dispatch 循环。Servlet 规范允许 startAsync 后 setTimeout。
     */
    private AsyncContext startAsyncWithTimeout(AtomicBoolean asyncClosed) {
        AsyncContext asyncCtx = request.startAsync();
        asyncCtx.setTimeout(asyncTimeoutMillis);
        asyncCtx.addListener(new AsyncListener() {
            @Override
            public void onTimeout(AsyncEvent event) {
                // 4.4-MA-03 closure-audit 勘正（2562 audit B-1）：Servlet 6.0 AsyncContext
                // 无 completeWithError——按裁定 H 原形态 asyncCtx.complete() 收口。
                // 此处 complete 只提交已写出字节（客户端可见截断，非错误洗白）；
                // 错误语义由 future.completeExceptionally 承载（C10-018 断言面），
                // 与 R1-Finding-3「客户端可见截断」意图一致。
                if (asyncClosed.compareAndSet(false, true)) {
                    asyncCtx.complete();
                }
            }

            @Override
            public void onError(AsyncEvent event) {
                // 4.4-MA-03（plan 2562 裁定 A）：不在此 complete——错误终止统一走超时收口
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
            }

            @Override
            public void onComplete(AsyncEvent event) {
            }
        });
        return asyncCtx;
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

        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean asyncClosed = new AtomicBoolean(false);
        AsyncContext asyncCtx = startAsyncWithTimeout(asyncClosed);

        try {
            Writer out = response.getWriter();
            IStreamResponseWriter writer = new IStreamResponseWriter() {
                @Override
                public CompletionStage<Void> write(String chunk) {
                    try {
                        out.write(chunk);
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
                            if (asyncClosed.compareAndSet(false, true)) {
                                asyncCtx.complete();
                            }
                            future.complete(null);
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        }
                    }
                    return FutureHelper.success(null);
                }

                /**
                 * 4.4-MA-03（plan 2562 裁定 A）：错误路径不再调用 asyncCtx.complete()——
                 * 错误仅经裁定 H 的 async timeout（AsyncListener.onTimeout → complete）终止。
                 * future.completeExceptionally 语义保持（C10-018 断言面）。
                 */
                @Override
                public CompletionStage<Void> fail(Throwable error) {
                    if (completed.compareAndSet(false, true)) {
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
            // 4.4-MA-03（plan 2562 裁定 A）：装配异常面不再调用 asyncCtx.complete()——
            // 与 completeWithError/writerFn.fail 同形，错误仅经裁定 H async timeout 终止。
            if (completed.compareAndSet(false, true)) {
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
