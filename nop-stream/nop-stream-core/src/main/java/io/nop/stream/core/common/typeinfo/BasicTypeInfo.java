/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.typeinfo;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class BasicTypeInfo<T> implements TypeInformation<T>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Map<Class<?>, BasicTypeInfo<?>> INSTANCES = new HashMap<>();

    public static final BasicTypeInfo<String> STRING = register(String.class);
    public static final BasicTypeInfo<Integer> INT = register(Integer.class);
    public static final BasicTypeInfo<Long> LONG = register(Long.class);
    public static final BasicTypeInfo<Double> DOUBLE = register(Double.class);
    public static final BasicTypeInfo<Boolean> BOOLEAN = register(Boolean.class);
    public static final BasicTypeInfo<Byte> BYTE = register(Byte.class);
    public static final BasicTypeInfo<Short> SHORT = register(Short.class);
    public static final BasicTypeInfo<Float> FLOAT = register(Float.class);
    public static final BasicTypeInfo<byte[]> BYTE_ARRAY = register(byte[].class);

    private final Class<T> typeClass;
    private final TypeSerializer<T> serializer;

    private BasicTypeInfo(Class<T> typeClass) {
        this.typeClass = typeClass;
        this.serializer = new SimpleTypeSerializer<>(typeClass);
    }

    private static <T> BasicTypeInfo<T> register(Class<T> typeClass) {
        BasicTypeInfo<T> info = new BasicTypeInfo<>(typeClass);
        INSTANCES.put(typeClass, info);
        return info;
    }

    public static <T> BasicTypeInfo<T> of(Class<T> typeClass) {
        @SuppressWarnings("unchecked")
        BasicTypeInfo<T> info = (BasicTypeInfo<T>) INSTANCES.get(typeClass);
        if (info != null) return info;
        info = new BasicTypeInfo<>(typeClass);
        INSTANCES.put(typeClass, info);
        return info;
    }

    public TypeSerializer<T> getSerializer() {
        return serializer;
    }

    @Override
    public Class<T> getTypeClass() {
        return typeClass;
    }

    @Override
    public String toString() {
        return "BasicTypeInfo<" + typeClass.getSimpleName() + ">";
    }
}
