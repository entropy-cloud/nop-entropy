package io.nop.core.model.mapper;

import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ValueMapperParser {
    public static final ValueMapperParser INSTANCE = new ValueMapperParser();

    public CompositeValueMapper<String, Object> parseMapper(Map<String, Object> config) {
        if (config == null || config.isEmpty())
            return null;

        List<IValueMapper<String, Object>> mappers = new ArrayList<>(config.size());

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String pattern = entry.getKey();
            Object value = entry.getValue();

            if (pattern.startsWith("[")) {
                mappers.add(parseRangeMapper(pattern, value));
            } else if (pattern.startsWith("(")) {
                mappers.add(parseRangeMapper(pattern, value));
            } else if (pattern.startsWith("/")) {
                mappers.add(parseRegexMapper(pattern, value));
            } else if (pattern.contains("|")) {
                List<String> group = StringHelper.split(pattern, '|');
                mappers.add(new GroupedValueMapper<>(group, value));
            } else if (pattern.equals("*")) {
                mappers.add(new MatchAllValueMapper<>(value));
            } else {
                mappers.add(new ExactMatchValueMapper<>(pattern, value));
            }
        }

        return new CompositeValueMapper<>(mappers);
    }

    protected IValueMapper<String, Object> parseRangeMapper(String pattern, Object value) {
        boolean excludeMin = pattern.startsWith("(");
        boolean excludeMax = pattern.endsWith(")");

        if (!pattern.startsWith("(") && !pattern.startsWith("["))
            throw new IllegalArgumentException("invalid pattern:" + pattern);
        if (!pattern.endsWith(")") && !pattern.endsWith("]"))
            throw new IllegalArgumentException("invalid pattern:" + pattern);

        int pos = pattern.indexOf(',');
        if (pos < 0)
            throw new IllegalArgumentException("invalid pattern:" + pattern);

        String min = pattern.substring(1, pos).trim();
        String max = pattern.substring(pos + 1, pattern.length() - 1).trim();

        Number minValue = StringHelper.parseNumber(min);
        Number maxValue = StringHelper.parseNumber(max);
        return new NumberRangeValueMapper<>(minValue, maxValue, excludeMin, excludeMax, value);
    }

    private IValueMapper<String, Object> parseRegexMapper(String pattern, Object value) {
        if (!pattern.startsWith("/") || !pattern.endsWith("/"))
            throw new IllegalArgumentException("invalid pattern:" + pattern);

        return new RegexValueMapper<>(pattern.substring(1, pattern.length()-1).trim(),value);
    }
}
