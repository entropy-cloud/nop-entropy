package io.nop.jq.jq.runtime;

import java.util.*;

/**
 * Runtime value implementations for jq execution.
 */

final class JqNull implements JqValue {
    static final JqNull INSTANCE = new JqNull();
    private JqNull() {}
    @Override public Object toJava() { return null; }
    @Override public boolean isNull() { return true; }
    @Override public JqNull asNull() { return this; }
    @Override public boolean equals(Object o) { return o instanceof JqNull; }
    @Override public int hashCode() { return 0; }
    @Override public String toString() { return "null"; }
}

final class JqBoolean implements JqValue {
    private final boolean value;
    JqBoolean(boolean value) { this.value = value; }
    static JqBoolean of(boolean value) { return value ? JqValue.TRUE : JqValue.FALSE; }
    public boolean value() { return value; }
    @Override public Object toJava() { return value; }
    @Override public boolean isBoolean() { return true; }
    @Override public JqBoolean asBoolean() { return this; }
    @Override public boolean equals(Object o) { return o instanceof JqBoolean jb && jb.value == value; }
    @Override public int hashCode() { return Boolean.hashCode(value); }
    @Override public String toString() { return Boolean.toString(value); }
}

final class JqNumber implements JqValue {
    private final Number value;
    JqNumber(Number value) { this.value = value; }
    static JqNumber of(int v) { return new JqNumber(v); }
    static JqNumber of(long v) { return new JqNumber(v); }
    static JqNumber of(double v) { return new JqNumber(v); }
    static JqNumber of(Number v) { return new JqNumber(v); }
    public Number value() { return value; }
    public int intValue() { return value.intValue(); }
    public long longValue() { return value.longValue(); }
    public double doubleValue() { return value.doubleValue(); }
    @Override public Object toJava() { return value; }
    @Override public boolean isNumber() { return true; }
    @Override public JqNumber asNumber() { return this; }
    @Override public boolean equals(Object o) {
        if (o instanceof JqNumber jn) {
            // jq semantics: two literal numbers compare exactly, but anything
            // that went through arithmetic is a double — so 9007199254740993+0
            // equals the literal 9007199254740992 even though the exact values differ
            if (isLiteralInteger() && jn.isLiteralInteger())
                return longValue() == jn.longValue();
            return Double.compare(doubleValue(), jn.doubleValue()) == 0;
        }
        return false;
    }

    boolean isLiteralInteger() {
        return value instanceof Integer || value instanceof Long;
    }
    @Override public int hashCode() { return Double.hashCode(doubleValue()); }
    // note: equals special-cases literal integers, hashCode stays double-based
    // (only affects hash dispersion, not correctness of HashMap lookups for
    // values that differ by less than a double ULP)
    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        JqPrinter.appendNumber(sb, this);
        return sb.toString();
    }
}

final class JqString implements JqValue {
    private final String value;
    JqString(String value) { this.value = value; }
    static JqString of(String v) { return new JqString(v); }
    public String value() { return value; }
    @Override public Object toJava() { return value; }
    @Override public boolean isString() { return true; }
    @Override public JqString asString() { return this; }
    public int length() { return value.length(); }
    @Override public boolean equals(Object o) { return o instanceof JqString js && js.value.equals(value); }
    @Override public int hashCode() { return value.hashCode(); }
    @Override public String toString() { return value; }
}

final class JqArray implements JqValue {
    private final List<JqValue> items;
    JqArray(List<JqValue> items) { this.items = List.copyOf(items); }
    public List<JqValue> items() { return items; }
    public int size() { return items.size(); }
    public boolean isEmpty() { return items.isEmpty(); }
    public JqValue get(int index) { return items.get(index); }
    public JqArray add(JqValue v) {
        List<JqValue> newItems = new ArrayList<>(items);
        newItems.add(v);
        return new JqArray(newItems);
    }
    public JqArray set(int index, JqValue v) {
        List<JqValue> newItems = new ArrayList<>(items);
        newItems.set(index, v);
        return new JqArray(newItems);
    }
    public JqArray remove(int index) {
        List<JqValue> newItems = new ArrayList<>(items);
        newItems.remove(index);
        return new JqArray(newItems);
    }
    @Override public Object toJava() {
        List<Object> result = new ArrayList<>(items.size());
        for (JqValue v : items) result.add(v.toJava());
        return result;
    }
    @Override public boolean isArray() { return true; }
    @Override public JqArray asArray() { return this; }
    @Override public boolean equals(Object o) {
        return o instanceof JqArray ja && items.equals(ja.items);
    }
    @Override public int hashCode() { return items.hashCode(); }
    @Override public String toString() { return JqPrinter.print(this); }
}

final class JqObject implements JqValue {
    private final Map<String, JqValue> properties;
    JqObject(Map<String, JqValue> properties) {
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    /**
     * Wrap a freshly built map without copying it: the caller transfers
     * ownership and must not touch the map afterwards. Hot construction
     * paths (object builds, to_entries) use this to avoid a second copy.
     */
    static JqObject ofFresh(Map<String, JqValue> properties) {
        return new JqObject(Collections.unmodifiableMap(properties));
    }
    public Map<String, JqValue> properties() { return properties; }
    public int size() { return properties.size(); }
    public boolean isEmpty() { return properties.isEmpty(); }
    public JqValue get(String key) { return properties.get(key); }
    public boolean has(String key) { return properties.containsKey(key); }
    public Set<String> keySet() { return properties.keySet(); }
    public JqObject put(String key, JqValue value) {
        Map<String, JqValue> newMap = new LinkedHashMap<>(properties);
        newMap.put(key, value);
        return new JqObject(newMap);
    }
    public JqObject remove(String key) {
        Map<String, JqValue> newMap = new LinkedHashMap<>(properties);
        newMap.remove(key);
        return new JqObject(newMap);
    }
    @Override public Object toJava() {
        Map<String, Object> result = new LinkedHashMap<>(properties.size());
        for (Map.Entry<String, JqValue> e : properties.entrySet()) {
            result.put(e.getKey(), e.getValue().toJava());
        }
        return result;
    }
    @Override public boolean isObject() { return true; }
    @Override public JqObject asObject() { return this; }
    @Override public boolean equals(Object o) {
        return o instanceof JqObject jo && properties.equals(jo.properties);
    }
    @Override public int hashCode() { return properties.hashCode(); }
    @Override public String toString() { return JqPrinter.print(this); }
}

@FunctionalInterface
non-sealed interface JqFunction extends JqValue {
    JqValue apply(List<JqValue> args, JqValue input);
    @Override default boolean isFunction() { return true; }
    @Override default Object toJava() { return this; }
}
