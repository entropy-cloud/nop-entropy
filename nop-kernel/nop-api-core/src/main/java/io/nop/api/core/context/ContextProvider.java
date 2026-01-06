/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.context;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.nop.api.core.ApiErrors.ERR_CONTEXT_PROVIDER_ALREADY_INITIALIZED;
import static io.nop.api.core.ApiErrors.ERR_CONTEXT_PROVIDER_NOT_INITIALIZED;

@SuppressWarnings("PMD.TooManyStaticImports")
public class ContextProvider {
    private static IContextProvider _instance = new BaseContextProvider();

    public static void registerInstance(IContextProvider instance) {
        if (_instance != null && instance != null)
            throw new NopException(ERR_CONTEXT_PROVIDER_ALREADY_INITIALIZED);
        _instance = instance;
    }

    public static IContextProvider instance() {
        IContextProvider provider = _instance;
        if (provider == null)
            throw new NopException(ERR_CONTEXT_PROVIDER_NOT_INITIALIZED);
        return provider;
    }

    /**
     * 从当前context上读取自定义属性
     *
     * @param name 属性名
     * @return 如果当前context为null，或者context上没有对应的属性，则返回null
     */
    public static Object getContextAttr(String name) {
        IContext current = currentContext();
        if (current == null)
            return null;
        return current.getAttribute(name);
    }

    public static void setContextAttr(String name, Object value) {
        IContext context = getOrCreateContext();
        context.setAttribute(name, value);
    }

    public static IContext currentContext() {
        return _instance.currentContext();
    }

    public static IContext getOrCreateContext() {
        return _instance.getOrCreateContext();
    }

    public static IContext newContext() {
        return newContext(true);
    }

    public static IContext newContext(boolean attach) {
        return _instance.newContext(attach);
    }

    public static boolean isCallExpired() {
        IContext ctx = currentContext();
        if (ctx == null)
            return false;
        return ctx.isCallExpired();
    }


    public static String currentLocale() {
        IContext context = currentContext();
        return context == null ? null : context.getLocale();
    }

    public static String currentUserId() {
        IContext context = currentContext();
        return context == null ? null : context.getUserId();
    }

    public static String currentUserName() {
        IContext context = currentContext();
        return context == null ? null : context.getUserName();
    }

    public static String currentTenantId() {
        IContext context = currentContext();
        return context == null ? null : context.getTenantId();
    }

    public static String currentUserRefNo() {
        IContext context = currentContext();
        return context == null ? null : context.getUserRefNo();
    }


    /**
     * 通过这个函数构造出一个没有tenant上下文的环境，然后在此环境下相关代码
     */
    public static <T> T runWithoutTenantId(Supplier<T> task) {
        IContext context = currentContext();
        if (context == null || context.isClosed())
            return task.get();

        if (context.getTenantId() == null)
            return task.get();

        IContext proxy = new TenantProxyContext(context);
        proxy.setTenantId(null);

        return runWithProxyContext(proxy, context, task);
    }

    public static <T> T runWithTenant(String tenantId, Supplier<T> task) {
        IContext context = getOrCreateContext();

        String oldTenantId = context.getTenantId();
        if (Objects.equals(tenantId, oldTenantId))
            return task.get();

        IContext proxy = new TenantProxyContext(context);
        proxy.setTenantId(tenantId);
        return runWithProxyContext(proxy, context, task);
    }

    private static <T> T runWithProxyContext(IContext context, IContext prevContext, Supplier<T> task) {
        IContextProvider provider = _instance;
        try {
            provider.attachContext(context);
            return task.get();
        } finally {
            provider.attachContext(prevContext);
        }
    }

    public static <T> T runWithContext(Function<IContext, T> task) {
        IContext oldContext = _instance.currentContext();
        IContext context = _instance.getOrCreateContext();
        try {
            return task.apply(context);
        } finally {
            if (oldContext == null) {
                context.close();
            }
        }
    }

    /**
     * 确保回调函数在当前context上执行。但是返回的promise需要立刻被使用，如果传递到其他线程上使用，则不确保这一点
     */
    public static <T> CompletionStage<T> thenOnContext(CompletionStage<T> future) {
        IContext context = getOrCreateContext();

        return thenOnContext(future, context);
    }

    /**
     * 确保future的回调函数执行时，上下文环境为context。
     *
     * @return 一个包装后的CompletionStage对象，它的complete回调函数在context上下文中调用
     */
    public static <T> CompletionStage<T> thenOnContext0(CompletionStage<T> future, IContext context) {
        Guard.notNull(context, "context");

        CompletableFuture<T> promise = new CompletableFuture<>();
        future.whenComplete((value, err) -> {
            context.execute(() -> {
                FutureHelper.complete(promise, value, err);
            });
        });

        return promise;
    }

    /**
     * 将回调函数发送到context的任务队列中执行，执行时线程上下文绑定context，且当前只有一个线程在执行。
     * 返回的promise需要立刻被使用，如果传递到其他线程上使用，则不确保这一点。
     */
    public static <T> CompletionStage<T> thenOnContext(CompletionStage<T> future, IContext context) {
        Guard.notNull(context, "context");
        if (FutureHelper.isDone(future) && context.isRunningOnContext()) {
            return future;
        }
        return thenOnContext0(future, context);
    }

    public static <T> void completeOnContext(CompletionStage<T> future, BiConsumer<? super T, Throwable> handler) {
        IContext context = getOrCreateContext();

        completeOnContext(future, context, handler);
    }

    public static <T> void completeOnContext(CompletionStage<T> future, IContext context,
                                             BiConsumer<? super T, Throwable> handler) {
        thenOnContext(future, context).whenComplete(handler);
    }

    public static <R, T> CompletionStage<R> completeAsyncOnContext(
            CompletionStage<T> future,
            BiFunction<? super T, ? super Throwable, CompletionStage<R>> handler) {
        return completeAsyncOnContext(future, getOrCreateContext(), handler);
    }

    public static <R, T> CompletionStage<R> completeAsyncOnContext(
            CompletionStage<T> future, IContext context,
            BiFunction<? super T, ? super Throwable, CompletionStage<R>> handler) {

        future = thenOnContext(future, context);
        CompletableFuture<R> f = new CompletableFuture<>();
        future.whenComplete((ret, err) -> {
            try {
                thenOnContext(handler.apply(ret, err), context).whenComplete((ret2, err2) -> {
                    FutureHelper.complete(f, ret2, err2);
                });
            } catch (Exception e) {
                f.completeExceptionally(e);
            }
        });
        return f;
    }

    /**
     * 在执行task的过程中禁用context上的callExpireTime参数。主要用于一些记录日志的场景，避免日志记录过程中因为超时发生日志漏记
     */
    public static <T> T disableExpireTime(Supplier<T> task) {
        IContext context = currentContext();
        if (context == null || context.getCallExpireTime() < 0)
            return task.get();

        IContext proxy = new CallExpireTimeProxyContext(context);
        return runWithProxyContext(proxy, context, task);
    }

    public static void propagateContext(IContext ctx, IContext baseCtx, boolean copyRcpHeaders) {
        ctx.setTenantId(baseCtx.getTenantId());
        ctx.setLocale(baseCtx.getLocale());
        ctx.setCallIp(baseCtx.getCallIp());
        ctx.setTraceId(baseCtx.getTraceId());
        ctx.setTimezone(baseCtx.getTimezone());
        ctx.setUserId(baseCtx.getUserId());
        ctx.setUserName(baseCtx.getUserName());
        ctx.setUserRefNo(baseCtx.getUserRefNo());
        if (copyRcpHeaders)
            ctx.setPropagateRpcHeaders(baseCtx.getPropagateRpcHeaders());
    }
}