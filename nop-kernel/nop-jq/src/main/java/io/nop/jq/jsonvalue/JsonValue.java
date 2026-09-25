package io.nop.jq.jsonvalue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable JSON value type. Uses Java sealed interface for compile-time exhaustive type checking.
 * <p>
 * All instances are immutable and safe to share across threads.
 */
public sealed interface JsonValue permits JsonNull, JsonBoolean, JsonNumber, JsonString, JsonArray, JsonObject {

    JsonNull NULL = JsonNull.INSTANCE;

    JsonBoolean TRUE = new JsonBoolean(true);
    JsonBoolean FALSE = new JsonBoolean(false);

    static JsonBoolean ofBoolean(boolean value) {
        return value ? TRUE : FALSE;
    }

    static JsonString ofString(String value) {
        return value == null ? null : new JsonString(value);
    }

    static JsonNumber ofInt(int value) {
        return new JsonNumber(value);
    }

    static JsonNumber ofLong(long value) {
        return new JsonNumber(value);
    }

    static JsonNumber ofDouble(double value) {
        return new JsonNumber(value);
    }

    static JsonNumber ofNumber(Number value) {
        return value == null ? null : new JsonNumber(value);
    }

    static JsonArray ofList(List<?> list) {
        return list == null ? null : new JsonArray(list);
    }

    static JsonArray emptyArray() {
        return new JsonArray(Collections.emptyList());
    }

    static JsonObject ofMap(Map<String, Object> map) {
        return map == null ? null : new JsonObject(map);
    }

    static JsonObject emptyObject() {
        return new JsonObject(Collections.emptyMap());
    }

    /**
     * Convert a plain Java object (Map, List, Number, Boolean, String, null) to a JsonValue.
     */
    static JsonValue fromObject(Object obj) {
        if (obj == null)
            return NULL;
        if (obj instanceof JsonValue)
            return (JsonValue) obj;
        if (obj instanceof Boolean)
            return ofBoolean((Boolean) obj);
        if (obj instanceof Number)
            return ofNumber((Number) obj);
        if (obj instanceof String)
            return ofString((String) obj);
        if (obj instanceof List)
            return ofList((List<?>) obj);
        if (obj instanceof Map)
            return ofMap((Map<String, Object>) obj);
        throw new IllegalArgumentException("Unsupported type: " + obj.getClass().getName());
    }

    /**
     * Convert this JsonValue to a plain Java object (Map, List, Number, Boolean, String, null).
     */
    Object toObject();

    default boolean isNull() {
        return false;
    }

    default boolean isBoolean() {
        return false;
    }

    default boolean isNumber() {
        return false;
    }

    default boolean isString() {
        return false;
    }

    default boolean isArray() {
        return false;
    }

    default boolean isObject() {
        return false;
    }

    default JsonNull asNull() {
        throw new IllegalArgumentException("Not a JsonNull: " + this);
    }

    default JsonBoolean asBoolean() {
        throw new IllegalArgumentException("Not a JsonBoolean: " + this);
    }

    default JsonNumber asNumber() {
        throw new IllegalArgumentException("Not a JsonNumber: " + this);
    }

    default JsonString asString() {
        throw new IllegalArgumentException("Not a JsonString: " + this);
    }

    default JsonArray asArray() {
        throw new IllegalArgumentException("Not a JsonArray: " + this);
    }

    default JsonObject asObject() {
        throw new IllegalArgumentException("Not a JsonObject: " + this);
    }
}
