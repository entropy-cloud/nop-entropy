/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.schema;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.json.IJsonString;
import io.nop.commons.type.StdDataType;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("java:S115")
public enum GraphQLScalarType {
    ID(StdDataType.STRING), //
    Boolean(StdDataType.BOOLEAN), //
    // Byte(StdDataType.BYTE),
    // Char(StdDataType.CHAR),
    // Short(StdDataType.SHORT),
    Int(StdDataType.INT), //
    Long(StdDataType.LONG),
    Float(StdDataType.DOUBLE), //
    String(StdDataType.STRING), //
    Map(StdDataType.MAP), //
    Any(StdDataType.ANY),
    Void(StdDataType.VOID);
    // BigInteger(StdDataType.BIGINT),
    // BigDecimal(StdDataType.DECIMAL);

    private final StdDataType stdDataType;

    GraphQLScalarType(StdDataType stdDataType) {
        this.stdDataType = stdDataType;
    }

    public StdDataType getStdDataType() {
        return stdDataType;
    }

    static final Map<StdDataType, GraphQLScalarType> stdMap = new HashMap<>();
    static final Map<String, GraphQLScalarType> textMap = new HashMap<>();
    static final Map<Class<?>, GraphQLScalarType> typeMap = new HashMap<>();

    static {
        for (GraphQLScalarType type : values()) {
            stdMap.put(type.getStdDataType(), type);
            textMap.put(type.name(), type);
        }
        // stdMap.put(StdDataType.LONG, GraphQLScalarType.String);
        stdMap.put(StdDataType.BIGINT, GraphQLScalarType.String);
        stdMap.put(StdDataType.DECIMAL, GraphQLScalarType.Float);
        stdMap.put(StdDataType.BYTE, GraphQLScalarType.Int);
        stdMap.put(StdDataType.CHAR, GraphQLScalarType.String);
        stdMap.put(StdDataType.SHORT, GraphQLScalarType.Int);
        stdMap.put(StdDataType.FLOAT, GraphQLScalarType.Float);
        stdMap.put(StdDataType.DATE, GraphQLScalarType.String);
        stdMap.put(StdDataType.TIME, GraphQLScalarType.String);
        stdMap.put(StdDataType.DATETIME, GraphQLScalarType.String);
        stdMap.put(StdDataType.TIMESTAMP, GraphQLScalarType.String);
        stdMap.put(StdDataType.DURATION, GraphQLScalarType.String);
        stdMap.put(StdDataType.BYTES, GraphQLScalarType.String);
        stdMap.put(StdDataType.MAP, GraphQLScalarType.Map);
        // stdMap.put(StdDataType.LIST, GraphQLScalarType.String);
        stdMap.put(StdDataType.POINT, GraphQLScalarType.String);
        stdMap.put(StdDataType.GEOMETRY, GraphQLScalarType.String);
        stdMap.put(StdDataType.FILE, GraphQLScalarType.String);
        stdMap.put(StdDataType.FILES, GraphQLScalarType.String);
        stdMap.put(StdDataType.VOID, GraphQLScalarType.Void);

        for (Map.Entry<StdDataType, GraphQLScalarType> entry : stdMap.entrySet()) {
            typeMap.put(entry.getKey().getJavaClass(), entry.getValue());
            typeMap.put(entry.getKey().getMandatoryJavaClass(), entry.getValue());
        }
        typeMap.put(void.class, GraphQLScalarType.Void);
    }

    public static GraphQLScalarType fromStdDataType(StdDataType dataType) {
        return stdMap.get(dataType);
    }

    public static GraphQLScalarType fromJavaClass(Class<?> clazz) {
        if (clazz == null)
            return null;

        GraphQLScalarType type = typeMap.get(clazz);
        if (type == null) {
            if (IJsonString.class.isAssignableFrom(clazz))
                return GraphQLScalarType.String;
            if (Map.class.isAssignableFrom(clazz))
                return Map;
        }
        return type;
    }

    public static GraphQLScalarType fromStdName(String stdName) {
        StdDataType type = StdDataType.fromStdName(stdName);
        if (type == null)
            return null;
        return fromStdDataType(type);
    }

    @StaticFactoryMethod
    public static GraphQLScalarType fromText(String text) {
        return textMap.get(text);
    }
}
