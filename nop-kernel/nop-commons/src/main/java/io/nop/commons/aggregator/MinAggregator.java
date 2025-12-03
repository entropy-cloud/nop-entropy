package io.nop.commons.aggregator;

import io.nop.commons.util.MathHelper;

public class MinAggregator implements IAggregator {
    private Object result = null;

    @Override
    public void update(Object value) {
        if (result == null) {
            result = value;
        } else if (value != null) {
            result = MathHelper.min(result, value);
        }
    }

    @Override
    public Object getValue() {
        return result;
    }

    @Override
    public void reset() {
        result = null;
    }
}
