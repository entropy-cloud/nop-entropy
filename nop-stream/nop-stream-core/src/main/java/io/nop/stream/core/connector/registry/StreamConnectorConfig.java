/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_PARAM_KIND;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_PARAM_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TYPE_NAME;

/**
 * Per-call construction input handed to a connector factory. Carries scalar params
 * (string/int/string-list) plus programmatic OBJECT params (code or infrastructure
 * artifacts such as {@code IMessageService}, {@code IJdbcTemplate}, a record-mapper
 * function). Factories are stateless; all construction inputs arrive here
 * (connector-design.md §8.3 D3).
 *
 * <p>Validation boundary: {@code requireXxx} accessors fail fast with
 * {@code ERR_STREAM_CONNECTOR_PARAM_REQUIRED} when a required param is absent;
 * rejecting UNKNOWN params is field-level conf validation and belongs to item 20.
 */
public final class StreamConnectorConfig {

    private final String typeName;
    private final Map<String, Object> params;

    public StreamConnectorConfig(String typeName) {
        this(typeName, Map.of());
    }

    public StreamConnectorConfig(String typeName, Map<String, Object> params) {
        if (typeName == null || typeName.isEmpty()) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_NULL_ARG)
                    .param(NopStreamErrors.ARG_ARG_NAME, "typeName");
        }
        this.typeName = typeName;
        this.params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /** The connector type name this config targets; used in error params. */
    public String getTypeName() {
        return typeName;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public boolean hasParam(String name) {
        return params.containsKey(name);
    }

    // ------------------------------------------------------------------
    // required accessors (fail-fast on absence)
    // ------------------------------------------------------------------

    public String requireString(String name) {
        Object value = params.get(name);
        if (value == null) {
            throw paramRequired(name, ConnectorParamKind.STRING);
        }
        return value.toString();
    }

    public int requireInt(String name) {
        Object value = params.get(name);
        if (value == null) {
            throw paramRequired(name, ConnectorParamKind.INT);
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    public List<String> requireStringList(String name) {
        Object value = params.get(name);
        if (value == null) {
            throw paramRequired(name, ConnectorParamKind.STRING_LIST);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        throw new StreamException(NopStreamErrors.ERR_STREAM_INVALID_ARG)
                .param(NopStreamErrors.ARG_ARG_NAME, name)
                .param(NopStreamErrors.ARG_DETAIL, "expected a list of strings, got: " + value.getClass().getName());
    }

    public <T> T requireObject(String name, Class<T> targetType) {
        Object value = params.get(name);
        if (value == null) {
            throw paramRequired(name, ConnectorParamKind.OBJECT);
        }
        if (!targetType.isInstance(value)) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_INVALID_ARG)
                    .param(NopStreamErrors.ARG_ARG_NAME, name)
                    .param(NopStreamErrors.ARG_DETAIL,
                            "expected " + targetType.getName() + ", got " + value.getClass().getName());
        }
        return targetType.cast(value);
    }

    // ------------------------------------------------------------------
    // optional accessors (null / default on absence)
    // ------------------------------------------------------------------

    public String getString(String name) {
        Object value = params.get(name);
        return value == null ? null : value.toString();
    }

    public Integer getInt(String name) {
        Object value = params.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    public <T> T getObject(String name, Class<T> targetType) {
        Object value = params.get(name);
        if (value == null) {
            return null;
        }
        if (!targetType.isInstance(value)) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_INVALID_ARG)
                    .param(NopStreamErrors.ARG_ARG_NAME, name)
                    .param(NopStreamErrors.ARG_DETAIL,
                            "expected " + targetType.getName() + ", got " + value.getClass().getName());
        }
        return targetType.cast(value);
    }

    private StreamException paramRequired(String name, ConnectorParamKind kind) {
        StreamException ex = new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_PARAM_REQUIRED);
        ex.param(ARG_TYPE_NAME, typeName).param(ARG_PARAM_NAME, name).param(ARG_PARAM_KIND, kind.name());
        return ex;
    }
}
