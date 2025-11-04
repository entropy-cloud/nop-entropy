package io.nop.core.model.mapper;

import io.nop.api.core.json.IJsonString;
import io.nop.core.lang.json.JsonTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CompositeValueMapper<S, R> implements IValueMapper<S, R>, IJsonString {
    private final List<IValueMapper<S, R>> mappers;

    public CompositeValueMapper(List<IValueMapper<S, R>> mappers) {
        this.mappers = mappers;
    }

    public List<IValueMapper<S, R>> getMappers() {
        return mappers;
    }

    @Override
    public Optional<R> mapValue(S value) {
        for (IValueMapper<S, R> mapper : mappers) {
            Optional<R> ret = mapper.mapValue(value);
            if (ret.isPresent())
                return ret;
        }
        return Optional.empty();
    }

    @Override
    public void serializeToMap(Map<String, Object> out) {
        for (IValueMapper<S, R> mapper : mappers) {
            mapper.serializeToMap(out);
        }
    }

    @Override
    public String toString() {
        Map<String, Object> out = new LinkedHashMap<>();
        serializeToMap(out);
        return JsonTool.serialize(out, true);
    }
}