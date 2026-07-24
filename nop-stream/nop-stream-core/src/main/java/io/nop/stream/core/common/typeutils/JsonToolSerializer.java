package io.nop.stream.core.common.typeutils;

import io.nop.core.lang.json.JsonTool;

import java.nio.charset.StandardCharsets;

public class JsonToolSerializer<T> implements IStreamSerializer<T> {

    private static final long serialVersionUID = 1L;

    @Override
    public boolean isImmutableType() {
        return false;
    }

    @Override
    public TypeSerializer<T> duplicate() {
        return this;
    }

    @Override
    public T createInstance() {
        return null;
    }

    @Override
    public T copy(T from) {
        return from;
    }

    @Override
    public T copy(T from, T reuse) {
        return from;
    }

    @Override
    public int getLength() {
        return -1;
    }

    @Override
    public byte[] serialize(T value) {
        if (value == null) return null;
        return JsonTool.serialize(value, false).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(byte[] data, Class<T> type) {
        if (data == null || data.length == 0) return null;
        String json = new String(data, StandardCharsets.UTF_8);
        return JsonTool.parseBeanFromText(json, type);
    }
}
