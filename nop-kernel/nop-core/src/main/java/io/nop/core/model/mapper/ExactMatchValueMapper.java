package io.nop.core.model.mapper;

import java.util.Map;
import java.util.Optional;

public class ExactMatchValueMapper<S, R> implements IValueMapper<S, R> {
    private final S matchedValue;
    private final Optional<R> optionalValue;

    public ExactMatchValueMapper(S matchedValue, R value) {
        this.matchedValue = matchedValue;
        this.optionalValue = Optional.of(value);
    }

    @Override
    public Optional<R> mapValue(S value) {
        if (matchedValue.equals(value))
            return optionalValue;
        return Optional.empty();
    }

    @Override
    public void serializeToMap(Map<String, Object> out) {
        out.put(matchedValue.toString(), optionalValue.orElse(null));
    }
}
