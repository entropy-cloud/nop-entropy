/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.context;

import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.context.IContext;
import io.nop.commons.cache.ICache;
import io.nop.core.lang.eval.IEvalScope;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class DelegateServiceContext implements IServiceContext {
    private final IServiceContext serviceContext;

    public DelegateServiceContext(IServiceContext serviceContext) {
        this.serviceContext = serviceContext;
    }

    @Override
    public void setEvalScope(IEvalScope scope) {
        serviceContext.setEvalScope(scope);
    }

    @Override
    public String getExecutionId() {
        return serviceContext.getExecutionId();
    }

    @Override
    public void appendOnCancel(Consumer<String> task) {
        serviceContext.appendOnCancel(task);
    }

    @Override
    public void appendOnCancelTask(Runnable task) {
        serviceContext.appendOnCancelTask(task);
    }

    @Override
    public void removeOnCancel(Consumer<String> task) {
        serviceContext.removeOnCancel(task);
    }

    @Override
    public boolean isCancelled() {
        return serviceContext.isCancelled();
    }

    @Override
    public String getCancelReason() {
        return serviceContext.getCancelReason();
    }

    @Override
    public void cancel(String reason) {
        serviceContext.cancel(reason);
    }

    @Override
    public void cancel() {
        serviceContext.cancel();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return serviceContext.getAttributes();
    }

    @Override
    public Set<String> getAttributeNames() {
        return serviceContext.getAttributeNames();
    }

    @Override
    public Object getAttribute(String name) {
        return serviceContext.getAttribute(name);
    }

    @Override
    public Object requireAttribute(String name) {
        return serviceContext.requireAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        serviceContext.setAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        serviceContext.removeAttribute(name);
    }

    @Override
    public IEvalScope getEvalScope() {
        return serviceContext.getEvalScope();
    }

    @Override
    public void registerAsyncResult(Future<Consumer<? extends IExecutionContext>> asyncFuture) {
        serviceContext.registerAsyncResult(asyncFuture);
    }

    @Override
    public boolean hasAsyncResult() {
        return serviceContext.hasAsyncResult();
    }

    @Override
    public void awaitAsyncResults() {
        serviceContext.awaitAsyncResults();
    }

    @Override
    public void cancelAsyncResults() {
        serviceContext.cancelAsyncResults();
    }

    @Override
    public void addBeforeComplete(Consumer<Throwable> callback) {
        serviceContext.addBeforeComplete(callback);
    }

    @Override
    public void addAfterComplete(Consumer<Throwable> callback) {
        serviceContext.addAfterComplete(callback);
    }

    @Override
    public void fireBeforeComplete(Throwable exception) {
        serviceContext.fireBeforeComplete(exception);
    }

    @Override
    public void complete() {
        serviceContext.complete();
    }

    @Override
    public void completeExceptionally(Throwable exception) {
        serviceContext.completeExceptionally(exception);
    }

    @Override
    public Throwable getError() {
        return serviceContext.getError();
    }

    @Override
    public void setError(Throwable error) {
        serviceContext.setError(error);
    }

    @Override
    public List<ErrorBean> getErrorBeans() {
        return serviceContext.getErrorBeans();
    }

    @Override
    public void addErrorBean(ErrorBean error) {
        serviceContext.addErrorBean(error);
    }

    @Override
    public ErrorBean getMostSevereErrorBean() {
        return serviceContext.getMostSevereErrorBean();
    }

    @Override
    public boolean isDone() {
        return serviceContext.isDone();
    }

    @Override
    public boolean isSuccess() {
        return serviceContext.isSuccess();
    }

    @Override
    public Object getRequest() {
        return serviceContext.getRequest();
    }

    @Override
    public void setRequest(Object request) {
        serviceContext.setRequest(request);
    }

    @Override
    public Object getResponse() {
        return serviceContext.getResponse();
    }

    @Override
    public void setResponse(Object response) {
        serviceContext.setResponse(response);
    }

    @Override
    public Map<String, Object> getRequestHeaders() {
        return serviceContext.getRequestHeaders();
    }

    @Override
    public void setRequestHeaders(Map<String, Object> requestHeaders) {
        serviceContext.setRequestHeaders(requestHeaders);
    }

    @Override
    public Object getRequestHeader(String name) {
        return serviceContext.getRequestHeader(name);
    }

    @Override
    public void setRequestHeader(String name, Object value) {
        serviceContext.setRequestHeader(name, value);
    }

    @Override
    public Map<String, Object> getResponseHeaders() {
        return serviceContext.getResponseHeaders();
    }

    @Override
    public void setResponseHeaders(Map<String, Object> headers) {
        serviceContext.setResponseHeaders(headers);
    }

    @Override
    public void clearResponseHeaders() {
        serviceContext.clearResponseHeaders();
    }

    @Override
    public void setResponseHeader(String name, Object value) {
        serviceContext.setResponseHeader(name, value);
    }

    @Override
    public Object getResponseHeader(String name) {
        return serviceContext.getResponseHeader(name);
    }

    @Override
    public IServiceContext newChildContext() {
        return serviceContext.newChildContext();
    }

    @Override
    public IServiceContext getParentContext() {
        return serviceContext.getParentContext();
    }

    @Override
    public ICache<Object, Object> getCache() {
        return serviceContext.getCache();
    }

    @Override
    public void setCache(ICache<Object, Object> cache) {
        serviceContext.setCache(cache);
    }

    @Override
    public IActionAuthChecker getActionAuthChecker() {
        return serviceContext.getActionAuthChecker();
    }

    @Override
    public IDataAuthChecker getDataAuthChecker() {
        return serviceContext.getDataAuthChecker();
    }

    @Override
    public IUserContext getUserContext() {
        return serviceContext.getUserContext();
    }

    @Override
    public IContext getContext() {
        return serviceContext.getContext();
    }

    @Override
    public void setActionAuthChecker(IActionAuthChecker checker) {
        serviceContext.setActionAuthChecker(checker);
    }

    @Override
    public void setDataAuthChecker(IDataAuthChecker checker) {
        serviceContext.setDataAuthChecker(checker);
    }

    @Override
    public void setUserContext(IUserContext userContext) {
        serviceContext.setUserContext(userContext);
    }

    @Override
    public void setContext(IContext context) {
        serviceContext.setContext(context);
    }
}