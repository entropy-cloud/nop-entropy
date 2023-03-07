/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.bean;

import io.nop.core.lang.eval.IEvalScope;

import java.util.Map;

public class MapBeanObjectAdapter implements IBeanObjectAdapter {
    public static MapBeanObjectAdapter INSTANCE = new MapBeanObjectAdapter();

    // @Override
    // public void forEachReadableProperty(Object bean, boolean onlySerializable, Consumer<String> action) {
    // ((Map<String, Object>) bean).keySet().forEach(action);
    // }

    @Override
    public Object getProperty(Object bean, String propName, IEvalScope scope) {
        return ((Map<String, Object>) bean).get(propName);
    }

    @Override
    public void setProperty(Object bean, String propName, Object value, IEvalScope scope) {
        ((Map<String, Object>) bean).put(propName, value);
    }
}
