/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.ioc.IocErrors.ARG_BEAN_SCOPE;
import static io.nop.ioc.IocErrors.ARG_CONTAINER_ID;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_SCOPE_ALREADY_CLOSED;

public class BeanScopeImpl implements IBeanScope {
    static final Logger LOG = LoggerFactory.getLogger(BeanScopeImpl.class);

    private final Map<String, Object> beans = new ConcurrentHashMap<>();
    private final IEvalScope scope;
    private final String name;

    private final IBeanContainerImplementor container;

    private volatile boolean closed;

    public BeanScopeImpl(String name, IEvalScope scope, IBeanContainerImplementor container) {
        this.name = name;
        this.container = container;
        this.scope = scope.duplicate();
        this.scope.setExtension(new BeanContainerVariableScope(container));
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    public IBeanContainerImplementor getContainer() {
        return container;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return beans.entrySet();
    }

    @Override
    public Object get(String name) {
        checkClosed();
        return beans.get(name);
    }

    @Override
    public void add(String name, Object value) {
        checkClosed();
        beans.put(name, value);
    }

    @Override
    public boolean remove(String name, Object bean) {
        boolean b = beans.remove(name, bean);
        if (b) {
            container.destroyBean(name, bean);
        }
        return b;
    }

    void checkClosed() {
        if (closed)
            throw new NopException(ERR_IOC_BEAN_SCOPE_ALREADY_CLOSED).param(ARG_CONTAINER_ID, container.getId())
                    .param(ARG_BEAN_SCOPE, name);
    }

    @Override
    public void close() {
        closed = true;

        Exception e = null;
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            try {
                remove(beanName, entry.getValue());
            } catch (Exception ex) {
                LOG.error("nop.err.ioc.destroy-bean-fail:beanName={}", beanName, ex);
                e = ex;
            }
        }
        Guard.checkState(beans.isEmpty());
        if (e != null)
            throw NopException.adapt(e);
    }
}