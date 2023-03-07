/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFieldModel;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;

import java.lang.annotation.Annotation;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_PARAM_NAME;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_READ_PROP_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_UNKNOWN_PROP;
import static io.nop.xlang.XLangErrors.ERR_EXEC_UNKNOWN_STATIC_FIELD;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_PROP_FAIL;

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
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        if (clazz == Class.class) {
            return getStaticFieldSetter(clazz);
        }

        IBeanPropertyModel field = beanModel.getPropertyModel(propName);
        if (field == null) {
            if (beanModel.isAllowSetExtProperty()) {
                return beanModel.getExtPropertySetter();
            }
            throw newError(ERR_EXEC_UNKNOWN_PROP).param(ARG_CLASS_NAME, clazz.getName()).param(ARG_PARAM_NAME,
                    propName);
        }
        return field.getSetter();
    }

    protected void setProp(Object obj, Object value, IPropertySetter setter, IEvalScope scope) {
        try {
            setter.setProperty(obj, propName, value, scope);
        } catch (Exception e) {
            throw newError(ERR_EXEC_WRITE_PROP_FAIL, e).forWrap().param(ARG_CLASS_NAME, obj.getClass().getName())
                    .param(ARG_PROP_NAME, propName).forWrap();
        }
    }

    protected IPropertySetter getStaticFieldSetter(Class<?> clazz) {
        IClassModel classModel = ReflectionManager.instance().getClassModel(clazz);
        IFieldModel field = classModel.getStaticField(propName);
        if (field == null)
            throw newError(ERR_EXEC_UNKNOWN_STATIC_FIELD).param(ARG_CLASS_NAME, clazz.getName()).param(ARG_PARAM_NAME,
                    propName);
        return field.getSetter();
    }

    protected IPropertyGetter getStaticFieldGetter(Class<?> clazz) {
        IClassModel classModel = ReflectionManager.instance().getClassModel(clazz);
        IFieldModel field = classModel.getStaticField(propName);
        if (field == null)
            throw newError(ERR_EXEC_UNKNOWN_STATIC_FIELD).param(ARG_CLASS_NAME, clazz.getName()).param(ARG_PARAM_NAME,
                    propName);
        return field.getGetter();
    }

    protected IPropertyGetter getGetter(Class<?> clazz, Object bean) {
        if (bean instanceof Annotation)
            clazz = ((Annotation) bean).annotationType();

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        if (clazz == Class.class)
            return getStaticFieldGetter(clazz);
        IBeanPropertyModel field = beanModel.getPropertyModel(propName);
        if (field == null) {
            if (beanModel.isAllowGetExtProperty()) {
                return beanModel.getExtPropertyGetter();
            }
            throw newError(ERR_EXEC_UNKNOWN_PROP).param(ARG_CLASS_NAME, clazz.getName()).param(ARG_PROP_NAME, propName);
        }
        return field.getGetter();
    }

    protected Object readProp(Object obj, IPropertyGetter reader, IEvalScope scope) {
        try {
            return reader.getProperty(obj, propName, scope);
        } catch (Exception e) {
            throw newError(ERR_EXEC_READ_PROP_FAIL, e).forWrap().param(ARG_CLASS_NAME, obj.getClass().getName())
                    .param(ARG_PROP_NAME, propName);
        }
    }
}
