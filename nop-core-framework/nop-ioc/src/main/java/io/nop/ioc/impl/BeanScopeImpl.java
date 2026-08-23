/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.exceptions.NopException;
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

    private final Map<String, ProducedBeanInstance> beans = new ConcurrentHashMap<>();
    private final IEvalScope scope;
    private final String name;

    private final IBeanContainerImplementor container;

    private volatile boolean closed;

    public BeanScopeImpl(String name, IEvalScope scope, IBeanContainerImplementor container) {
        this.name = name;
        this.container = container;
        this.scope = scope;
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
    public Set<Entry<String, ProducedBeanInstance>> entrySet() {
        return beans.entrySet();
    }

    @Override
    public ProducedBeanInstance get(String name) {
        checkClosed();
        return beans.get(name);
    }

    @Override
    public void add(String name, ProducedBeanInstance value) {
        // checkClosed 与 put 原子化：与 close 的 closed 置位互斥，消除
        // "add 通过检查后、put 前 close 完成遍历" 的 check-then-act 窗口
        // （该窗口内 put 的 bean 不会被 close 销毁，且终态校验失败会以裸 ISE 中止 stop）
        synchronized (beans) {
            checkClosed();
            beans.put(name, value);
        }
    }

    @Override
    public boolean remove(String name, ProducedBeanInstance bean) {
        boolean b = beans.remove(name, bean);
        if (b) {
            container.destroyBean(name, bean.getCreatedBean());
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
        // closed 置位与 add 互斥：置位前完成 put 的 bean 必然被本次遍历覆盖到，
        // 置位后 add 直接抛 ERR_IOC_BEAN_SCOPE_ALREADY_CLOSED，不再产生窗口内泄漏
        synchronized (beans) {
            closed = true;
        }

        Exception e = null;
        for (Map.Entry<String, ProducedBeanInstance> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            try {
                remove(beanName, entry.getValue());
            } catch (Exception ex) {
                LOG.error("nop.err.ioc.destroy-bean-fail:beanName={}", beanName, ex);
                e = ex;
            }
        }
        if (!beans.isEmpty())
            throw new NopException(ERR_IOC_BEAN_SCOPE_ALREADY_CLOSED).param(ARG_CONTAINER_ID, container.getId())
                    .param(ARG_BEAN_SCOPE, name);
        if (e != null)
            throw NopException.adapt(e);
    }
}