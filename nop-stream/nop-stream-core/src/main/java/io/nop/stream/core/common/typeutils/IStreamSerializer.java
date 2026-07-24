package io.nop.stream.core.common.typeutils;

public interface IStreamSerializer<T> extends TypeSerializer<T> {

    byte[] serialize(T value);

    T deserialize(byte[] data, Class<T> type);
}
