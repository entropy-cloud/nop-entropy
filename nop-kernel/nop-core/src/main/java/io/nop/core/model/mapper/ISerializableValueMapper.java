package io.nop.core.model.mapper;

import java.util.Map;

public interface ISerializableValueMapper<S, R> extends IValueMapper<S, R> {

    void serializeToMap(Map<String, Object> out);
}
