package io.nop.core.model.mapper;

import java.util.Map;
import java.util.Optional;

/**
 * 值映射规则
 */
public interface IValueMapper<S, R> {
    Optional<R> mapValue(S value);

    default void serializeToMap(Map<String, Object> out) {
        throw new UnsupportedOperationException("serializeToMap");
    }
}