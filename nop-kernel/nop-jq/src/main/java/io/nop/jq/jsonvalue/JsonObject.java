package io.nop.jq.jsonvalue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JsonObject implements JsonValue {
    private final Map<String, JsonValue> properties;

    JsonObject(Map<String, Object> map) {
        Map<String, JsonValue> converted = new LinkedHashMap<>(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            converted.put(entry.getKey(), JsonValue.fromObject(entry.getValue()));
        }
        this.properties = Collections.unmodifiableMap(converted);
    }

    private JsonObject(Map<String, JsonValue> properties, boolean direct) {
        this.properties = properties;
    }

    public int size() {
        return properties.size();
    }

    public boolean isEmpty() {
        return properties.isEmpty();
    }

    public boolean has(String key) {
        return properties.containsKey(key);
    }

    public JsonValue get(String key) {
        return properties.get(key);
    }

    public String getString(String key) {
        JsonValue v = properties.get(key);
        if (v == null || v.isNull())
            return null;
        return v.asString().value();
    }

    public int getInt(String key, int defaultValue) {
        JsonValue v = properties.get(key);
        if (v == null || v.isNull())
            return defaultValue;
        return v.asNumber().intValue();
    }

    public Set<String> keySet() {
        return properties.keySet();
    }

    public Set<Map.Entry<String, JsonValue>> entrySet() {
        return properties.entrySet();
    }

    public Map<String, JsonValue> properties() {
        return properties;
    }

    public JsonObject put(String key, JsonValue value) {
        Map<String, JsonValue> newMap = new LinkedHashMap<>(properties);
        newMap.put(key, value);
        return new JsonObject(Collections.unmodifiableMap(newMap), true);
    }

    public JsonObject remove(String key) {
        Map<String, JsonValue> newMap = new LinkedHashMap<>(properties);
        newMap.remove(key);
        return new JsonObject(Collections.unmodifiableMap(newMap), true);
    }

    public List<String> keys() {
        return new ArrayList<>(properties.keySet());
    }

    @Override
    public Object toObject() {
        Map<String, Object> result = new LinkedHashMap<>(properties.size());
        for (Map.Entry<String, JsonValue> entry : properties.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toObject());
        }
        return result;
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public JsonObject asObject() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof JsonObject))
            return false;
        return properties.equals(((JsonObject) obj).properties);
    }

    @Override
    public int hashCode() {
        return properties.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, JsonValue> entry : properties.entrySet()) {
            if (!first)
                sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(entry.getValue().toString());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
