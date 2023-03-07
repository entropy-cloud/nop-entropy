/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.type;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.beans.PointBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.lang.Never;
import io.nop.commons.lang.Null;
import io.nop.commons.lang.Unknown;
import io.nop.commons.util.StringHelper;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static io.nop.commons.CommonErrors.ARG_INDEX;
import static io.nop.commons.CommonErrors.ARG_NAME;
import static io.nop.commons.CommonErrors.ERR_TYPE_INVALID_TYPE_INDEX;
import static io.nop.commons.CommonErrors.ERR_TYPE_UNKNOWN_TYPE_NAME;

/**
 * 基础数据类型，在Java内置类型基础上扩充了DateTime,Duration等一般编程中常用的类型，覆盖了关系数据库字段映射到java语言所对应的所有类型。
 * <p>
 * json schema type: null, boolean, object, array, number, string
 */
public enum StdDataType {
    ANY(0, "any", Object.class), //

    BOOLEAN(1, "boolean", Boolean.class), //
    CHAR(2, "char", Character.class), //
    BYTE(3, "byte", Byte.class), //

    SHORT(4, "short", Short.class), //
    INT(5, "int", Integer.class), //
    LONG(6, "long", Long.class), //

    FLOAT(7, "float", Float.class), //
    DOUBLE(8, "double", Double.class), //

    DECIMAL(9, "decimal", BigDecimal.class), //
    BIGINT(10, "bigint", BigInteger.class), //

    STRING(11, "string", String.class), // String类型对应于VARCHAR

    DATE(12, "date", LocalDate.class), //
    DATETIME(13, "datetime", LocalDateTime.class), //
    TIMESTAMP(14, "timestamp", Timestamp.class), //
    TIME(15, "time", LocalTime.class), DURATION(16, "duration", Duration.class),

    MAP(17, "map", Map.class), // 可以映射到数据库中的JSON类型
    LIST(18, "list", List.class), // 可以映射到数据库中的JSON类型

    FILE(19, "file", FileReference.class), // 对应于附件字段
    FILES(20, "files", FileListReference.class), // 对应于附件列表字段

    POINT(21, "point", PointBean.class), // 对应于平面二维点
    GEOMETRY(22, "geometry", GeometryObject.class), // 对应于地理信息对象等

    BYTES(23, "bytes", ByteString.class), //

    // 当一个函数返回空值时，它的返回值为 void 类型
    VOID(24, "void", Void.class), //

    NULL(25, "null", Null.class),

    UNKNOWN(26, "unknown", Unknown.class),

    // 当一个函数永不返回时（或者总是抛出错误），它的返回值为 never 类型。
    // 除了 never 本身以外，其他任何类型不能赋值给 never
    NEVER(27, "never", Never.class);

    private final String name;
    private final Class javaType;

    private final int index;

    StdDataType(int index, String name, Class javaType) {
        this.javaType = javaType;
        this.name = name;
        this.index = index;
    }

    /**
     * 简单数据类型的转换由ConvertHelper负责
     */
    public boolean isSimpleType() {
        return ordinal() >= BOOLEAN.ordinal() && ordinal() <= DURATION.ordinal();
    }

    public String getClassName() {
        return javaType.getTypeName();
    }

    public String getJavaTypeName() {
        return javaType.getTypeName();
    }

    public String getSimpleClassName() {
        return javaType.getSimpleName();
    }

    public String getMandatoryJavaTypeName() {
        return getMandatoryJavaClass().getTypeName();
    }

    public Class<?> getJavaClass() {
        return javaType;
    }

    public Class<?> getMandatoryJavaClass() {
        Class<?> clazz = ConvertHelper.getPrimitiveClass(javaType);
        if (clazz == null)
            clazz = javaType;
        return clazz;
    }

    public int getIndex() {
        return index;
    }

    public boolean isPrimitiveType() {
        return getMandatoryJavaClass().isPrimitive();
    }

    public boolean isNumericType() {
        return this == INT || this == LONG || this == FLOAT || this == DOUBLE || this == DECIMAL || this == SHORT;
    }

    public boolean isTemporalType() {
        return this == DATE || this == DATETIME || this == TIMESTAMP;
    }

    public boolean isGeoType() {
        return this == POINT || this == GEOMETRY;
    }

    public boolean isBoolType() {
        return this == BOOLEAN;
    }

    public boolean isMapType() {
        return this == MAP;
    }

    public boolean isListType() {
        return this == LIST;
    }

    public boolean isAbstractType() {
        return this.index >= VOID.index;
    }

    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    private static final Map<String, StdDataType> stdNameMap = new HashMap<>();
    private static final Map<Class<?>, StdDataType> javaTypeMap = new HashMap<>();
    private static final Map<String, StdDataType> javaClassMap = new HashMap<>();
    private static final StdDataType[] indexMap;

    private static final Map<String, StdDataType> jsonMap = new HashMap<>();

    static {
        jsonMap.put("null", NULL);
        jsonMap.put("string", STRING);
        jsonMap.put("boolean", BOOLEAN);
        jsonMap.put("object", MAP);
        jsonMap.put("array", LIST);
        jsonMap.put("number", DOUBLE);

        for (StdDataType type : StdDataType.values()) {
            stdNameMap.put(type.getName(), type);

            if (!javaTypeMap.containsKey(type.getJavaClass()))
                javaTypeMap.put(type.getJavaClass(), type);

            if (!javaTypeMap.containsKey(type.getMandatoryJavaClass()))
                javaTypeMap.put(type.getMandatoryJavaClass(), type);

            if (!javaClassMap.containsKey(type.getJavaClass().getName()))
                javaClassMap.put(type.getJavaClass().getName(), type);

            if (!javaClassMap.containsKey(type.getMandatoryJavaClass().getName()))
                javaClassMap.put(type.getMandatoryJavaClass().getName(), type);

            if (!javaClassMap.containsKey(type.getSimpleClassName()))
                javaClassMap.put(type.getSimpleClassName(), type);

            if (!javaClassMap.containsKey(type.getMandatoryJavaClass().getSimpleName()))
                javaClassMap.put(type.getMandatoryJavaClass().getSimpleName(), type);
        }

        int maxIndex = NEVER.getIndex();
        indexMap = new StdDataType[maxIndex + 1];
        for (StdDataType type : StdDataType.values()) {
            if (indexMap[type.getIndex()] != null)
                throw new IllegalStateException("nop.err.commons.type-index-conflict:type=" + type);
            indexMap[type.getIndex()] = type;
        }
    }

    public static int getMaxIndex() {
        return ANY.getIndex();
    }

    public static StdDataType fromIndex(int index) {
        if (index < 0 || index >= indexMap.length)
            throw new NopException(ERR_TYPE_INVALID_TYPE_INDEX).param(ARG_INDEX, index);
        return indexMap[index];
    }

    @StaticFactoryMethod
    public static StdDataType of(String stdName) {
        StdDataType type = stdNameMap.get(stdName);
        if (type == null)
            throw new NopException(ERR_TYPE_UNKNOWN_TYPE_NAME).param(ARG_NAME, stdName);
        return type;
    }

    public static StdDataType fromJsonName(String jsonName) {
        return jsonMap.get(jsonName);
    }

    public static StdDataType fromStdName(String stdName) {
        StdDataType type = stdNameMap.get(stdName);
        return type;
    }

    public static Set<String> getNames() {
        return Collections.unmodifiableSet(stdNameMap.keySet());
    }

    public Object convert(Object value, Function<ErrorCode, NopException> errorFactory) {
        if (value == null)
            return null;
        return ConvertHelper.convertTo(getJavaClass(), value, errorFactory);
    }

    public Object convert(Object value) {
        return convert(value, NopException::new);
    }

    public String getDemoJsonValue() {
        switch (this) {
            case STRING: {
                return "\"\"";
            }
            case BOOLEAN:
                return "false";
            case INT:
            case LONG:
            case SHORT:
            case BYTE:
                return "0";
            case DOUBLE:
            case FLOAT:
            case DECIMAL:
                return "0.0";
            case DATE:
                return "\"2000-01-01\"";
            case DATETIME:
            case TIMESTAMP:
                return "\"2000-01-01 14:00:00\"";
            default:
                return "null";
        }
    }

    public static boolean isSimpleType(String className) {
        StdDataType type = fromJavaClassName(className);
        if (type == null)
            type = fromStdName(className);
        if (type == null)
            return false;
        return type.isSimpleType();
    }

    public static StdDataType fromJavaClassName(String className) {
        return javaClassMap.get(className);
    }

    public static StdDataType fromJavaClass(Class<?> clazz) {
        StdDataType type = javaTypeMap.get(clazz);
        if (type != null)
            return type;

        if (Map.class.isAssignableFrom(clazz))
            return MAP;
        if (List.class.isAssignableFrom(clazz))
            return LIST;
        if (FileReference.class.isAssignableFrom(clazz))
            return FILE;
        if (FileListReference.class.isAssignableFrom(clazz))
            return FILES;
        if (PointBean.class.isAssignableFrom(clazz))
            return POINT;
        if (GeometryObject.class.isAssignableFrom(clazz))
            return GEOMETRY;
        return ANY;
    }

    public static String toLiteral(Object value, Type type) {
        if (value == null)
            return null;

        if (value instanceof Boolean)
            return value.toString();

        if (value instanceof Character)
            return "'" + value + "'";

        if (value instanceof String) {
            if (type != null && !type.getTypeName().equals(String.class.getName()))
                return null;
            String str = value.toString();
            return StringHelper.quote(str);
        }

        if (value instanceof Integer)
            return value.toString();

        if (value instanceof Short)
            return "(short)" + value;

        if (value instanceof Long)
            return value + "L";

        if (value instanceof Float)
            return value + "f";

        if (value instanceof Double)
            return value.toString();

        if (value instanceof BigDecimal) {
            return "java.math.BigDecimal.valueOf(\"" + value + "\")";
        }

        if (value instanceof LocalDate) {
            if (ApiStringHelper.INVALID_DATE.equals(value))
                return "io.nop.api.core.util.ApiStringHelper.INVALID_DATE";
            if (ApiStringHelper.FUTURE_DATE.equals(value))
                return "io.nop.api.core.util.ApiStringHelper.FUTURE_DATE";
        }

        return null;
    }

    public static String toInitializer(Object value, Type type) {
        String literal = toLiteral(value, type);
        if (literal != null)
            return " = " + literal;
        return null;
    }
}