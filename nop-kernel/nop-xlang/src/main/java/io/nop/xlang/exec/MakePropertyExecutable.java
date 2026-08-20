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


import static io.nop.xlang.XLangErrors.ARG_OBJ_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_MAKE_PROP_OBJ_NULL;

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
        return XLangSemantics.getMakerGetter(getLocation(), display(), getPropName(), bean);
    }

    @Override
    protected Object readProp(Object obj, IPropertyGetter reader, IEvalScope scope) {
        return XLangSemantics.readMakerPropValue(getLocation(), display(), getPropName(), obj, reader, scope);
    }
}
