/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * plan 2517 红绿驱动器测试替身工具：基于 java.lang.reflect.Proxy 的接口桩
 * （nop-orm 测试域无 mockito 依赖）。仅限测试使用。
 */
public final class ProxyStub {
    private ProxyStub() {
    }

    /**
     * 创建接口桩：hashCode/toString/equals 提供缺省实现，其余方法交由 handler；
     * handler 返回 null 时按返回类型补原语缺省值。
     */
    @SuppressWarnings("unchecked")
    public static <T> T of(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return type.getSimpleName() + "$ProxyStub@" + Integer.toHexString(System.identityHashCode(proxy));
                case "equals":
                    return proxy == args[0];
                default:
            }
            Object ret = handler.invoke(proxy, method, args);
            if (ret == null && method.getReturnType().isPrimitive()) {
                if (method.getReturnType() == boolean.class)
                    return Boolean.FALSE;
                if (method.getReturnType() == long.class)
                    return 0L;
                if (method.getReturnType() == int.class)
                    return 0;
                return (byte) 0;
            }
            return ret;
        });
    }
}
