/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.api.IBeanScopeContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.ioc.IocErrors.ARG_SCOPE;
import static io.nop.ioc.IocErrors.ERR_IOC_SCOPE_NOT_OPENED;

public class BeanScopeContextImpl implements IBeanScopeContext {
    // static final Logger LOG = LoggerFactory.getLogger(BeanScopeContextImpl.class);

    static final String CONTEXT_KEY = BeanContainerScope.class.getSimpleName();

    static class BeanContainerScope {
        Map<String, IBeanScope> scopes = new ConcurrentHashMap<>();
    }

    private Map<IBeanContainer, BeanContainerScope> getContainerScopes() {
        Map<IBeanContainer, BeanContainerScope> containerScopes = (Map<IBeanContainer, BeanContainerScope>) ContextProvider
                .getContextAttr(CONTEXT_KEY);
        return containerScopes;
    }

    private BeanContainerScope makeContainerScope(IBeanContainer container) {
        Map<IBeanContainer, BeanContainerScope> containerScopes = getContainerScopes();
        if (containerScopes == null) {
            containerScopes = new ConcurrentHashMap<>();
            ContextProvider.setContextAttr(CONTEXT_KEY, containerScopes);
        }
        return containerScopes.computeIfAbsent(container, k -> new BeanContainerScope());
    }

    private BeanContainerScope getContainerScope(IBeanContainer container) {
        Map<IBeanContainer, BeanContainerScope> containerScopes = getContainerScopes();
        return containerScopes == null ? null : containerScopes.get(container);
    }

    @Override
    public IBeanScope newBeanScope(IBeanContainer container, String scopeName, IEvalScope evalScope) {
        return new BeanScopeImpl(scopeName, evalScope, (IBeanContainerImplementor) container);
    }

    @Override
    public void bind(IBeanScope scope) {
        BeanContainerScope containerScope = makeContainerScope(scope.getContainer());
        containerScope.scopes.put(scope.getName(), scope);
    }

    @Override
    public void unbind(IBeanScope scope) {
        BeanContainerScope containerScope = getContainerScope(scope.getContainer());
        if (containerScope != null)
            containerScope.scopes.remove(scope.getName(), scope);
    }

    @Override
    public IBeanScope getScope(IBeanContainerImplementor container, String scopeName) {
        BeanContainerScope containerScope = getContainerScope(container);
        IBeanScope beanScope = containerScope == null ? null : containerScope.scopes.get(scopeName);
        if (beanScope == null)
            throw new NopException(ERR_IOC_SCOPE_NOT_OPENED).param(ARG_SCOPE, scopeName);
        return beanScope;
    }

    @Override
    public void onContainerStop(IBeanContainerImplementor container) {
        BeanContainerScope containerScope = getContainerScope(container);
        if (containerScope != null) {
            for (IBeanScope scope : containerScope.scopes.values()) {
                scope.close();
            }
        }
    }
}