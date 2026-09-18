package io.nop.jq.jq.runtime;

import java.util.*;

/**
 * Runtime value representation for jq execution.
 */
public sealed interface JqValue permits
        JqNull, JqBoolean, JqNumber, JqString, JqArray, JqObject, JqFunction {

    JqNull NULL = JqNull.INSTANCE;
    JqBoolean TRUE = new JqBoolean(true);
    JqBoolean FALSE = new JqBoolean(false);

    static JqValue of(Object obj) {
        if (obj == null) return NULL;
        if (obj instanceof Boolean b) return JqBoolean.of(b);
        if (obj instanceof Integer i) return JqNumber.of(i);
        if (obj instanceof Long l) return JqNumber.of(l);
        if (obj instanceof Double d) return JqNumber.of(d);
        if (obj instanceof Float f) return JqNumber.of(f.doubleValue());
        if (obj instanceof Number n) return JqNumber.of(n.doubleValue());
        if (obj instanceof String s) return JqString.of(s);
        if (obj instanceof List<?> list) {
            List<JqValue> items = new ArrayList<>(list.size());
            for (Object item : list) items.add(of(item));
            return new JqArray(items);
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, JqValue> props = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                props.put(e.getKey().toString(), of(e.getValue()));
            }
            return new JqObject(props);
        }
        if (obj instanceof JqValue jv) return jv;
        throw new IllegalArgumentException("Cannot convert " + obj.getClass() + " to JqValue");
    }

    Object toJava();

    default boolean isNull() { return false; }
    default boolean isBoolean() { return false; }
    default boolean isNumber() { return false; }
    default boolean isString() { return false; }
    default boolean isArray() { return false; }
    default boolean isObject() { return false; }
    default boolean isFunction() { return false; }

    default JqNull asNull() { throw new JqRuntimeException("Not null: " + this); }
    default JqBoolean asBoolean() { throw new JqRuntimeException("Not boolean: " + this); }
    default JqNumber asNumber() { throw new JqRuntimeException("Not number: " + this); }
    default JqString asString() { throw new JqRuntimeException("Not string: " + this); }
    default JqArray asArray() { throw new JqRuntimeException("Not array: " + this); }
    default JqObject asObject() { throw new JqRuntimeException("Not object: " + this); }

    default String typeName() {
        if (this instanceof JqNull) return "null";
        if (this instanceof JqBoolean) return "boolean";
        if (this instanceof JqNumber) return "number";
        if (this instanceof JqString) return "string";
        if (this instanceof JqArray) return "array";
        if (this instanceof JqObject) return "object";
        if (this instanceof JqFunction) return "function";
        return "unknown";
    }
}
