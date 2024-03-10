/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.accessor;

import io.nop.commons.collections.IKeyedList;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;

public class KeyedListPropertyAccessor implements IPropertyGetter, IPropertySetter {

    @Override
    public void setProperty(Object obj, String propName, Object value, IEvalScope scope) {
        IKeyedList list = (IKeyedList) obj;
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    @Override
    public Object getProperty(Object bean, String propName, IEvalScope scope) {
        IKeyedList list = (IKeyedList) bean;
        return list.getByKey(propName);
    }
}