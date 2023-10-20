/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.lb.impl;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.cluster.lb.ILoadBalance;

import java.util.List;

public class DynamicLoadBalance<T, R> implements ILoadBalance<T, R> {
    private IBeanProvider beanProvider;
    private String beanPrefix = "nopLoadBalance_";
    private String name;

    @Override
    public T choose(List<T> candidates, R request) {
        ILoadBalance<T, R> lb = (ILoadBalance<T, R>) beanProvider.getBean(beanPrefix + name);
        return lb.choose(candidates, request);
    }

    public IBeanProvider getBeanProvider() {
        return beanProvider;
    }

    public void setBeanProvider(IBeanProvider beanProvider) {
        this.beanProvider = beanProvider;
    }

    public String getBeanPrefix() {
        return beanPrefix;
    }

    public void setBeanPrefix(String beanPrefix) {
        this.beanPrefix = beanPrefix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
