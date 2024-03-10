/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.IPropertyGetter;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_OBJ_EXPR;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_GET_PROP_ON_NULL_OBJ;
import static io.nop.xlang.XLangErrors.ERR_EXEC_NOT_READABLE_PROP;

public class GetPropertyExecutable extends AbstractPropertyExecutable {
    private final IExecutableExpression objExpr;

    public GetPropertyExecutable(SourceLocation loc, IExecutableExpression objExpr, boolean optional, String propName) {
        super(loc, propName, optional);
        this.objExpr = objExpr;
    }

    public IExecutableExpression getObjExpr() {
        return objExpr;
    }

    public boolean isOptional() {
        return optional;
    }

    public String getPropName() {
        return propName;
    }

    @Override
    public void display(StringBuilder sb) {
        objExpr.display(sb);
        if (optional)
            sb.append('?');
        sb.append('.');
        sb.append(propName);
    }

    protected Object returnNull() {
        return null;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object o = eval(objExpr, executor, scope);
        if (o == null) {
            if (!optional)
                throw newError(ERR_EXEC_GET_PROP_ON_NULL_OBJ)
                        .param(ARG_PROP_NAME, propName).param(ARG_OBJ_EXPR, objExpr.display());
            return returnNull();
        }

        IPropertyGetter reader = getGetterWithCache(o);

        return readProp(o, reader, scope);
    }

    private transient Pair<Class<?>, IPropertyGetter> _cacheGetter = Pair.of(null, null);

    IPropertyGetter getGetterWithCache(Object bean) {
        IPropertyGetter reader;

        Class<?> clazz = bean.getClass();

        Pair<Class<?>, IPropertyGetter> pair = _cacheGetter;
        if (pair.getLeft() == clazz) {
            reader = pair.getRight();
        } else {
            reader = getGetter(clazz, bean);
            if (reader == null)
                throw newError(ERR_EXEC_NOT_READABLE_PROP).param(ARG_CLASS_NAME, clazz.getName()).param(ARG_PROP_NAME,
                        propName);
            _cacheGetter = Pair.of(clazz, reader);
        }

        return reader;
    }
}