package io.nop.table.validator.validate;

import java.util.Map;
import java.util.function.Function;

public enum StatProperty {
    MEAN(ColumnStats::getMean),
    COUNT(s -> (double) s.getCount()),
    NULL_COUNT(s -> (double) s.getNullCount()),
    SUM(ColumnStats::getSum),
    SUM_OF_SQUARES(ColumnStats::getSumOfSquares),
    MIN(ColumnStats::getMin),
    MAX(ColumnStats::getMax),
    STD_DEV(ColumnStats::getStdDev),
    DISTINCT_COUNT(s -> (double) s.getDistinctCount());

    private static final Map<String, StatProperty> NAME_MAP = Map.ofEntries(
            Map.entry("value", MEAN),
            Map.entry("mean", MEAN),
            Map.entry("count", COUNT),
            Map.entry("nullCount", NULL_COUNT),
            Map.entry("sum", SUM),
            Map.entry("sumOfSquares", SUM_OF_SQUARES),
            Map.entry("min", MIN),
            Map.entry("max", MAX),
            Map.entry("stdDev", STD_DEV),
            Map.entry("distinctCount", DISTINCT_COUNT)
    );

    private final Function<ColumnStats, Object> accessor;

    StatProperty(Function<ColumnStats, Object> accessor) {
        this.accessor = accessor;
    }

    public Object getValue(ColumnStats stats) {
        return accessor.apply(stats);
    }

    public static StatProperty fromName(String name) {
        return name != null ? NAME_MAP.get(name) : null;
    }
}
