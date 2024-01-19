package io.nop.commons.type;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static io.nop.commons.CommonErrors.ARG_ALLOWED_NAMES;
import static io.nop.commons.CommonErrors.ARG_TYPE_NAME;
import static io.nop.commons.CommonErrors.ERR_COMMONS_UNKNOWN_BINARY_SCALAR_TYPE;

/**
 * 包含proto协议中定义的所有基本数据类型
 */
public enum BinaryScalarType {
    ANY,
    BOOL,
    BYTES,
    DOUBLE,
    FLOAT,
    FIXED32,
    FIXED64,
    INT32,
    INT64,
    SFIXED32,
    SFIXED64,
    SINT32,
    SINT64,
    STRING,
    UINT32,
    UINT64,

    //-----------------从此以后为protobuf定义之外的类型-------------------------//
    INT8,
    INT16,
    VOID;

    public boolean isProtoBufType() {
        return ordinal() <= UINT32.ordinal();
    }

    public StdDataType toStdDataType() {
        switch (this) {
            case ANY:
                return StdDataType.ANY;
            case STRING:
                return StdDataType.STRING;
            case BOOL:
                return StdDataType.BOOLEAN;
            case BYTES:
                return StdDataType.BYTES;
            case DOUBLE:
                return StdDataType.DOUBLE;
            case FLOAT:
                return StdDataType.FLOAT;
            case FIXED32:
            case INT32:
            case SFIXED32:
            case UINT32:
                return StdDataType.INT;
            case FIXED64:
            case INT64:
            case SFIXED64:
            case UINT64:
                return StdDataType.LONG;
            case INT8:
                return StdDataType.BYTE;
            case INT16:
                return StdDataType.SHORT;
            case VOID:
                return StdDataType.VOID;
            default:
                // 不会执行到这里
                throw new IllegalStateException("unknown scala type:" + this);
        }
    }

    public BinaryScalarType toProtoBufType() {
        if (isProtoBufType())
            return this;
        switch (this) {
            case INT8:
            case INT16:
                return INT32;
            case VOID:
                return VOID;
        }
        // 不会执行到这里
        throw new IllegalStateException("unknown prototype type:" + this);
    }

    public String toProtoBufTypeName() {
        BinaryScalarType type = toProtoBufType();
        if (type == VOID)
            return "google.protobuf.Empty";
        return type.getText();
    }

    private final String text;

    BinaryScalarType() {
        text = name().toLowerCase(Locale.US);
    }

    @Override
    public String toString() {
        return text;
    }

    public String getText() {
        return text;
    }

    private static Map<String, BinaryScalarType> textMap = new HashMap<>();

    static {
        for (BinaryScalarType type : values()) {
            textMap.put(textMap.toString(), type);
        }
    }

    public static BinaryScalarType requireFromText(String text) {
        BinaryScalarType type = fromText(text);
        if (type == null)
            throw new NopException(ERR_COMMONS_UNKNOWN_BINARY_SCALAR_TYPE)
                    .param(ARG_TYPE_NAME, text).param(ARG_ALLOWED_NAMES, textMap.keySet());
        return type;
    }

    @StaticFactoryMethod
    public static BinaryScalarType fromText(String text) {
        if (StringHelper.isEmpty(text))
            return null;

        return textMap.get(text);
    }
}
