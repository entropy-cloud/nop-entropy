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

package io.nop.core.reflect.proxy;

import jakarta.annotation.Nullable;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.Arrays;

// refactor from springframework

public class ProxyHelper {

    public static boolean isProxyClass(Class clazz) {
        return Proxy.class.isAssignableFrom(clazz);
    }

    /**
     * 对于aop生成的派生类，获取到基类. CGLib生成类名含有$$
     */
    public static Class getProxyTargetClass(Class clazz) {
        String name = clazz.getName();
        int pos = name.lastIndexOf("$$");
        if (pos < 0)
            return clazz;
        if (isProxyClass(clazz))
            return clazz.getInterfaces()[0];
        return clazz.getSuperclass();
    }

    public static Object metaToString(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Class) {
            return ((Class<?>) value).getName();
        }
        if (value.getClass().isArray()) {
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            for (int i = 0; i < Array.getLength(value); i++) {
                builder.append(i == 0 ? "" : ", ");
                builder.append(metaToString(Array.get(value, i)));
            }
            builder.append("]");
            return builder.toString();
        }
        return String.valueOf(value);
    }

    public static int metaHashCode(Object value) {
        if (value == null)
            return 0;

        // Use Arrays.hashCode since ObjectUtils doesn't comply to to
        // Annotation#hashCode()
        if (value instanceof boolean[]) {
            return Arrays.hashCode((boolean[]) value);
        }
        if (value instanceof byte[]) {
            return Arrays.hashCode((byte[]) value);
        }
        if (value instanceof char[]) {
            return Arrays.hashCode((char[]) value);
        }
        if (value instanceof double[]) {
            return Arrays.hashCode((double[]) value);
        }
        if (value instanceof float[]) {
            return Arrays.hashCode((float[]) value);
        }
        if (value instanceof int[]) {
            return Arrays.hashCode((int[]) value);
        }
        if (value instanceof long[]) {
            return Arrays.hashCode((long[]) value);
        }
        if (value instanceof short[]) {
            return Arrays.hashCode((short[]) value);
        }
        if (value instanceof Object[]) {
            return Arrays.hashCode((Object[]) value);
        }
        return value.hashCode();
    }
}
