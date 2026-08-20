/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;



public abstract class AbstractPropertyExecutable extends AbstractExecutable {
    protected final String propName;
    protected final boolean optional;

    public AbstractPropertyExecutable(SourceLocation loc, String propName, boolean optional) {
        super(loc);
        this.propName = Guard.notEmpty(propName, "propName");
        this.optional = optional;
    }

    public String getPropName() {
        return propName;
    }

    protected IPropertySetter getSetter(Class<?> clazz) {
        return XLangSemantics.getPropSetter(getLocation(), display(), propName, clazz);
    }

    protected void setProp(Object obj, Object value, IPropertySetter setter, IEvalScope scope) {
        XLangSemantics.writePropValue(getLocation(), display(), propName, obj, value, setter, scope);
    }

    protected IPropertySetter getStaticFieldSetter(Class<?> clazz) {
        return XLangSemantics.getStaticFieldSetter(getLocation(), display(), propName, clazz);
    }

    protected IPropertyGetter getStaticFieldGetter(Class<?> clazz) {
        return XLangSemantics.getStaticFieldGetter(getLocation(), display(), propName, clazz);
    }

    protected IPropertyGetter getGetter(Class<?> clazz, Object bean) {
        return XLangSemantics.getPropGetter(getLocation(), display(), propName, clazz, bean);
    }

    protected Object readProp(Object obj, IPropertyGetter reader, IEvalScope scope) {
        return XLangSemantics.readPropValue(getLocation(), display(), propName, obj, reader, scope);
    }
}
