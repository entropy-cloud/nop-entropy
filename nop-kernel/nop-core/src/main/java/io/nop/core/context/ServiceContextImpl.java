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
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.core.CoreConstants;

import java.util.Map;

public class ServiceContextImpl extends ExecutionContextImpl implements IServiceContext {
    private IServiceContext parentContext;

    private Map<String, Object> requestHeaders;
    private Map<String, Object> responseHeaders;

    private ICache<Object, Object> cache;

    private IDataAuthChecker dataAuthChecker;

    private IActionAuthChecker actionAuthChecker;

    private IUserContext userContext = IUserContext.get();

    private IContext context = ContextProvider.getOrCreateContext();

    public ServiceContextImpl() {
        getEvalScope().setLocalValue(null, CoreConstants.VAR_SVC_CTX, this);
    }

    public ServiceContextImpl(Map<String, Object> vars) {
        super(vars);
        getEvalScope().setLocalValue(null, CoreConstants.VAR_SVC_CTX, this);
    }

    @Override
    public IServiceContext getParentContext() {
        return parentContext;
    }

    public void setParentContext(IServiceContext parentContext) {
        this.parentContext = parentContext;
    }

    @Override
    public synchronized Object getRequest() {
        return getAttribute(CoreConstants.VAR_REQUEST);
    }

    @Override
    public synchronized void setRequest(Object request) {
        setAttribute(CoreConstants.VAR_REQUEST, request);
    }

    @Override
    public synchronized Object getRequestHeader(String name) {
        return IServiceContext.super.getRequestHeader(name);
    }

    @Override
    public synchronized void setRequestHeader(String name, Object value) {
        IServiceContext.super.setRequestHeader(name, value);
    }

    @Override
    public synchronized void clearResponseHeaders() {
        IServiceContext.super.clearResponseHeaders();
    }

    @Override
    public synchronized void setResponseHeader(String name, Object value) {
        IServiceContext.super.setResponseHeader(name, value);
    }

    @Override
    public synchronized Object getResponseHeader(String name) {
        return IServiceContext.super.getResponseHeader(name);
    }

    @Override
    public synchronized Object getResponse() {
        return getAttribute(CoreConstants.VAR_RESPONSE);
    }

    @Override
    public synchronized void setResponse(Object response) {
        setAttribute(CoreConstants.VAR_RESPONSE, response);
    }

    @Override
    public synchronized Map<String, Object> getRequestHeaders() {
        return requestHeaders;
    }

    @Override
    public synchronized void setRequestHeaders(Map<String, Object> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    @Override
    public synchronized Map<String, Object> getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public synchronized void setResponseHeaders(Map<String, Object> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    @Override
    public IServiceContext newChildContext() {
        ServiceContextImpl context = newServiceContextImpl();
        context.setParentContext(this);
        context.setActionAuthChecker(actionAuthChecker);
        context.setDataAuthChecker(dataAuthChecker);
        context.setCache(cache);
        context.setUserContext(userContext);
        context.setContext(this.context);
        return context;
    }

    protected ServiceContextImpl newServiceContextImpl() {
        return new ServiceContextImpl();
    }

    @Override
    public synchronized ICache<Object, Object> getCache() {
        if (cache == null) {
            cache = new MapCache<>("service-cache", true);
        }
        return cache;
    }

    @Override
    public synchronized void setCache(ICache<Object, Object> cache) {
        this.cache = cache;
    }

    @Override
    public IDataAuthChecker getDataAuthChecker() {
        return dataAuthChecker;
    }

    public void setDataAuthChecker(IDataAuthChecker dataAuthChecker) {
        this.dataAuthChecker = dataAuthChecker;
    }

    @Override
    public IActionAuthChecker getActionAuthChecker() {
        return actionAuthChecker;
    }

    public void setActionAuthChecker(IActionAuthChecker actionAuthChecker) {
        this.actionAuthChecker = actionAuthChecker;
    }

    @Override
    public IUserContext getUserContext() {
        return userContext;
    }

    public void setUserContext(IUserContext userContext) {
        this.userContext = userContext;
    }

    @Override
    public IContext getContext() {
        return context;
    }

    public void setContext(IContext context) {
        this.context = context;
    }
}
