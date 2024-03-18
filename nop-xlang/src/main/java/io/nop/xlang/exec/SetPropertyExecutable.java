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
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.IPropertySetter;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_NOT_WRITABLE_PROP;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_PROP_OBJ_NULL;

/**
 * 设置对象属性
 */
public class SetPropertyExecutable extends AbstractPropertyExecutable {
    private final IExecutableExpression objExpr;
    private final IExecutableExpression valueExpr;

    public SetPropertyExecutable(SourceLocation loc, IExecutableExpression objExpr, String propName,
                                 IExecutableExpression valueExpr) {
        super(loc, propName, false);
        this.objExpr = Guard.notNull(objExpr, "objExpr");
        this.valueExpr = Guard.notNull(valueExpr, "valueExpr");
    }

    public IExecutableExpression getObjExpr() {
        return objExpr;
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
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object o = executor.execute(objExpr, rt);
        if (o == null) {
            throw newError(ERR_EXEC_WRITE_PROP_OBJ_NULL);
        }

        Object value = executor.execute(valueExpr, rt);

        Class<?> clazz = o.getClass();
        IPropertySetter setter = getSetterWithCache(clazz);

        setProp(o, value, setter, rt.getScope());
        return value;
    }

    // tell cpd to start ignoring code - CPD-OFF
    private transient Pair<Class<?>, IPropertySetter> _cacheSetter = Pair.of(null, null);

    IPropertySetter getSetterWithCache(Class<?> clazz) {
        IPropertySetter setter;
        Pair<Class<?>, IPropertySetter> pair = _cacheSetter;
        if (pair.getLeft() == clazz) {
            setter = pair.getRight();
        } else {
            setter = getSetter(clazz);
            if (setter == null)
                throw newError(ERR_EXEC_NOT_WRITABLE_PROP).param(ARG_CLASS_NAME, clazz.getName()).param(ARG_PROP_NAME,
                        propName);
            _cacheSetter = Pair.of(clazz, setter);
        }
        return setter;
    }
    // resume CPD analysis - CPD-ON
}
