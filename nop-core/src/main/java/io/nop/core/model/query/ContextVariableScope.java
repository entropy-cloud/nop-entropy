/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.query;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.util.IVariableScope;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;

import java.util.Set;

public class ContextVariableScope implements IVariableScope {
    public static final ContextVariableScope INSTANCE = new ContextVariableScope();

    private final Set<String> propNames;

    public ContextVariableScope() {
        propNames = ReflectionManager.instance().getBeanModelForClass(IContext.class).getPropertyModels().keySet();
    }

    @Override
    public boolean containsValue(String name) {
        IContext context = ContextProvider.currentContext();
        return propNames.contains(name) || context.getAttribute(name) != null;
    }

    @Override
    public Object getValue(String name) {
        if (name.startsWith("$conf.")) {
            String varName = name.substring("$conf.".length());
            return AppConfig.var(varName);
        }
        IContext context = ContextProvider.currentContext();
        if (context == null)
            return null;

        if (propNames.contains(name))
            return BeanTool.instance().getProperty(context, name);

        int pos = name.indexOf('.');
        if (pos < 0)
            return context.getAttribute(name);

        Object value = context.getAttribute(name.substring(0, pos));
        if (value == null)
            return null;

        return BeanTool.getComplexProperty(value, name.substring(pos + 1));
    }

    @Override
    public Object getValueByPropPath(String propPath) {
        return getValue(propPath);
    }
}