/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.accessor;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 根据指定属性从List中过滤出一个元素
 */
public class FilterListPropertyAccessor implements ISpecializedPropertyGetter, ISpecializedPropertySetter {
    private final ISpecializedPropertyGetter getter;
    private final ISpecializedPropertySetter setter;
    private final String filterValue;

    public FilterListPropertyAccessor(ISpecializedPropertyGetter getter, ISpecializedPropertySetter setter,
                                      String filterValue) {
        this.getter = getter;
        this.setter = setter;
        this.filterValue = filterValue;
    }

    @Override
    public Object getProperty(Object obj, String ignored, IEvalScope scope) {
        Collection<?> c = (Collection<?>) obj;
        for (Object o : c) {
            Object v = getter.getProperty(o, null, scope);
            if (v == null)
                continue;
            if (Objects.equals(filterValue, StringHelper.toString(v, null)))
                return o;
        }
        return null;
    }

    @Override
    public void setProperty(Object obj, String ignored, Object value, IEvalScope scope) {
        Object current = getter.getProperty(value, null, scope);
        if (current == null && this.filterValue != null) {
            setter.setProperty(value, null, this.filterValue, scope);
        }

        List<Object> list = (List<Object>) obj;
        for (int i = 0, n = list.size(); i < n; i++) {
            Object o = list.get(i);
            Object v = getter.getProperty(o, null, scope);
            if (Objects.equals(value, StringHelper.toString(v, null))) {
                list.set(i, value);
                return;
            }
        }
        list.add(value);
    }
}
