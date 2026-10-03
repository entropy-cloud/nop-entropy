/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.api.core.exceptions.NopException;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.model.StreamSchemaFieldModel;
import io.nop.stream.flow.model.StreamSchemaModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

/**
 * WI8b: resolves the {@code <schemas>} declaration surface into typed field specs.
 *
 * <p>Field types converge on the managed type names understood by
 * {@link BasicTypeInfo} (string/int/bigint/smallint/tinyint/float/double/boolean/bytes);
 * the field coder is the {@link BasicTypeInfo#getSerializer() built-in serializer} —
 * the {@code <coders>} registry stays fail-fast (FU-3).
 *
 * <p>This class is intentionally independent of the transient
 * {@link StreamModelDslBuilder} lifecycle: {@link #resolveSchemas(StreamModel)} is a
 * stateless entry point whose first caller is {@code build()}, and the returned
 * registry is immutable so the WI17 SQL compiler can query it freely.
 */
public final class StreamSchemaRegistry {

    /**
     * Immutable resolved field spec: model metadata + parsed {@link BasicTypeInfo}.
     */
    public static final class FieldSpec {
        private final String name;
        private final BasicTypeInfo<?> type;
        private final Boolean nullable;
        private final Object defaultValue;

        FieldSpec(String name, BasicTypeInfo<?> type, Boolean nullable, Object defaultValue) {
            this.name = name;
            this.type = type;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
        }

        public String getName() {
            return name;
        }

        public BasicTypeInfo<?> getType() {
            return type;
        }

        public Boolean getNullable() {
            return nullable;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }
    }

    private final Map<String, List<FieldSpec>> schemas;

    private StreamSchemaRegistry(Map<String, List<FieldSpec>> schemas) {
        this.schemas = schemas;
    }

    /**
     * Strict managed-name resolution: returns the {@link BasicTypeInfo} singleton for
     * one of the nine managed names, or {@code null} when the name is unknown (the
     * caller anchors the error to the model location).
     */
    public static BasicTypeInfo<?> resolveManagedType(String typeName) {
        switch (typeName) {
            case "string":
                return BasicTypeInfo.STRING;
            case "int":
                return BasicTypeInfo.INT;
            case "bigint":
                return BasicTypeInfo.LONG;
            case "smallint":
                return BasicTypeInfo.SHORT;
            case "tinyint":
                return BasicTypeInfo.BYTE;
            case "float":
                return BasicTypeInfo.FLOAT;
            case "double":
                return BasicTypeInfo.DOUBLE;
            case "boolean":
                return BasicTypeInfo.BOOLEAN;
            case "bytes":
                return BasicTypeInfo.BYTE_ARRAY;
            default:
                return null;
        }
    }

    /**
     * Stateless resolution entry point: parses every declared schema's fields into
     * typed specs. First unresolvable field fails fast with the field anchored
     * (builder's existing style — no error collection).
     */
    public static StreamSchemaRegistry resolveSchemas(StreamModel model) {
        Map<String, List<FieldSpec>> schemas = new LinkedHashMap<>();
        if (model.getSchemas() != null) {
            for (StreamSchemaModel schema : model.getSchemas()) {
                Map<String, FieldSpec> fields = new LinkedHashMap<>();
                if (schema.getFields() != null) {
                    for (StreamSchemaFieldModel field : schema.getFields()) {
                        BasicTypeInfo<?> type = resolveManagedType(field.getType());
                        if (type == null)
                            throw new StreamException(ERR_STREAM_INVALID_ARG)
                                    .param(ARG_ARG_NAME, "schemas")
                                    .param(ARG_DETAIL, "schema=" + schema.getId()
                                            + " field=" + field.getName()
                                            + " type=" + field.getType()
                                            + " is not a managed type name (string/int/bigint/smallint/tinyint/float/double/boolean/bytes)")
                                    .loc(schema.getLocation());
                        fields.put(field.getName(), new FieldSpec(field.getName(), type,
                                field.getNullable(), field.getDefaultValue()));
                    }
                }
                schemas.put(schema.getId(), Collections.unmodifiableList(new java.util.ArrayList<>(fields.values())));
            }
        }
        return new StreamSchemaRegistry(Collections.unmodifiableMap(schemas));
    }

    public boolean contains(String schemaId) {
        return schemas.containsKey(schemaId);
    }

    public List<FieldSpec> resolveSchema(String schemaId) {
        List<FieldSpec> specs = schemas.get(schemaId);
        if (specs == null)
            throw new NopException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "schemaId")
                    .param(ARG_DETAIL, "unknown schema id: " + schemaId);
        return specs;
    }
}
