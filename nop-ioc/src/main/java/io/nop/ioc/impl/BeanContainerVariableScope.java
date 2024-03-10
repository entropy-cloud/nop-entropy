/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.util.IVariableScope;
import io.nop.core.reflect.bean.BeanTool;

public class BeanContainerVariableScope implements IVariableScope {
    private final IBeanContainer container;

    public BeanContainerVariableScope(IBeanContainer container) {
        this.container = container;
    }

    @Override
    public boolean containsValue(String name) {
        return container.containsBean(name);
    }

    @Override
    public Object getValue(String name) {
        return container.getBean(name);
    }

    @Override
    public Object getValueByPropPath(String propPath) {
        int pos = propPath.indexOf('.');
        if (pos < 0)
            return getValue(propPath);
        Object o = getValue(propPath.substring(0, pos));
        if (o == null)
            return null;
        return BeanTool.getComplexProperty(o, propPath.substring(pos + 1));
    }
}