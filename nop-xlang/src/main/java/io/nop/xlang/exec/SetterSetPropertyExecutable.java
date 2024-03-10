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
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.IPropertySetter;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_PROP_FAIL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_PROP_OBJ_NULL;

public class SetterSetPropertyExecutable extends AbstractExecutable {
    private final IExecutableExpression objExpr;
    private final String propName;
    private final IExecutableExpression valueExpr;
    private final IPropertySetter setter;

    public SetterSetPropertyExecutable(SourceLocation loc, IExecutableExpression objExpr, String propName,
                                       IExecutableExpression valueExpr, IPropertySetter setter) {
        super(loc);
        this.objExpr = Guard.notNull(objExpr, "objExpr is null");
        this.propName = Guard.notNull(propName, "propName is null");
        this.valueExpr = Guard.notNull(valueExpr, "valueExpr is null");
        this.setter = Guard.notNull(setter, "setter is null");
    }

    public IExecutableExpression getObjExpr() {
        return objExpr;
    }

    public String getPropName() {
        return propName;
    }

    @Override
    public void display(StringBuilder sb) {
        objExpr.display(sb);
        sb.append('.');
        sb.append(propName);
        sb.append(" = ");
        valueExpr.display(sb);
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object o = executor.execute(objExpr, scope);
        if (o == null)
            throw newError(ERR_EXEC_WRITE_PROP_OBJ_NULL);

        Object value = executor.execute(valueExpr, scope);

        setProp(o, value, setter, scope);
        return value;
    }

    protected void setProp(Object obj, Object value, IPropertySetter setter, IEvalScope scope) {
        try {
            setter.setProperty(obj, propName, value, scope);
        } catch (Exception e) {
            throw newError(ERR_EXEC_WRITE_PROP_FAIL, e).param(ARG_CLASS_NAME, obj.getClass().getName())
                    .param(ARG_PROP_NAME, propName).forWrap();
        }
    }
}
