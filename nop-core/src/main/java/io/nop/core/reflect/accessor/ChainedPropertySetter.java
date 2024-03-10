/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.accessor;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.core.lang.eval.IEvalScope;

import static io.nop.core.CoreErrors.ARG_BEAN;
import static io.nop.core.CoreErrors.ARG_PROP_NAME;
import static io.nop.core.CoreErrors.ERR_REFLECT_SET_PROP_ON_NULL_OBJ;

public class ChainedPropertySetter implements ISpecializedPropertySetter {
    private final ISpecializedPropertyGetter owner;
    private final ISpecializedPropertySetter setter;

    public ChainedPropertySetter(ISpecializedPropertyGetter owner, ISpecializedPropertySetter setter) {
        this.owner = Guard.notNull(owner, "owner");
        this.setter = Guard.notNull(setter, "setter");
    }

    @Override
    public void setProperty(Object obj, String propName, Object value, IEvalScope scope) {
        Object bean = owner.getProperty(obj, null, scope);
        if (bean == null)
            throw new NopException(ERR_REFLECT_SET_PROP_ON_NULL_OBJ).param(ARG_PROP_NAME, propName).param(ARG_BEAN,
                    obj);
        setter.setProperty(bean, null, value, scope);
    }
}