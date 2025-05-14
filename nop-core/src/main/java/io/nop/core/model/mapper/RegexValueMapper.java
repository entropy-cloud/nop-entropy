package io.nop.core.model.mapper;

import io.nop.commons.text.regex.IRegex;
import io.nop.commons.text.regex.RegexHelper;

import java.util.Map;
import java.util.Optional;

public class RegexValueMapper<S, R> implements IValueMapper<S, R> {
    private final String regex;
    private final IRegex regexObj;
    private final Optional<R> optionalValue;

    public RegexValueMapper(String regex, R value) {
        this.regex = regex;
        this.regexObj = RegexHelper.fromPattern(regex);
        this.optionalValue = Optional.of(value);
    }

    @Override
    public Optional<R> mapValue(S value) {
        if (regexObj.test(value.toString()))
            return optionalValue;

        return Optional.empty();
    }

    @Override
    public void serializeToMap(Map<String, Object> out) {
        out.put("/" + regex + "/", optionalValue.orElse(null));
    }
}
