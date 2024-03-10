/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.aop;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.core.context.IServiceContext;
import io.nop.core.exceptions.ErrorMessageManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class AopProxyHelper {

    public static Object invokeObjectMethod(InvocationHandler handler, Object proxy, Method method, Object[] args)
            throws Throwable {
        String methodName = method.getName();
        if (methodName.equals("equals")) {
            // Only consider equal when proxies are identical.
            return (proxy == args[0]);
        } else if (methodName.equals("hashCode")) {
            return System.identityHashCode(proxy);
        } else if (methodName.equals("toString")) {
            Class<?>[] interfaces = proxy.getClass().getInterfaces();
            if (interfaces.length == 0) {
                return "Proxy[" + handler + "]";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Proxy[").append(handler);
            sb.append(",interfaces=");
            for (Class<?> inf : interfaces) {
                sb.append(inf.getTypeName());
                sb.append(',');
            }
            sb.append("]");
            return sb.toString();
        }
        return method.invoke(proxy, args);
    }

    public static ApiResponse<?> buildResponse(Object ret, Throwable e, IServiceContext ctx) {
        if (e != null) {
            String locale = ContextProvider.currentLocale();
            ApiResponse<?> res = ErrorMessageManager.instance().buildResponse(locale, e);
            if (ctx != null) {
                res.setHeaders(ctx.getResponseHeaders());
            }
            return res;
        }

        if (ret instanceof ApiResponse<?>)
            return (ApiResponse<?>) ret;

        ApiResponse<Object> res = ApiResponse.buildSuccess(ret);
        res.setHeaders(ctx.getResponseHeaders());
        return res;
    }
}
