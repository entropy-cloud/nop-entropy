/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.commons.util;

import io.nop.api.core.annotations.core.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;

public class ReflectionHelper {
    static final Logger LOG = LoggerFactory.getLogger(ReflectionHelper.class);

    /**
     * Determine whether the given method is an "equals" method.
     *
     * @see java.lang.Object#equals(Object)
     */
    public static boolean isEqualsMethod(@Nullable Method method) {
        if (method == null || !method.getName().equals("equals")) {
            return false;
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        return (paramTypes.length == 1 && paramTypes[0] == Object.class);
    }

    /**
     * Determine whether the given method is a "hashCode" method.
     *
     * @see java.lang.Object#hashCode()
     */
    public static boolean isHashCodeMethod(@Nullable Method method) {
        return (method != null && method.getName().equals("hashCode") && method.getParameterCount() == 0);
    }

    /**
     * Determine whether the given method is a "toString" method.
     *
     * @see java.lang.Object#toString()
     */
    public static boolean isToStringMethod(@Nullable Method method) {
        return (method != null && method.getName().equals("toString") && method.getParameterCount() == 0);
    }

    /**
     * Determine whether the given method is originally declared by {@link java.lang.Object}.
     */
    public static boolean isObjectMethod(@Nullable Method method) {
        return (method != null && (method.getDeclaringClass() == Object.class || isEqualsMethod(method)
                || isHashCodeMethod(method) || isToStringMethod(method)));
    }

    /**
     * Make the given method accessible, explicitly setting it accessible if necessary. The {@code setAccessible(true)}
     * method is only called when actually necessary, to avoid unnecessary conflicts with a JVM SecurityManager (if
     * active).
     *
     * @param method the method to make accessible
     * @see java.lang.reflect.Method#setAccessible
     */
    @SuppressWarnings("deprecation") // on JDK 9
    public static void makeAccessible(Executable method) {
        if ((!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(method.getDeclaringClass().getModifiers()))
                && !method.isAccessible()) {
            try {
                method.setAccessible(true);
            } catch (Exception e) {
                LOG.warn("nop.commons.reflect.set-accessible-fail:method={}", e, method);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static void makeAccessible(Field field) {
        if (!field.isAccessible()) {
            try {
                field.setAccessible(true);
            } catch (Exception e) {
                LOG.warn("nop.commons.reflect.set-accessible-fail:field={}", e, field);
            }
        }
    }

    public static String getParamName(Parameter arg) {
        Name name = arg.getAnnotation(Name.class);
        if (name != null)
            return name.value();
        return arg.getName();
    }

    public static Method getFunctionalMethod(Class clazz) {
        if (!clazz.isInterface() || !clazz.isAnnotationPresent(FunctionalInterface.class))
            return null;

        for (Method method : clazz.getMethods()) {
            if (method.isDefault() || method.isSynthetic())
                continue;

            if ((method.getModifiers() & (Modifier.ABSTRACT | Modifier.STATIC)) == Modifier.ABSTRACT)
                return method;
        }
        return null;
    }

    /**
     * JDK9以上调用接口上default方法
     */
    public static Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> declaringClass = method.getDeclaringClass();
        return MethodHandles.lookup()
                .findSpecial(declaringClass, method.getName(),
                        MethodType.methodType(method.getReturnType(), method.getParameterTypes()), declaringClass)
                .bindTo(proxy).invokeWithArguments(args);
    }
}
