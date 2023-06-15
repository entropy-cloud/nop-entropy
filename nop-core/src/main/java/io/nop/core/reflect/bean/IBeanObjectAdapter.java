/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.bean;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalScope;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public interface IBeanObjectAdapter {
    // void forEachReadableProperty(Object bean, boolean onlySerializable, Consumer<String> action);

    Object getProperty(Object bean, String propName, IEvalScope scope);

    default Object getProperty(Object bean, String propName) {
        return getProperty(bean, propName, DisabledEvalScope.INSTANCE);
    }

    default Object makeProperty(Object bean, String propName) {
        return makeProperty(bean, propName, DisabledEvalScope.INSTANCE);
    }

    default void setProperty(Object bean, String propName, Object value) {
        setProperty(bean, propName, value, DisabledEvalScope.INSTANCE);
    }

    default Object makeProperty(Object bean, String propName, IEvalScope scope) {
        return getProperty(bean, propName, scope);
    }

    void setProperty(Object bean, String propName, Object value, IEvalScope scope);

    boolean hasProperty(Object bean, String propName);

    default void setProperties(Object bean, Map<String, Object> values) {
        setProperties(bean, values, DisabledEvalScope.INSTANCE);
    }

    default void setProperties(Object bean, Map<String, Object> props, IEvalScope scope) {
        if (props != null) {
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                setProperty(bean, entry.getKey(), entry.getValue(), scope);
            }
        }
    }

    default Map<String, Object> getProperties(Object bean, Collection<String> propNames) {
        return getProperties(bean, propNames, DisabledEvalScope.INSTANCE);
    }

    default Map<String, Object> getProperties(Object bean, Collection<String> propNames, IEvalScope scope) {
        if (propNames == null || propNames.isEmpty())
            return Collections.emptyMap();

        Map<String, Object> ret = new LinkedHashMap<>(propNames.size());
        for (String propName : propNames) {
            Object value = getProperty(bean, propName, scope);
            ret.put(propName, value);
        }
        return ret;
    }
}
