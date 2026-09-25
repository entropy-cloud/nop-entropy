package io.nop.jq.jsonvalue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public final class JsonArray implements JsonValue, Iterable<JsonValue> {
    private final List<JsonValue> items;

    JsonArray(List<?> list) {
        List<JsonValue> converted = new ArrayList<>(list.size());
        for (Object item : list) {
            converted.add(JsonValue.fromObject(item));
        }
        this.items = Collections.unmodifiableList(converted);
    }

    private JsonArray(List<JsonValue> items, boolean direct) {
        this.items = items;
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public JsonValue get(int index) {
        return items.get(index);
    }

    public List<JsonValue> items() {
        return items;
    }

    public JsonArray add(JsonValue value) {
        List<JsonValue> newList = new ArrayList<>(items);
        newList.add(value);
        return new JsonArray(Collections.unmodifiableList(newList), true);
    }

    public JsonArray set(int index, JsonValue value) {
        List<JsonValue> newList = new ArrayList<>(items);
        newList.set(index, value);
        return new JsonArray(Collections.unmodifiableList(newList), true);
    }

    public JsonArray remove(int index) {
        List<JsonValue> newList = new ArrayList<>(items);
        newList.remove(index);
        return new JsonArray(Collections.unmodifiableList(newList), true);
    }

    @Override
    public Iterator<JsonValue> iterator() {
        return items.iterator();
    }

    @Override
    public Object toObject() {
        List<Object> result = new ArrayList<>(items.size());
        for (JsonValue item : items) {
            result.add(item.toObject());
        }
        return result;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public JsonArray asArray() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof JsonArray))
            return false;
        return items.equals(((JsonArray) obj).items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    @Override
    public String toString() {
        return items.stream()
                .map(JsonValue::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
