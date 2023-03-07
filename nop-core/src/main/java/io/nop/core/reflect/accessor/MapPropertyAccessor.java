/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IExtPropertyGetter;
import io.nop.core.reflect.IPropertySetter;

import java.util.Map;
import java.util.Set;

public class MapPropertyAccessor implements IExtPropertyGetter, IPropertySetter {
    public static final MapPropertyAccessor INSTANCE = new MapPropertyAccessor();

    @Override
    public Set<String> getExtPropNames(Object obj) {
        return ((Map<String, ?>) obj).keySet();
    }

    @Override
    public boolean isAllowExtProperty(Object obj, String name) {
        return ((Map<String, ?>) obj).containsKey(name);
    }

    @Override
    public Object getProperty(Object bean, String propName, IEvalScope scope) {
        return ((Map<?, ?>) bean).get(propName);
    }

    @Override
    public void setProperty(Object bean, String propName, Object value, IEvalScope scope) {
        ((Map<Object, Object>) bean).put(propName, value);
    }
}
