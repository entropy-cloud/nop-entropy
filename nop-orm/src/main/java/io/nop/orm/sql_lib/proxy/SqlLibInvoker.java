package io.nop.orm.sql_lib.proxy;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ReflectionHelper;
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

        IEvalScope context = XLang.newEvalScope();
        LongRangeBean range = null;
        Parameter[] params = method.getParameters();
        for (int i = 0, n = args.length; i < n; i++) {
            Parameter param = params[i];
            if (param.getType() == LongRangeBean.class) {
                range = (LongRangeBean) args[i];
            } else {
                String name = ReflectionHelper.getParamName(param);
                context.setLocalValue(null, name, args[i]);
            }
        }

        Object ret = sqlLibManager.invoke(sqlLibPath, method.getName(), range, context);
        return ConvertHelper.convertTo(method.getReturnType(), ret, err -> {
            return new NopException(ERR_SQL_LIB_CONVERT_RETURN_TYPE_FAIL).param(ARG_METHOD, method);
        });
    }
}
