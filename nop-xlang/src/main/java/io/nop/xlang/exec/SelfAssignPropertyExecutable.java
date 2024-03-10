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
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.xlang.ast.XLangOperator;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_NOT_READABLE_PROP;
import static io.nop.xlang.XLangErrors.ERR_EXEC_NOT_WRITABLE_PROP;
import static io.nop.xlang.XLangErrors.ERR_EXEC_OBJ_PROP_IS_NULL;
import static io.nop.xlang.XLangErrors.ERR_EXEC_WRITE_PROP_OBJ_NULL;

/**
 * 设置对象属性
 */
public class SelfAssignPropertyExecutable extends AbstractPropertyExecutable {
    private final IExecutableExpression objExpr;
    private final XLangOperator operator;
    private final IExecutableExpression valueExpr;

    public SelfAssignPropertyExecutable(SourceLocation loc, IExecutableExpression objExpr, String propName,
                                        XLangOperator operator, IExecutableExpression valueExpr) {
        super(loc, propName, false);
        this.objExpr = Guard.notNull(objExpr, "objExpr");
        this.operator = Guard.notNull(operator, "operator");
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
        sb.append(" ").append(operator).append(' ');
        valueExpr.display(sb);
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object o = executor.execute(objExpr, scope);
        if (o == null) {
            throw newError(ERR_EXEC_WRITE_PROP_OBJ_NULL);
        }

        Object value = executor.execute(valueExpr, scope);

        Class<?> clazz = o.getClass();
        IPropertyGetter getter = getGetterWithCache(clazz, o);
        Object oldValue = readProp(o, getter, scope);
        if (oldValue == null)
            throw newError(ERR_EXEC_OBJ_PROP_IS_NULL).param(ARG_PROP_NAME, getPropName());

        Object newValue = selfAssignValue(operator, oldValue, value);
        IPropertySetter setter = getSetterWithCache(clazz);

        setProp(o, newValue, setter, scope);
        return newValue;
    }

    private transient Pair<Class<?>, IPropertyGetter> _cacheGetter = Pair.of(null, null);

    IPropertyGetter getGetterWithCache(Class<?> clazz, Object bean) {
        IPropertyGetter reader;

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

}
