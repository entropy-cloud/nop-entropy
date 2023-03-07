/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.IPropertyGetter;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_READ_PROP_FAIL;

public class GetterGetPropertyExecutable extends AbstractExecutable {
    private final IExecutableExpression objExpr;
    private final String propName;
    private final IPropertyGetter getter;

    public GetterGetPropertyExecutable(SourceLocation loc, IExecutableExpression objExpr, String propName,
                                       IPropertyGetter getter) {
        super(loc);
        this.objExpr = objExpr;
        this.propName = propName;
        this.getter = getter;
    }

    @Override
    public void display(StringBuilder sb) {
        objExpr.display(sb);
        sb.append('.');
        sb.append(propName);
    }

    protected Object returnNull() {
        return null;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object o = eval(objExpr, executor, scope);
        if (o == null)
            return returnNull();

        return readProp(o, getter, scope);
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
