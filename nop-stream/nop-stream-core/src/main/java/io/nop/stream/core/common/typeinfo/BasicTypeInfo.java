/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.typeinfo;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BasicTypeInfo<T> implements TypeInformation<T>, Serializable {

    private static final long serialVersionUID = 1L;

    // CRA 8.41 W-01（C1-003）：并发懒注册的 check-then-act（get→put）在 HashMap 上可结构损坏或双实例
    // 分叉——CHM + computeIfAbsent 与 StreamElementCodec.DECODED_VALUE_TYPES 既有正确形态同型
    private static final Map<Class<?>, BasicTypeInfo<?>> INSTANCES = new ConcurrentHashMap<>();

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
    private final SimpleSerializer<T> serializer;

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
        // CRA 8.41 W-01：computeIfAbsent 原子闭合懒注册（JDK 契约：映射函数对同一 key 至多应用一次——
        // 同 key 并发只产生一个实例，双实例行为分叉在类型层面不可能）
        @SuppressWarnings("unchecked")
        BasicTypeInfo<T> info = (BasicTypeInfo<T>) INSTANCES.computeIfAbsent(typeClass,
                cls -> new BasicTypeInfo<>((Class<?>) cls));
        return info;
    }

    public SimpleSerializer<T> getSerializer() {
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
