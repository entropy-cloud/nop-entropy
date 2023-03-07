/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.bean.IBeanConstructor;

public class DefaultPropertyMaker implements IPropertyGetter {
    private final IPropertyGetter getter;
    private final IPropertySetter setter;
    private final IBeanConstructor constructor;

    public DefaultPropertyMaker(IPropertyGetter getter, IPropertySetter setter, IBeanConstructor constructor) {
        this.getter = getter;
        this.setter = setter;
        this.constructor = constructor;
    }

    @Override
    public Object getProperty(Object obj, String propName, IEvalScope scope) {
        Object value = getter.getProperty(obj, propName, scope);
        if (value != null)
            return value;

        value = constructor.newInstance();
        setter.setProperty(obj, propName, value, scope);
        return value;
    }
}
