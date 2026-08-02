/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import java.io.Serializable;

import io.nop.stream.core.common.typeutils.IStreamSerializer;
import io.nop.stream.core.common.typeutils.JsonToolSerializer;
import io.nop.stream.core.common.typeutils.TypeSerializer;

public class StateDescriptor<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final Class<T> valueType;
    private final T defaultValue;

    private TypeSerializer<T> serializer;

    /**
     * Optional TTL configuration. Defaults to {@link StateTtlConfig#DISABLED}. This field
     * is intentionally <b>not</b> {@code final} so that {@link #setTtlConfig} can rebind
     * TTL at runtime (e.g. after restore). Backward compatible with previously serialized
     * descriptors: a missing field deserializes to {@code null}, which
     * {@link #getTtlConfig()} resolves to {@link StateTtlConfig#DISABLED}.
     *
     * <p>TTL is a runtime behaviour, not a schema attribute —
     * {@link StateSchemaResolver} never reads it, so {@code schemaChecksum} is unaffected.
     */
    private StateTtlConfig ttlConfig = StateTtlConfig.DISABLED;

    public StateDescriptor(String name, Class<T> valueType) {
        this.name = name;
        this.valueType = valueType;
        this.defaultValue = null;
        this.serializer = new JsonToolSerializer<>();
    }

    public StateDescriptor(String name, Class<T> valueType, T defaultValue) {
        this.name = name;
        this.valueType = valueType;
        this.defaultValue = defaultValue;
        this.serializer = new JsonToolSerializer<>();
    }

    public String getName() {
        return name;
    }

    public Class<T> getValueType() {
        return valueType;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public TypeSerializer<T> getSerializer() {
        return serializer;
    }

    public void setSerializer(TypeSerializer<T> serializer) {
        this.serializer = serializer;
    }

    /**
     * Returns the TTL configuration attached to this descriptor, never {@code null}
     * (resolves a deserialized {@code null} to {@link StateTtlConfig#DISABLED}).
     */
    public StateTtlConfig getTtlConfig() {
        return ttlConfig != null ? ttlConfig : StateTtlConfig.DISABLED;
    }

    /**
     * Attaches TTL configuration to this descriptor. Convenience for
     * {@code descriptor.setTtlConfig(StateTtlConfig.newBuilder(ttl).setUpdateType(type).build())}.
     */
    public void setTtlConfig(StateTtlConfig ttlConfig) {
        this.ttlConfig = ttlConfig != null ? ttlConfig : StateTtlConfig.DISABLED;
    }
}
