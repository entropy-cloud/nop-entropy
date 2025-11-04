package io.nop.core.model.mapper;

import java.util.Map;
import java.util.Optional;

public class MatchAllValueMapper<S, R> implements IValueMapper<S, R> {
    private final Optional<R> optionalValue;

    public MatchAllValueMapper(R value) {
        this.optionalValue = Optional.of(value);
    }

    @Override
    public Optional<R> mapValue(S value) {
        return this.optionalValue;
    }

    @Override
    public void serializeToMap(Map<String, Object> out) {
        out.put("*", optionalValue.orElse(null));
    }
}
