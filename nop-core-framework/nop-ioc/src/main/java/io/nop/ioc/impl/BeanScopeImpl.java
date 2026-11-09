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
import io.nop.ioc.api.IBeanDefinition;
import io.nop.ioc.api.IBeanScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * C4-010：经容器定义查 beanTopoIndex（含 parent 回溯）；查不到定义 WARN 并返回 0（哈希序兜底）。
     */
    private int topoIndexOf(String beanName) {
        IBeanDefinition def = container.getBeanDefinition(beanName);
        if (def instanceof BeanDefinition) {
            return ((BeanDefinition) def).getBeanTopoIndex();
        }
        LOG.warn("nop.warn.ioc.scope-destroy-no-definition:scope={},bean={}", name, beanName);
        return 0;
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

        // C4-010（plan 2549 裁定 F，←并 C6-006）：销毁序 = beanTopoIndex 降序（依赖方先于被依赖方销毁）
        // ——创建有拓扑契约、销毁无序会使 destroy 运行于依赖已销毁之后。查不到定义（理论面）WARN+哈希序兜底
        List<Map.Entry<String, ProducedBeanInstance>> entries = new ArrayList<>(beans.entrySet());
        entries.sort((a, b) -> {
            int ia = topoIndexOf(a.getKey());
            int ib = topoIndexOf(b.getKey());
            return Integer.compare(ib, ia);
        });

        Exception e = null;
        for (Map.Entry<String, ProducedBeanInstance> entry : entries) {
            String beanName = entry.getKey();
            try {
                remove(beanName, entry.getValue());
            } catch (Exception ex) {
                // C6-007（plan 2549 裁定 F）：逐 bean 异常收集后统一汇总（不中断其余 bean 销毁），
                // 口径 catch Exception→Throwable 由 remove/destroyBean 内部保持
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