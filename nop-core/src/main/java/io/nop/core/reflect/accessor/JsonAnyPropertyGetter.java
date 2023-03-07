/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IExtPropertyGetter;
import io.nop.core.reflect.IFunctionModel;

import java.util.Map;
import java.util.Set;

public class JsonAnyPropertyGetter implements IExtPropertyGetter {
    private final IFunctionModel method;

    public JsonAnyPropertyGetter(IFunctionModel method) {
        this.method = method;
    }

    public IFunctionModel getMethod() {
        return method;
    }

    @Override
    public Set<String> getExtPropNames(Object obj) {
        Map<String, Object> map = (Map<String, Object>) method.call0(obj, DisabledEvalScope.INSTANCE);
        if (map == null)
            return null;
        return map.keySet();
    }

    @Override
    public boolean isAllowExtProperty(Object obj, String name) {
        Map<String, Object> map = (Map<String, Object>) method.call0(obj, DisabledEvalScope.INSTANCE);
        if (map == null)
            return false;
        return map.containsKey(name);
    }

    @Override
    public Object getProperty(Object bean, String propName, IEvalScope scope) {
        Map<String, Object> map = (Map<String, Object>) method.call0(bean, scope);
        if (map == null)
            return null;
        return map.get(propName);
    }
}