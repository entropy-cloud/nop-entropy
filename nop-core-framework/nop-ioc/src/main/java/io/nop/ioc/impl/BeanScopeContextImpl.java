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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.ioc.IocErrors.ARG_SCOPE;
import static io.nop.ioc.IocErrors.ERR_IOC_SCOPE_NOT_OPENED;

public class BeanScopeContextImpl implements IBeanScopeContext {
    static final Logger LOG = LoggerFactory.getLogger(BeanScopeContextImpl.class);

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
        // C1-005（plan 2549 裁定 A/E 残余面收口）：null→setContextAttr 覆写窗收进 synchronized——
        // 共享 IContext 双线程同时初始化时后者覆写前者（前者已放入的 scope 失联泄漏）。
        // computeIfAbsent 已原子化 per-container 映射，此处仅收口 context attr 初始化半边
        Map<IBeanContainer, BeanContainerScope> containerScopes = getContainerScopes();
        if (containerScopes == null) {
            synchronized (this) {
                containerScopes = getContainerScopes();
                if (containerScopes == null) {
                    containerScopes = new ConcurrentHashMap<>();
                    ContextProvider.setContextAttr(CONTEXT_KEY, containerScopes);
                }
            }
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
        RuntimeException ex = null;
        if (containerScope != null) {
            for (IBeanScope scope : containerScope.scopes.values()) {
                try {
                    scope.close();
                } catch (RuntimeException e2) {
                    LOG.error("nop.err.ioc.container-stop-fail:containerId={}", container.getId(), e2);
                    ex = e2;
                }
            }
            // C6-009（plan 2549 裁定 F）：close 后摘除 scopes 条目 + 清 containerScopes——
            // 原实现只 close 不摘：restart 后自定义 scope 永久 ALREADY_CLOSED + 死容器强引用滞留
            containerScope.scopes.clear();
            Map<IBeanContainer, BeanContainerScope> containerScopes = getContainerScopes();
            if (containerScopes != null) {
                containerScopes.remove(container, containerScope);
            }
        }
        if (ex != null)
            throw ex;
    }
}