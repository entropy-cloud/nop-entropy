package io.nop.core.model.mapper;

import io.nop.commons.util.StringHelper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GroupedValueMapper<S, R> implements IValueMapper<S, R> {
    private final List<S> group;
    private final Optional<R> optionalValue;

    public GroupedValueMapper(List<S> group, R value) {
        this.group = group;
        this.optionalValue = Optional.of(value);
    }

    @Override
    public Optional<R> mapValue(S value) {
        if (group.contains(value))
            return this.optionalValue;
        return Optional.empty();
    }

    @Override
    public void serializeToMap(Map<String, Object> out) {
        out.put(StringHelper.join(group, "|"), optionalValue.orElse(null));
    }
}