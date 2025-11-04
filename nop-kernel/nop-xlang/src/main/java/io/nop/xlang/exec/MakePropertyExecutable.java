/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;

import java.lang.annotation.Annotation;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_OBJ_EXPR;
import static io.nop.xlang.XLangErrors.ARG_PARAM_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_MAKE_PROP_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_MAKE_PROP_OBJ_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_UNKNOWN_PROP;

public class MakePropertyExecutable extends GetPropertyExecutable {

    public MakePropertyExecutable(SourceLocation loc, IExecutableExpression objExpr, String propName) {
        super(loc, objExpr,false, propName);
    }

    @Override
    protected Object returnNull() {
        throw newError(ERR_EXEC_MAKE_PROP_OBJ_NULL).param(ARG_OBJ_EXPR, getObjExpr().display());
    }

    @Override
    protected IPropertyGetter getGetter(Class clazz, Object bean) {
        if (bean instanceof Annotation)
            clazz = ((Annotation) bean).annotationType();

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        IBeanPropertyModel field = beanModel.getPropertyModel(getPropName());
        if (field == null) {
            if (beanModel.isAllowGetExtProperty()) {
                return beanModel.getExtPropertyGetter();
            }
            throw newError(ERR_EXEC_UNKNOWN_PROP).param(ARG_CLASS_NAME, clazz.getName()).param(ARG_PARAM_NAME,
                    getPropName());
        }
        return field.getMaker();
    }

    protected Object readProp(Object obj, IPropertyGetter reader, IEvalScope scope) {
        Object value = super.readProp(obj, reader, scope);
        if (value == null)
            throw newError(ERR_EXEC_MAKE_PROP_NULL);
        return value;
    }
}
