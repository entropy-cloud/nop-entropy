package io.nop.core.model.mapper;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.MathHelper;

import java.util.Map;
import java.util.Optional;

public class NumberRangeValueMapper<S, R> implements IValueMapper<S, R> {
    private final Number min;
    private final Number max;
    private final boolean excludeMin;
    private final boolean excludeMax;
    private final Optional<R> optionalValue;

    public NumberRangeValueMapper(Number min, Number max, boolean excludeMin, boolean excludeMax, R value) {
        this.min = min;
        this.max = max;
        this.excludeMin = excludeMin;
        this.excludeMax = excludeMax;
        this.optionalValue = Optional.of(value);
    }

    @Override
    public Optional<R> mapValue(S value) {
        Number num = ConvertHelper.toNumber(value, NopException::new);
        if (min != null) {
            int cmp = MathHelper.compareWithConversion(num, min);
            if (cmp < 0 || (cmp == 0 && excludeMin))
                return Optional.empty();
        }

        if (max != null) {
            int cmp = MathHelper.compareWithConversion(num, max);
            if (cmp > 0 || (cmp == 0 && excludeMax))
                return Optional.empty();
        }
        return optionalValue;
    }

    @Override
    public void serializeToMap(Map<String, Object> out) {
        StringBuilder sb = new StringBuilder();
        sb.append(excludeMin ? "(" : "[");
        if (min != null)
            sb.append(min);
        sb.append(',');
        if (max != null)
            sb.append(max);
        sb.append(excludeMax ? ")" : "]");
        out.put(sb.toString(), optionalValue.orElse(null));
    }
}
