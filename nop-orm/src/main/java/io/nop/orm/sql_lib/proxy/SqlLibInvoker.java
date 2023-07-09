/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.sql_lib.proxy;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ReflectionHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.orm.sql_lib.SqlLibManager;
import io.nop.xlang.api.XLang;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static io.nop.orm.OrmErrors.ARG_METHOD;
import static io.nop.orm.OrmErrors.ERR_SQL_LIB_CONVERT_RETURN_TYPE_FAIL;

public class SqlLibInvoker implements InvocationHandler {
    private final SqlLibManager sqlLibManager;
    private final String sqlLibPath;

    public SqlLibInvoker(SqlLibManager sqlLibManager, String sqlLibPath) {
        this.sqlLibManager = sqlLibManager;
        this.sqlLibPath = sqlLibPath;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            if (methodName.equals("toString")) {
                return this.toString();
            } else if (methodName.equals("equals")) {
                return proxy == args[0];
            } else if (methodName.equals("hashCode")) {
                return System.identityHashCode(proxy);
            } else {
                try {
                    return method.invoke(this, args);
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            }
        }

        if (method.isDefault()) {
            return ReflectionHelper.invokeDefaultMethod(proxy, method, args);
        }

        int contextIndex = findContext(method);

        IEvalContext context = contextIndex >= 0 ? (IEvalContext) args[contextIndex] : null;
        if (context == null) {
            context = XLang.newEvalScope();
        }

        IEvalScope scope = context.getEvalScope().newChildScope();

        LongRangeBean range = null;
        Parameter[] params = method.getParameters();
        for (int i = 0, n = args.length; i < n; i++) {
            if (i == contextIndex)
                continue;

            Parameter param = params[i];
            if (param.getType() == LongRangeBean.class) {
                range = (LongRangeBean) args[i];
            } else {
                String name = ReflectionHelper.getParamName(param);
                scope.setLocalValue(null, name, args[i]);
            }
        }

        Object ret = sqlLibManager.invoke(sqlLibPath, method.getName(), range, scope);

        // 忽略返回值
        if (method.getReturnType() == Void.TYPE || method.getReturnType() == Void.class)
            return null;

        return ConvertHelper.convertTo(method.getReturnType(), ret, err -> {
            return new NopException(ERR_SQL_LIB_CONVERT_RETURN_TYPE_FAIL).param(ARG_METHOD, method);
        });
    }

    private int findContext(Method method) {
        Parameter[] params = method.getParameters();
        for (int i = 0, n = params.length; i < n; i++) {
            if (IEvalContext.class.isAssignableFrom(params[i].getType()))
                return i;
        }
        return -1;
    }
}
